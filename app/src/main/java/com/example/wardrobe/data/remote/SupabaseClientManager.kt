package com.example.wardrobe.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.wardrobe.auth.model.AuthUser
import com.example.wardrobe.auth.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Exception thrown when Supabase requires email verification before issuing an active session
 * or when a user attempts to sign in before confirming their email address.
 */
class EmailConfirmationRequiredException(
    val email: String,
    val fullName: String? = null,
    override val message: String = "Please check your inbox and confirm your email address before signing in."
) : Exception(message)

/**
 * Persisted Supabase Auth session model stored on-device for automatic session restoration.
 */
data class SupabasePersistedSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String? = null,
    val stylePreference: String? = null,
    val isEmailVerified: Boolean = true
) {
    fun isExpiredOrExpiringSoon(bufferSeconds: Long = 180L): Boolean {
        val nowSeconds = System.currentTimeMillis() / 1000L
        return expiresAtEpochSeconds <= (nowSeconds + bufferSeconds)
    }

    fun toAuthUser(): AuthUser = AuthUser(
        uid = userId,
        email = email,
        displayName = fullName?.ifBlank { null }
            ?: email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Wardrobe Member",
        isEmailVerified = isEmailVerified,
        avatarUrl = avatarUrl,
        stylePreference = stylePreference,
        isGuest = false
    )
}

/**
 * Result of a signup request: either an active authenticated session or pending email verification.
 */
sealed interface SupabaseSignUpResult {
    data class Authenticated(val user: AuthUser, val profile: UserProfile?) : SupabaseSignUpResult
    data class ConfirmationPending(val email: String, val fullName: String?, val message: String) : SupabaseSignUpResult
}

/**
 * Centralized Supabase Client & Auth/PostgREST Manager for Wardrobe AI.
 *
 * Uses ONLY the public Supabase URL and publishable key (`sb_publishable_...`).
 * Never uses a service-role or secret key. All database operations against `public.profiles`
 * authenticate using the signed-in user's JWT (`Authorization: Bearer <access_token>`)
 * so PostgreSQL Row Level Security (RLS) policies (`auth.uid() = id`) are enforced.
 */
object SupabaseClientManager {

    private const val TAG = "SupabaseClientManager"
    private const val DEFAULT_SUPABASE_URL = "https://glpkragirdqihhqbmnjp.supabase.co"
    private const val DEFAULT_PUBLISHABLE_KEY = "sb_publishable_xoxBcYqTanoFAyeE0YppaA_zqOMACdP"

    const val REDIRECT_SCHEME = "wardrobeai"
    const val REDIRECT_HOST = "auth-callback"
    const val REDIRECT_URL = "$REDIRECT_SCHEME://$REDIRECT_HOST"

    private const val PREFS_NAME = "wardrobe_supabase_auth_session"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at_epoch_sec"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_FULL_NAME = "user_full_name"
    private const val KEY_AVATAR_URL = "user_avatar_url"
    private const val KEY_STYLE_PREF = "user_style_pref"
    private const val KEY_EMAIL_VERIFIED = "user_email_verified"
    private const val KEY_PROFILE_COLUMNS = "detected_profile_columns"

    val supabaseUrl: String
        get() {
            val raw = runCatching { BuildConfig.SUPABASE_URL }.getOrNull()?.trim().orEmpty()
            return if (raw.isNotEmpty() && !raw.startsWith("YOUR_") && raw.startsWith("http")) {
                raw.trimEnd('/')
            } else {
                DEFAULT_SUPABASE_URL
            }
        }

    val supabasePublishableKey: String
        get() {
            val raw = runCatching { BuildConfig.SUPABASE_PUBLISHABLE_KEY }.getOrNull()?.trim().orEmpty()
            // Enforce: never use a service_role or secret key
            return if (
                raw.isNotEmpty() &&
                !raw.startsWith("YOUR_") &&
                !raw.startsWith("sb_secret_") &&
                !raw.contains("service_role", ignoreCase = true)
            ) {
                raw
            } else {
                DEFAULT_PUBLISHABLE_KEY
            }
        }

    val isConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") && supabasePublishableKey.isNotBlank()

