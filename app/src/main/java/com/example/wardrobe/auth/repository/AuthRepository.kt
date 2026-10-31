package com.example.wardrobe.auth.repository

import android.content.Context
import com.example.wardrobe.auth.model.AuthUser
import com.example.wardrobe.auth.model.UserProfile
import com.example.wardrobe.auth.motion.AuthMotion
import com.example.wardrobe.data.remote.EmailConfirmationRequiredException
import com.example.wardrobe.data.remote.SupabaseClientManager
import com.example.wardrobe.data.remote.SupabaseSignUpResult
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Clean AuthRepository interface.
 * UI and ViewModel communicate solely through this contract.
 */
interface AuthRepository {
    val isConfigured: Boolean get() = true
    val isPlaceholder: Boolean get() = false

    suspend fun signIn(email: String, password: String): Result<AuthUser>
    suspend fun signUp(email: String, password: String, displayName: String? = null): Result<AuthUser>
    suspend fun signInWithGoogle(): Result<AuthUser>
    suspend fun signInWithApple(): Result<AuthUser>
    suspend fun continueAsGuest(): Result<AuthUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>

    suspend fun restoreSession(): Result<AuthUser?> = Result.success(null)
    suspend fun resendVerificationEmail(email: String): Result<Unit> = Result.success(Unit)
    suspend fun verifyEmailOtp(email: String, otpCode: String): Result<AuthUser> =
        Result.failure(UnsupportedOperationException("OTP verification not supported by this repository"))
    suspend fun resetPasswordWithRecoveryCode(email: String, recoveryCode: String, newPassword: String): Result<AuthUser> =
        Result.failure(UnsupportedOperationException("Recovery code reset not supported by this repository"))
    suspend fun updatePassword(newPassword: String): Result<Unit> = Result.success(Unit)
    suspend fun fetchUserProfile(): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("Profile fetch not supported by this repository"))
    suspend fun updateUserProfile(
        fullName: String? = null,
        stylePreference: String? = null,
        avatarUrl: String? = null
    ): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("Profile update not supported by this repository"))
}

/**
 * Production Supabase Authentication & Profile Repository.
 *
 * Connects to the configured Supabase project using `SupabaseClientManager`, supporting:
 * - Real email/password signup (with `full_name` metadata for `on_auth_user_created`)
 * - Real email/password login
 * - Email verification handling & confirmation email resend / OTP verification
 * - Persistent sessions & automatic session restoration on app restart
 * - Forgot password & password reset
 * - Real logout
 * - Fetching and updating the authenticated user's profile from `public.profiles` with RLS
 */
class SupabaseAuthRepository(private val context: Context) : AuthRepository {

    init {
        SupabaseClientManager.initialize(context.applicationContext)
    }

    override val isConfigured: Boolean
        get() = SupabaseClientManager.isConfigured

    override val isPlaceholder: Boolean
        get() = false

    override suspend fun restoreSession(): Result<AuthUser?> {
        return SupabaseClientManager.restoreSessionOnLaunch(context.applicationContext)
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your email address."))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your password."))
        }
        return SupabaseClientManager.signInWithEmail(trimmedEmail, password)
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String?
    ): Result<AuthUser> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your email address."))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters long."))
        }

        val result = SupabaseClientManager.signUpWithEmail(
            email = trimmedEmail,
            password = password,
            fullName = displayName?.trim()?.ifBlank { null }
        )

        return result.fold(
            onSuccess = { signUpOutcome ->
                when (signUpOutcome) {
                    is SupabaseSignUpResult.Authenticated -> Result.success(signUpOutcome.user)
                    is SupabaseSignUpResult.ConfirmationPending -> Result.failure(
                        EmailConfirmationRequiredException(
                            email = signUpOutcome.email,
                            fullName = signUpOutcome.fullName,
                            message = signUpOutcome.message
                        )
                    )
                }
            },
            onFailure = { err -> Result.failure(err) }
        )
    }

    override suspend fun resendVerificationEmail(email: String): Result<Unit> {
        return SupabaseClientManager.resendConfirmationEmail(email)
    }

    override suspend fun verifyEmailOtp(email: String, otpCode: String): Result<AuthUser> {
        return SupabaseClientManager.verifyEmailOrRecoveryOtp(
            email = email,
            token = otpCode,
            type = "signup"
        )
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return SupabaseClientManager.sendPasswordResetEmail(email)
    }

    override suspend fun resetPasswordWithRecoveryCode(
        email: String,
        recoveryCode: String,
        newPassword: String
    ): Result<AuthUser> {
        return SupabaseClientManager.resetPasswordWithRecoveryCode(
            email = email,
            recoveryCode = recoveryCode,
            newPassword = newPassword
        )
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> {
        return SupabaseClientManager.updatePassword(newPassword)
    }

    override suspend fun fetchUserProfile(): Result<UserProfile> {
        return SupabaseClientManager.fetchUserProfile()
    }

    override suspend fun updateUserProfile(
        fullName: String?,
        stylePreference: String?,
        avatarUrl: String?
    ): Result<UserProfile> {
        return SupabaseClientManager.updateUserProfile(
            fullName = fullName,
            stylePreference = stylePreference,
            avatarUrl = avatarUrl
        )
    }

    override suspend fun signOut(): Result<Unit> {
        return SupabaseClientManager.signOut()
    }

    override suspend fun signInWithGoogle(): Result<AuthUser> {
        // Fallback if user taps social button when OAuth provider is not configured
        return Result.failure(
            IllegalStateException("Please sign in or create an account with your email and password.")
        )
    }

    override suspend fun signInWithApple(): Result<AuthUser> {
        return Result.failure(
            IllegalStateException("Please sign in or create an account with your email and password.")
        )
    }

    override suspend fun continueAsGuest(): Result<AuthUser> {
        return Result.success(
            AuthUser(
                uid = "guest_local",
                email = null,
                displayName = "Guest",
                isEmailVerified = true,
                isGuest = true
            )
        )
    }
}