    /**
     * Official Supabase Kotlin SDK client instance configured with Auth and Postgrest plugins
     * and the Ktor OkHttp engine.
     */
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabasePublishableKey
        ) {
            httpEngine = OkHttp.create()
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = REDIRECT_SCHEME
                host = REDIRECT_HOST
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val sessionMutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    private val _sessionState = MutableStateFlow<SupabasePersistedSession?>(null)
    val sessionState: StateFlow<SupabasePersistedSession?> = _sessionState.asStateFlow()

    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    private val _passwordRecoveryPending = MutableStateFlow(false)
    val passwordRecoveryPending: StateFlow<Boolean> = _passwordRecoveryPending.asStateFlow()

    /**
     * Initializes the manager with the application context and loads any persisted session
     * synchronously into memory so the initial state is immediately available.
     */
    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        val stored = readPersistedSession(context.applicationContext)
        if (stored != null && _sessionState.value == null) {
            _sessionState.value = stored
            _currentProfile.value = UserProfile(
                id = stored.userId,
                email = stored.email,
                fullName = stored.fullName,
                avatarUrl = stored.avatarUrl,
                stylePreference = stored.stylePreference
            )
        }
    }

    private fun prefs(context: Context? = appContext): SharedPreferences? {
        val ctx = context ?: appContext ?: return null
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPersistedSession(context: Context? = appContext): SupabasePersistedSession? {
        val current = _sessionState.value
        if (current != null) return current
        val ctx = context ?: appContext ?: return null
        return readPersistedSession(ctx).also {
            if (it != null) _sessionState.value = it
        }
    }

    private fun readPersistedSession(context: Context): SupabasePersistedSession? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = sp.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = sp.getString(KEY_REFRESH_TOKEN, null)
        val userId = sp.getString(KEY_USER_ID, null)
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() || userId.isNullOrBlank()) {
            return null
        }
        return SupabasePersistedSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = sp.getLong(KEY_EXPIRES_AT, 0L),
            userId = userId,
            email = sp.getString(KEY_EMAIL, null),
            fullName = sp.getString(KEY_FULL_NAME, null),
            avatarUrl = sp.getString(KEY_AVATAR_URL, null),
            stylePreference = sp.getString(KEY_STYLE_PREF, null),
            isEmailVerified = sp.getBoolean(KEY_EMAIL_VERIFIED, true)
        )
    }

    private fun savePersistedSession(session: SupabasePersistedSession) {
        _sessionState.value = session
        prefs()?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, session.accessToken)
            putString(KEY_REFRESH_TOKEN, session.refreshToken)
            putLong(KEY_EXPIRES_AT, session.expiresAtEpochSeconds)
            putString(KEY_USER_ID, session.userId)
            putString(KEY_EMAIL, session.email)
            putString(KEY_FULL_NAME, session.fullName)
            putString(KEY_AVATAR_URL, session.avatarUrl)
            putString(KEY_STYLE_PREF, session.stylePreference)
            putBoolean(KEY_EMAIL_VERIFIED, session.isEmailVerified)
            apply()
        }
    }

    fun clearPersistedSession() {
        _sessionState.value = null
        _currentProfile.value = null
        _passwordRecoveryPending.value = false
        prefs()?.edit()?.clear()?.apply()
    }

    fun clearPasswordRecoveryFlag() {
        _passwordRecoveryPending.value = false
    }

    /**
     * Automatically restores and validates the persisted Supabase session when the app starts.
     * Refreshes the JWT access token using the refresh token if expired or near expiry,
     * and fetches the latest profile from `public.profiles`.
     */
    suspend fun restoreSessionOnLaunch(context: Context): Result<AuthUser?> = withContext(Dispatchers.IO) {
        initialize(context)
        val existing = getPersistedSession(context) ?: return@withContext Result.success(null)

        sessionMutex.withLock {
            try {
                val activeSession = if (existing.isExpiredOrExpiringSoon()) {
                    refreshSessionInternal(existing.refreshToken).getOrElse {
                        // If refresh failed due to invalid/revoked refresh token, clear session
                        val msg = it.message.orEmpty()
                        if (msg.contains("invalid_grant", ignoreCase = true) ||
                            msg.contains("Refresh Token Not Found", ignoreCase = true) ||
                            msg.contains("Invalid Refresh Token", ignoreCase = true)
                        ) {
                            clearPersistedSession()
                            return@withContext Result.success(null)
                        }
                        // Network offline fallback: keep cached session so user isn't locked out offline
                        existing
                    }
                } else {
                    existing
                }

                // Fetch latest user & profile from public.profiles
                val profileResult = fetchUserProfile(
                    userId = activeSession.userId,
                    accessToken = activeSession.accessToken
                )
                val mergedSession = profileResult.getOrNull()?.let { profile ->
                    activeSession.copy(
                        fullName = profile.fullName?.ifBlank { null } ?: activeSession.fullName,
                        email = profile.email?.ifBlank { null } ?: activeSession.email,
                        avatarUrl = profile.avatarUrl ?: activeSession.avatarUrl,
                        stylePreference = profile.stylePreference ?: activeSession.stylePreference
                    ).also { savePersistedSession(it) }
                } ?: activeSession

                Result.success(mergedSession.toAuthUser())
            } catch (e: Exception) {
                Log.w(TAG, "Session restore warning (continuing with cached session): ${e.message}")
                Result.success(existing.toAuthUser())
            }
        }
    }

    /**
     * Refreshes the current session using the stored refresh_token.
     */
    suspend fun ensureValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val current = getPersistedSession() ?: return@withContext null
        if (!current.isExpiredOrExpiringSoon()) {
            return@withContext current.accessToken
        }
        sessionMutex.withLock {
            val recheck = getPersistedSession() ?: return@withLock null
            if (!recheck.isExpiredOrExpiringSoon()) {
                return@withLock recheck.accessToken
            }
            refreshSessionInternal(recheck.refreshToken).getOrNull()?.accessToken ?: recheck.accessToken
        }
    }

    private fun refreshSessionInternal(refreshToken: String): Result<SupabasePersistedSession> {
        return try {
            val payload = buildJsonObject {
                put("refresh_token", refreshToken)
            }.toString()

            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = parseSupabaseError(bodyStr, response.code)
                    return Result.failure(Exception(err))
                }
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val session = parseSessionFromAuthResponse(root, fallbackFullName = _sessionState.value?.fullName)
                    ?: return Result.failure(Exception("Invalid session response during token refresh."))
                savePersistedSession(session)
                Result.success(session)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real Email/Password Signup with Full Name, Email, and Password.
     *
     * Passes `full_name` in `data` (`raw_user_meta_data`) so the existing `on_auth_user_created`
     * trigger in Supabase automatically populates `public.profiles`.
     * Handles both immediate sessions and email confirmation required flows.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        fullName: String?
    ): Result<SupabaseSignUpResult> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val cleanedName = fullName?.trim()?.takeIf { it.isNotEmpty() }
                ?: trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

            val payload = buildJsonObject {
                put("email", trimmedEmail)
                put("password", password)
                put(
                    "data",
                    buildJsonObject {
                        put("full_name", cleanedName)
                        put("name", cleanedName)
                        put("display_name", cleanedName)
                    }
                )
            }.toString()

            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/signup")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMsg = parseSupabaseError(bodyStr, response.code)
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val root = json.parseToJsonElement(bodyStr).jsonObject

                // Check if Supabase returned an obfuscated existing-user response (empty identities array)
                val userObj = root["user"]?.jsonObject ?: root
                val identities = userObj["identities"] as? JsonArray
                if (identities != null && identities.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("An account with this email already exists. Please sign in or reset your password.")
                    )
                }

                val session = parseSessionFromAuthResponse(root, fallbackFullName = cleanedName)
                if (session != null) {
                    // Immediate session returned (email confirmation disabled or auto-confirmed)
                    savePersistedSession(session)

                    // Ensure public.profiles has the user's full_name via RLS
                    val profile = syncProfileAfterAuth(
                        session = session,
                        preferredFullName = cleanedName
                    )
                    val updatedUser = session.copy(
                        fullName = profile?.fullName?.ifBlank { null } ?: cleanedName,
                        stylePreference = profile?.stylePreference ?: session.stylePreference
                    ).also { savePersistedSession(it) }.toAuthUser()

                    return@withContext Result.success(
                        SupabaseSignUpResult.Authenticated(
                            user = updatedUser,
                            profile = profile
                        )
                    )
                } else {
                    // Email confirmation is enabled: user record & public.profiles row were created by
                    // the on_auth_user_created trigger, and a confirmation email was sent.
                    return@withContext Result.success(
                        SupabaseSignUpResult.ConfirmationPending(
                            email = trimmedEmail,
                            fullName = cleanedName,
                            message = "We've sent a confirmation email to $trimmedEmail. Please verify your email address, then tap 'I've Verified — Sign In' or enter your confirmation code."
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "signUpWithEmail failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Real Email/Password Sign-In.
     * Persists the session and fetches the authenticated user's profile from `public.profiles`.
     */
    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val payload = buildJsonObject {
                put("email", trimmedEmail)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorCode = parseSupabaseErrorCode(bodyStr)
                    val errorMsg = parseSupabaseError(bodyStr, response.code)
                    if (errorCode == "email_not_confirmed" ||
                        errorMsg.contains("Email not confirmed", ignoreCase = true)
                    ) {
                        return@withContext Result.failure(
                            EmailConfirmationRequiredException(
                                email = trimmedEmail,
                                message = "Your email address ($trimmedEmail) has not been confirmed yet. Please check your inbox for the verification link or resend the confirmation email."
                            )
                        )
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val root = json.parseToJsonElement(bodyStr).jsonObject
                val session = parseSessionFromAuthResponse(root, fallbackFullName = null)
                    ?: return@withContext Result.failure(Exception("Unable to parse authentication session from Supabase."))

                savePersistedSession(session)

                // Fetch and synchronize the user's profile from public.profiles using RLS
                val profile = syncProfileAfterAuth(
                    session = session,
                    preferredFullName = session.fullName
                )

                val finalSession = session.copy(
                    fullName = profile?.fullName?.ifBlank { null } ?: session.fullName,
                    email = profile?.email?.ifBlank { null } ?: session.email,
                    avatarUrl = profile?.avatarUrl ?: session.avatarUrl,
                    stylePreference = profile?.stylePreference ?: session.stylePreference
                )
                savePersistedSession(finalSession)

                Result.success(finalSession.toAuthUser())
            }
        } catch (e: Exception) {
            Log.w(TAG, "signInWithEmail failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Resends the signup confirmation email for an unverified account.
     */
    suspend fun resendConfirmationEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val payload = buildJsonObject {
                put("type", "signup")
                put("email", trimmedEmail)
            }.toString()

            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/resend")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseSupabaseError(bodyStr, response.code)))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies an email confirmation OTP or password recovery OTP code (`POST /auth/v1/verify`).
     */
    suspend fun verifyEmailOrRecoveryOtp(
        email: String,
        token: String,
        type: String = "signup"
    ): Result<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val trimmedToken = token.trim()
            val payload = buildJsonObject {
                put("type", type)
                put("email", trimmedEmail)
                put("token", trimmedToken)
            }.toString()

            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/verify")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseSupabaseError(bodyStr, response.code)))
                }

                val root = json.parseToJsonElement(bodyStr).jsonObject
                val session = parseSessionFromAuthResponse(root, fallbackFullName = null)
                    ?: return@withContext Result.failure(Exception("Verification succeeded, please sign in with your email and password."))

                savePersistedSession(session)
                val profile = syncProfileAfterAuth(session, session.fullName)
                val finalSession = session.copy(
                    fullName = profile?.fullName?.ifBlank { null } ?: session.fullName,
                    stylePreference = profile?.stylePreference ?: session.stylePreference
                )
                savePersistedSession(finalSession)
                Result.success(finalSession.toAuthUser())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a password reset email via Supabase (`POST /auth/v1/recover`).
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val payload = buildJsonObject {
                put("email", trimmedEmail)
            }.toString()

            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/recover?redirect_to=$REDIRECT_URL")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseSupabaseError(bodyStr, response.code)))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Completes a password reset or updates the authenticated user's password (`PUT /auth/v1/user`).
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (newPassword.length < 8) {
                return@withContext Result.failure(IllegalArgumentException("Password must be at least 8 characters."))
            }
            val token = ensureValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("No active authenticated session found. Please verify your recovery code or sign in first."))

            val payload = buildJsonObject {
                put("password", newPassword)
            }.toString()

            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/user")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .put(payload.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseSupabaseError(bodyStr, response.code)))
                }
                _passwordRecoveryPending.value = false
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resets password using a recovery code/OTP sent to the user's email + new password in one step.
     */
    suspend fun resetPasswordWithRecoveryCode(
        email: String,
        recoveryCode: String,
        newPassword: String
    ): Result<AuthUser> = withContext(Dispatchers.IO) {
        val verifyResult = verifyEmailOrRecoveryOtp(
            email = email.trim(),
            token = recoveryCode.trim(),
            type = "recovery"
        )
        verifyResult.fold(
            onSuccess = { user ->
                val updateResult = updatePassword(newPassword)
                updateResult.fold(
                    onSuccess = { Result.success(user) },
                    onFailure = { err -> Result.failure(err) }
                )
            },
            onFailure = { err -> Result.failure(err) }
        )
    }

    /**
     * Handles deep link callbacks from Supabase confirmation or password recovery emails
     * (e.g., `wardrobeai://auth-callback#access_token=...&refresh_token=...&type=recovery`
     * or `?token_hash=...&type=signup`).
     */
    suspend fun handleDeepLinkIntent(uri: Uri?): Result<AuthUser?> = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext Result.success(null)
        try {
            val fragment = uri.fragment.orEmpty()
            val fragmentParams = fragment.split("&")
                .mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0) {
                        Uri.decode(part.substring(0, idx)) to Uri.decode(part.substring(idx + 1))
                    } else null
                }
                .toMap()

            val accessToken = fragmentParams["access_token"] ?: uri.getQueryParameter("access_token")
            val refreshToken = fragmentParams["refresh_token"] ?: uri.getQueryParameter("refresh_token")
            val expiresIn = (fragmentParams["expires_in"] ?: uri.getQueryParameter("expires_in"))?.toLongOrNull() ?: 3600L
            val type = fragmentParams["type"] ?: uri.getQueryParameter("type")
            val tokenHash = uri.getQueryParameter("token_hash") ?: fragmentParams["token_hash"]

            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                val userObj = fetchAuthUserObject(accessToken)
                if (userObj != null) {
                    val userId = userObj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val email = userObj["email"]?.jsonPrimitive?.contentOrNull
                    val meta = userObj["user_metadata"]?.jsonObject
                    val fullName = meta?.get("full_name")?.jsonPrimitive?.contentOrNull
                        ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
                    val session = SupabasePersistedSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + expiresIn,
                        userId = userId,
                        email = email,
                        fullName = fullName,
                        isEmailVerified = true
                    )
                    savePersistedSession(session)
                    syncProfileAfterAuth(session, fullName)
                    if (type.equals("recovery", ignoreCase = true)) {
                        _passwordRecoveryPending.value = true
                    }
                    return@withContext Result.success(session.toAuthUser())
                }
            } else if (!tokenHash.isNullOrBlank() && !type.isNullOrBlank()) {
                val payload = buildJsonObject {
                    put("type", type)
                    put("token_hash", tokenHash)
                }.toString()
                val request = Request.Builder()
                    .url("$supabaseUrl/auth/v1/verify")
                    .addHeader("apikey", supabasePublishableKey)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val root = json.parseToJsonElement(bodyStr).jsonObject
                        val session = parseSessionFromAuthResponse(root, null)
                        if (session != null) {
                            savePersistedSession(session)
                            syncProfileAfterAuth(session, session.fullName)
                            if (type.equals("recovery", ignoreCase = true)) {
                                _passwordRecoveryPending.value = true
                            }
                            return@withContext Result.success(session.toAuthUser())
                        }
                    }
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed handling Supabase deep link: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Real Logout: revokes the current user's session on Supabase (`POST /auth/v1/logout`)
     * and clears the local persisted session.
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        val current = getPersistedSession()
        clearPersistedSession()
        if (current != null && current.accessToken.isNotBlank()) {
            runCatching {
                val request = Request.Builder()
                    .url("$supabaseUrl/auth/v1/logout")
                    .addHeader("apikey", supabasePublishableKey)
                    .addHeader("Authorization", "Bearer ${current.accessToken}")
                    .post("".toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(request).execute().close()
            }
        }
        Result.success(Unit)
    }

    /**
     * Fetches the authenticated user's profile from `public.profiles` using RLS.
     *
     * Sends `Authorization: Bearer <accessToken>` and `apikey: <publishableKey>`
     * and filters by `id=eq.<userId>` matching `auth.uid() = id`.
     */
    suspend fun fetchUserProfile(
        userId: String? = getPersistedSession()?.userId,
        accessToken: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val uid = userId?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(IllegalStateException("Not authenticated."))
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: ensureValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("No valid access token."))

            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/profiles?id=eq.$uid&select=*")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = parseSupabaseError(bodyStr, response.code)
                    return@withContext Result.failure(Exception(err))
                }

                val array = json.parseToJsonElement(bodyStr).jsonArray
                if (array.isEmpty()) {
                    // Row not yet created or empty result: return fallback from current session
                    val fallback = UserProfile(
                        id = uid,
                        email = _sessionState.value?.email,
                        fullName = _sessionState.value?.fullName,
                        avatarUrl = _sessionState.value?.avatarUrl,
                        stylePreference = _sessionState.value?.stylePreference
                    )
                    _currentProfile.value = fallback
                    return@withContext Result.success(fallback)
                }

                val rowObj = array.first().jsonObject
                recordDetectedProfileColumns(rowObj.keys)
                val profile = parseUserProfileRow(rowObj, uid)
                _currentProfile.value = profile

                // Keep persisted session in sync with fetched profile
                _sessionState.value?.let { current ->
                    savePersistedSession(
                        current.copy(
                            fullName = profile.fullName?.ifBlank { null } ?: current.fullName,
                            email = profile.email?.ifBlank { null } ?: current.email,
                            avatarUrl = profile.avatarUrl ?: current.avatarUrl,
                            stylePreference = profile.stylePreference ?: current.stylePreference
                        )
                    )
                }

                Result.success(profile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserProfile failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Updates the authenticated user's profile in `public.profiles` using RLS (`id=eq.<auth.uid()>`),
     * and also synchronizes `user_metadata` on `auth.users`.
     *
     * Does NOT create another `profiles` table or trigger. Automatically adapts to the columns
     * present in the user's `public.profiles` schema (e.g., `full_name`, `username`, `avatar_url`,
     * `updated_at`, `style_preference`).
     */
    suspend fun updateUserProfile(
        fullName: String? = null,
        stylePreference: String? = null,
        avatarUrl: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val session = getPersistedSession()
                ?: return@withContext Result.failure(IllegalStateException("Please sign in to update your cloud profile."))
            val token = ensureValidAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Session expired. Please sign in again."))
            val uid = session.userId

            // Ensure we know the columns of public.profiles first
            var knownColumns = getDetectedProfileColumns()
            if (knownColumns.isEmpty()) {
                fetchUserProfile(uid, token)
                knownColumns = getDetectedProfileColumns()
            }

            // Build PATCH body matching the columns that exist in public.profiles
            val patchObject = buildJsonObject {
                if (fullName != null) {
                    val trimmedName = fullName.trim()
                    when {
                        knownColumns.isEmpty() || "full_name" in knownColumns -> put("full_name", trimmedName)
                        "name" in knownColumns -> put("name", trimmedName)
                        "display_name" in knownColumns -> put("display_name", trimmedName)
                        "username" in knownColumns -> put("username", trimmedName)
                        else -> put("full_name", trimmedName)
                    }
                }
                if (stylePreference != null) {
                    when {
                        "style_preference" in knownColumns -> put("style_preference", stylePreference)
                        "style_persona" in knownColumns -> put("style_persona", stylePreference)
                        "aesthetic" in knownColumns -> put("aesthetic", stylePreference)
                    }
                }
                if (avatarUrl != null && (knownColumns.isEmpty() || "avatar_url" in knownColumns)) {
                    put("avatar_url", avatarUrl)
                }
                if ("updated_at" in knownColumns) {
                    put("updated_at", Instant.now().toString())
                }
            }

            var updatedProfile: UserProfile? = null

            if (patchObject.isNotEmpty()) {
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/profiles?id=eq.$uid")
                    .addHeader("apikey", supabasePublishableKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(patchObject.toString().toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val arr = runCatching { json.parseToJsonElement(bodyStr).jsonArray }.getOrNull()
                        if (arr != null && arr.isNotEmpty()) {
                            val rowObj = arr.first().jsonObject
                            recordDetectedProfileColumns(rowObj.keys)
                            updatedProfile = parseUserProfileRow(rowObj, uid)
                        }
                    } else if (fullName != null) {
                        // Fallback minimal PATCH with only full_name in case an optional column caused PGRST204
                        val minimalPatch = buildJsonObject {
                            put("full_name", fullName.trim())
                        }
                        val fallbackReq = Request.Builder()
                            .url("$supabaseUrl/rest/v1/profiles?id=eq.$uid")
                            .addHeader("apikey", supabasePublishableKey)
                            .addHeader("Authorization", "Bearer $token")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=representation")
                            .patch(minimalPatch.toString().toRequestBody(jsonMediaType))
                            .build()
                        httpClient.newCall(fallbackReq).execute().use { fbResp ->
                            val fbBody = fbResp.body?.string().orEmpty()
                            if (fbResp.isSuccessful) {
                                val arr = runCatching { json.parseToJsonElement(fbBody).jsonArray }.getOrNull()
                                if (arr != null && arr.isNotEmpty()) {
                                    updatedProfile = parseUserProfileRow(arr.first().jsonObject, uid)
                                }
                            }
                        }
                    }
                }
            }

            // Also keep auth.users raw_user_meta_data synchronized with full_name & style_preference
            runCatching {
                val metaPayload = buildJsonObject {
                    put(
                        "data",
                        buildJsonObject {
                            if (fullName != null) {
                                put("full_name", fullName.trim())
                                put("name", fullName.trim())
                            }
                            if (stylePreference != null) {
                                put("style_preference", stylePreference)
                            }
                            if (avatarUrl != null) {
                                put("avatar_url", avatarUrl)
                            }
                        }
                    )
                }.toString()

                val authUpdateReq = Request.Builder()
                    .url("$supabaseUrl/auth/v1/user")
                    .addHeader("apikey", supabasePublishableKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .put(metaPayload.toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(authUpdateReq).execute().close()
            }

            val finalFullName = fullName?.trim()?.ifBlank { null }
                ?: updatedProfile?.fullName
                ?: session.fullName
            val finalStyle = stylePreference
                ?: updatedProfile?.stylePreference
                ?: session.stylePreference
            val finalAvatar = avatarUrl
                ?: updatedProfile?.avatarUrl
                ?: session.avatarUrl

            val finalProfile = UserProfile(
                id = uid,
                email = updatedProfile?.email ?: session.email,
                fullName = finalFullName,
                avatarUrl = finalAvatar,
                stylePreference = finalStyle,
                updatedAt = updatedProfile?.updatedAt ?: Instant.now().toString()
            )
            _currentProfile.value = finalProfile

            savePersistedSession(
                session.copy(
                    fullName = finalFullName,
                    stylePreference = finalStyle,
                    avatarUrl = finalAvatar
                )
            )

            Result.success(finalProfile)
        } catch (e: Exception) {
            Log.w(TAG, "updateUserProfile failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Synchronizes `public.profiles` right after sign-in or sign-up.
     * Fetches the user's profile row created by `on_auth_user_created`, and if `full_name`
     * in `public.profiles` is blank while `preferredFullName` is available, updates `public.profiles`.
     */
    private suspend fun syncProfileAfterAuth(
        session: SupabasePersistedSession,
        preferredFullName: String?
    ): UserProfile? {
        val fetched = fetchUserProfile(session.userId, session.accessToken).getOrNull()
        if (fetched != null) {
            if (fetched.fullName.isNullOrBlank() && !preferredFullName.isNullOrBlank()) {
                return updateUserProfile(fullName = preferredFullName).getOrNull() ?: fetched.copy(fullName = preferredFullName)
            }
            return fetched
        }
        return null
    }

    private fun fetchAuthUserObject(accessToken: String): JsonObject? {
        return try {
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/user")
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string().orEmpty()
                json.parseToJsonElement(bodyStr).jsonObject
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSessionFromAuthResponse(
        root: JsonObject,
        fallbackFullName: String?
    ): SupabasePersistedSession? {
        val sessionObj = root["session"]?.let { if (it is JsonObject) it else null } ?: root
        val accessToken = sessionObj["access_token"]?.jsonPrimitive?.contentOrNull
        val refreshToken = sessionObj["refresh_token"]?.jsonPrimitive?.contentOrNull
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            return null
        }

        val expiresAt = sessionObj["expires_at"]?.jsonPrimitive?.longOrNull
            ?: ((System.currentTimeMillis() / 1000L) + (sessionObj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L))

        val userObj = (sessionObj["user"] as? JsonObject)
            ?: (root["user"] as? JsonObject)
            ?: return null

        val userId = userObj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val email = userObj["email"]?.jsonPrimitive?.contentOrNull
        val emailConfirmedAt = userObj["email_confirmed_at"]?.jsonPrimitive?.contentOrNull
            ?: userObj["confirmed_at"]?.jsonPrimitive?.contentOrNull

        val userMeta = userObj["user_metadata"] as? JsonObject
        val metaFullName = userMeta?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?: userMeta?.get("name")?.jsonPrimitive?.contentOrNull
            ?: userMeta?.get("display_name")?.jsonPrimitive?.contentOrNull
            ?: fallbackFullName

        val metaAvatar = userMeta?.get("avatar_url")?.jsonPrimitive?.contentOrNull
        val metaStyle = userMeta?.get("style_preference")?.jsonPrimitive?.contentOrNull

        return SupabasePersistedSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt,
            userId = userId,
            email = email,
            fullName = metaFullName,
            avatarUrl = metaAvatar,
            stylePreference = metaStyle,
            isEmailVerified = !emailConfirmedAt.isNullOrBlank()
        )
    }

    private fun parseUserProfileRow(row: JsonObject, fallbackUserId: String): UserProfile {
        fun JsonObject.str(vararg keys: String): String? {
            for (k in keys) {
                val el = this[k]
                if (el is JsonPrimitive) {
                    val v = el.contentOrNull?.trim()
                    if (!v.isNullOrEmpty() && v != "null") return v
                }
            }
            return null
        }

        val id = row.str("id", "user_id") ?: fallbackUserId
        val email = row.str("email") ?: _sessionState.value?.email
        val fullName = row.str("full_name", "name", "display_name", "username")
            ?: _sessionState.value?.fullName
        val avatarUrl = row.str("avatar_url", "avatar") ?: _sessionState.value?.avatarUrl
        val stylePreference = row.str("style_preference", "style_persona", "aesthetic")
            ?: _sessionState.value?.stylePreference
        val updatedAt = row.str("updated_at", "created_at")

        return UserProfile(
            id = id,
            email = email,
            fullName = fullName,
            avatarUrl = avatarUrl,
            stylePreference = stylePreference,
            updatedAt = updatedAt
        )
    }

    private fun recordDetectedProfileColumns(keys: Set<String>) {
        if (keys.isEmpty()) return
        prefs()?.edit()?.putString(KEY_PROFILE_COLUMNS, keys.joinToString(","))?.apply()
    }

    private fun getDetectedProfileColumns(): Set<String> {
        val raw = prefs()?.getString(KEY_PROFILE_COLUMNS, null).orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun parseSupabaseErrorCode(bodyStr: String): String? {
        if (bodyStr.isBlank()) return null
        return runCatching {
            val obj = json.parseToJsonElement(bodyStr).jsonObject
            obj["error_code"]?.jsonPrimitive?.contentOrNull
                ?: obj["code"]?.jsonPrimitive?.contentOrNull
                ?: obj["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun parseSupabaseError(bodyStr: String, statusCode: Int): String {
        if (bodyStr.isBlank()) {
            return "Request failed (HTTP $statusCode). Please check your connection and try again."
        }
        return runCatching {
            val obj = json.parseToJsonElement(bodyStr).jsonObject
            val msg = obj["msg"]?.jsonPrimitive?.contentOrNull
                ?: obj["message"]?.jsonPrimitive?.contentOrNull
                ?: obj["error_description"]?.jsonPrimitive?.contentOrNull
                ?: obj["error"]?.jsonPrimitive?.contentOrNull
            when {
                msg.isNullOrBlank() -> "Authentication error (HTTP $statusCode)."
                msg.contains("Invalid login credentials", ignoreCase = true) ->
                    "Invalid email or password. Please check your credentials and try again."
                msg.contains("Email not confirmed", ignoreCase = true) ->
                    "Your email address has not been verified yet. Please check your inbox for the confirmation link."
                msg.contains("User already registered", ignoreCase = true) ->
                    "An account with this email already exists. Please sign in instead."
                msg.contains("rate limit", ignoreCase = true) || statusCode == 429 ->
                    "Too many email requests. Please wait a minute before trying again."
                else -> msg
            }
        }.getOrDefault("Authentication error (HTTP $statusCode). Please try again.")
    }
}