/**
 * Default placeholder implementation simulating realistic network latency (600–900ms)
 * and returning valid mock responses for unit tests.
 */
class PlaceholderAuthRepository : AuthRepository {

    override val isPlaceholder: Boolean = true

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        delay(AuthMotion.SIMULATED_NETWORK_DELAY_MS)

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your email or username."))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val name = if (trimmedEmail.contains("@")) {
            trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        } else {
            trimmedEmail.replaceFirstChar { it.uppercase() }
        }

        return Result.success(
            AuthUser(
                uid = "user_simulated_${System.currentTimeMillis()}",
                email = trimmedEmail,
                displayName = name,
                isEmailVerified = true
            )
        )
    }

    override suspend fun signUp(email: String, password: String, displayName: String?): Result<AuthUser> {
        delay(AuthMotion.SIMULATED_NETWORK_DELAY_MS)

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your email address."))
        }
        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("Password must be at least 8 characters."))
        }

        val name = displayName?.trim()?.ifBlank { null }
            ?: (if (trimmedEmail.contains("@")) trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() } else "Alex")

        return Result.success(
            AuthUser(
                uid = "user_simulated_${System.currentTimeMillis()}",
                email = trimmedEmail,
                displayName = name,
                isEmailVerified = true
            )
        )
    }

    override suspend fun signInWithGoogle(): Result<AuthUser> {
        delay(650L)
        return Result.success(
            AuthUser(
                uid = "user_google_simulated",
                email = "alex.google@example.com",
                displayName = "Alex Rivera",
                isEmailVerified = true
            )
        )
    }

    override suspend fun signInWithApple(): Result<AuthUser> {
        delay(650L)
        return Result.success(
            AuthUser(
                uid = "user_apple_simulated",
                email = "alex.apple@privaterelay.appleid.com",
                displayName = "Alex Rivera",
                isEmailVerified = true
            )
        )
    }

    override suspend fun continueAsGuest(): Result<AuthUser> {
        delay(400L)
        return Result.success(
            AuthUser(
                uid = "guest_local",
                email = null,
                displayName = "Guest",
                isEmailVerified = true,
                isGuest = true
            )
        )
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        delay(500L)
        return Result.success(Unit)
    }

    override suspend fun signOut(): Result<Unit> {
        delay(100L)
        return Result.success(Unit)
    }
}

/**
 * Firebase Authentication repository preserved for compatibility.
 */
class FirebaseAuthRepository(private val context: Context) : AuthRepository {

    override val isConfigured: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Throwable) {
            false
        }

    private val auth: FirebaseAuth?
        get() = if (isConfigured) {
            try {
                FirebaseAuth.getInstance()
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth isn't configured yet. Add google-services.json to the app/ directory.")
        )
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).awaitTask()
            val user = result.user?.toAuthUser()
                ?: return Result.failure(Exception("Failed to retrieve user profile after sign-in."))
            Result.success(user)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun signUp(email: String, password: String, displayName: String?): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Firebase Auth isn't configured yet. Add google-services.json to the app/ directory.")
        )
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).awaitTask()
            val firebaseUser = result.user
                ?: return Result.failure(Exception("Failed to create user account."))

            if (!displayName.isNullOrBlank()) {
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName.trim())
                        .build()
                    firebaseUser.updateProfile(profileUpdates).awaitTask()
                } catch (ignored: Throwable) {}
            }

            Result.success(firebaseUser.toAuthUser())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(): Result<AuthUser> {
        return Result.success(
            AuthUser(
                uid = "google_user",
                email = "google.user@example.com",
                displayName = "Google User",
                isEmailVerified = true
            )
        )
    }

    override suspend fun signInWithApple(): Result<AuthUser> {
        return Result.success(
            AuthUser(
                uid = "apple_user",
                email = "apple.user@example.com",
                displayName = "Apple User",
                isEmailVerified = true
            )
        )
    }

    override suspend fun continueAsGuest(): Result<AuthUser> {
        return Result.success(
            AuthUser(
                uid = "guest_user",
                email = null,
                displayName = "Guest",
                isEmailVerified = true,
                isGuest = true
            )
        )
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.success(Unit)
        return try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).awaitTask()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.success(Unit)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        val firebaseAuth = auth ?: return Result.success(Unit)
        return try {
            firebaseAuth.signOut()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            isEmailVerified = isEmailVerified
        )
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { exception -> continuation.resumeWithException(exception) }
    addOnCanceledListener { continuation.cancel() }
}
