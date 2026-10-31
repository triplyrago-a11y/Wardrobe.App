package com.example.wardrobe.auth.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wardrobe.auth.model.AuthUiState
import com.example.wardrobe.auth.model.AuthUser
import com.example.wardrobe.auth.model.UserProfile
import com.example.wardrobe.auth.repository.AuthRepository
import com.example.wardrobe.auth.repository.SupabaseAuthRepository
import com.example.wardrobe.data.local.PreferencesManager
import com.example.wardrobe.data.remote.EmailConfirmationRequiredException
import com.example.wardrobe.data.remote.SupabaseClientManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing Supabase Authentication, Session Restoration, Email Verification,
 * Password Reset, and `public.profiles` synchronization.
 */
class AuthViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = SupabaseAuthRepository(application),
    private val preferencesManager: PreferencesManager = PreferencesManager(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentProfile: StateFlow<UserProfile?> = SupabaseClientManager.currentProfile
    val passwordRecoveryPending: StateFlow<Boolean> = SupabaseClientManager.passwordRecoveryPending

    val isAuthIntroCompleted: Flow<Boolean> = preferencesManager.isAuthIntroCompleted

    val isPlaceholderBackend: Boolean
        get() = authRepository.isPlaceholder

    init {
        restorePersistedSession()
    }

    /**
     * Automatically restores any persisted Supabase session when the app starts,
     * refreshes the token if needed, and syncs `public.profiles` to local preferences.
     */
    fun restorePersistedSession() {
        val cached = SupabaseClientManager.getPersistedSession(getApplication())
        if (cached != null) {
            _uiState.value = AuthUiState.Success(cached.toAuthUser())
        }

        viewModelScope.launch {
            val result = authRepository.restoreSession()
            result.onSuccess { restoredUser ->
                if (restoredUser != null) {
                    _uiState.value = AuthUiState.Success(restoredUser)
                    preferencesManager.setAuthIntroCompleted(true)
                    preferencesManager.setFirstLaunchDone(true)
                    if (!restoredUser.displayName.isNullOrBlank()) {
                        preferencesManager.setUserName(restoredUser.displayName)
                    }
                    if (!restoredUser.stylePreference.isNullOrBlank()) {
                        preferencesManager.setStylePreference(restoredUser.stylePreference)
                    }
                }
            }
        }
    }

    fun handleDeepLink(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val result = SupabaseClientManager.handleDeepLinkIntent(uri)
            result.onSuccess { user ->
                if (user != null) {
                    preferencesManager.setAuthIntroCompleted(true)
                    preferencesManager.setFirstLaunchDone(true)
                    if (!user.displayName.isNullOrBlank()) {
                        preferencesManager.setUserName(user.displayName)
                    }
                    _uiState.value = AuthUiState.Success(user)
                }
            }
        }
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun dismissEmailVerification() {
        if (_uiState.value is AuthUiState.EmailVerificationRequired) {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun signIn(email: String, pass: String, onSuccess: () -> Unit) {
        if (_uiState.value is AuthUiState.Loading) return

        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            _uiState.value = AuthUiState.Error("Please enter your email address.")
            return
        }
        if (pass.isEmpty()) {
            _uiState.value = AuthUiState.Error("Please enter your password.")
            return
        }

        _uiState.value = AuthUiState.Loading("Signing in...")

        viewModelScope.launch {
            val result = authRepository.signIn(trimmedEmail, pass)
            result.onSuccess { user ->
                preferencesManager.setAuthIntroCompleted(true)
                preferencesManager.setFirstLaunchDone(true)
                if (!user.displayName.isNullOrBlank()) {
                    preferencesManager.setUserName(user.displayName)
                }
                if (!user.stylePreference.isNullOrBlank()) {
                    preferencesManager.setStylePreference(user.stylePreference)
                }
                _uiState.value = AuthUiState.Success(user)
                onSuccess()
            }.onFailure { error ->
                if (error is EmailConfirmationRequiredException) {
                    _uiState.value = AuthUiState.EmailVerificationRequired(
                        email = error.email,
                        fullName = error.fullName,
                        message = error.message
                    )
                } else {
                    _uiState.value = AuthUiState.Error(
                        error.localizedMessage ?: "Sign-in failed. Please verify your email and password."
                    )
                }
            }
        }
    }

    fun signUp(
        email: String,
        pass: String,
        confirmPass: String,
        displayName: String?,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value is AuthUiState.Loading) return

        val trimmedEmail = email.trim()
        val trimmedName = displayName?.trim()
        if (trimmedName.isNullOrEmpty()) {
            _uiState.value = AuthUiState.Error("Please enter your full name.")
            return
        }
        if (trimmedEmail.isEmpty()) {
            _uiState.value = AuthUiState.Error("Please enter your email address.")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (pass.length < 8) {
            _uiState.value = AuthUiState.Error("Password must be at least 8 characters long.")
            return
        }
        if (pass != confirmPass) {
            _uiState.value = AuthUiState.Error("Passwords do not match.")
            return
        }

        _uiState.value = AuthUiState.Loading("Creating your account...")

        viewModelScope.launch {
            val result = authRepository.signUp(trimmedEmail, pass, trimmedName)
            result.onSuccess { user ->
                preferencesManager.setAuthIntroCompleted(true)
                preferencesManager.setFirstLaunchDone(true)
                preferencesManager.setUserName(user.displayName ?: trimmedName)
                _uiState.value = AuthUiState.Success(user)
                onSuccess()
            }.onFailure { error ->
                if (error is EmailConfirmationRequiredException) {
                    preferencesManager.setUserName(trimmedName)
                    _uiState.value = AuthUiState.EmailVerificationRequired(
                        email = error.email,
                        fullName = error.fullName ?: trimmedName,
                        message = error.message
                    )
                } else {
                    _uiState.value = AuthUiState.Error(
                        error.localizedMessage ?: "Account creation failed. Please try again."
                    )
                }
            }
        }
    }

    fun resendVerificationEmail(email: String, onResult: (Boolean, String) -> Unit) {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "Please enter a valid email address.")
            return
        }
        viewModelScope.launch {
            val result = authRepository.resendVerificationEmail(trimmed)
            result.onSuccess {
                onResult(true, "Confirmation email resent to $trimmed. Please check your inbox and spam folder.")
            }.onFailure { err ->
                onResult(false, err.localizedMessage ?: "Could not resend confirmation email.")
            }
        }
    }

    fun verifyEmailOtp(
        email: String,
        otpCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmedCode = otpCode.trim()
        if (trimmedCode.length < 6) {
            onError("Please enter the verification code from your email.")
            return
        }
        viewModelScope.launch {
            val result = authRepository.verifyEmailOtp(email.trim(), trimmedCode)
            result.onSuccess { user ->
                preferencesManager.setAuthIntroCompleted(true)
                preferencesManager.setFirstLaunchDone(true)
                if (!user.displayName.isNullOrBlank()) {
                    preferencesManager.setUserName(user.displayName)
                }
                _uiState.value = AuthUiState.Success(user)
                onSuccess()
            }.onFailure { err ->
                onError(err.localizedMessage ?: "Invalid or expired verification code.")
            }
        }
    }

    fun signInWithGoogle(onSuccess: () -> Unit) {
        if (_uiState.value is AuthUiState.Loading) return
        _uiState.value = AuthUiState.Loading("Connecting with Google...")

        viewModelScope.launch {
            val result = authRepository.signInWithGoogle()
            result.onSuccess { user ->
                preferencesManager.setAuthIntroCompleted(true)
                preferencesManager.setFirstLaunchDone(true)
                _uiState.value = AuthUiState.Success(user)
                onSuccess()
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Google sign-in is not enabled.")
            }
        }
    }

    fun signInWithApple(onSuccess: () -> Unit) {
        if (_uiState.value is AuthUiState.Loading) return
        _uiState.value = AuthUiState.Loading("Connecting with Apple...")

        viewModelScope.launch {
            val result = authRepository.signInWithApple()
            result.onSuccess { user ->
                preferencesManager.setAuthIntroCompleted(true)
                preferencesManager.setFirstLaunchDone(true)
                _uiState.value = AuthUiState.Success(user)
                onSuccess()
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Apple sign-in is not enabled.")
            }
        }
    }

    fun continueAsGuest(onSuccess: () -> Unit) {
        if (_uiState.value is AuthUiState.Loading) return
        _uiState.value = AuthUiState.Loading("Continuing as Guest...")

        viewModelScope.launch {
            val result = authRepository.continueAsGuest()
            result.onSuccess { user ->
                preferencesManager.setAuthIntroCompleted(true)
                preferencesManager.setFirstLaunchDone(true)
                _uiState.value = AuthUiState.Success(user)
                onSuccess()
            }.onFailure {
                preferencesManager.setAuthIntroCompleted(true)
                preferencesManager.setFirstLaunchDone(true)
                onSuccess()
            }
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            onResult(false, "Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(trimmed)
            result.onSuccess {
                onResult(true, "Password recovery email sent to $trimmed.")
            }.onFailure { err ->
                onResult(false, err.localizedMessage ?: "Failed to send password reset email.")
            }
        }
    }

    fun resetPasswordWithRecoveryCode(
        email: String,
        recoveryCode: String,
        newPassword: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val trimmedEmail = email.trim()
        val trimmedCode = recoveryCode.trim()
        if (trimmedEmail.isEmpty()) {
            onResult(false, "Please enter your email address.")
            return
        }
        if (trimmedCode.isEmpty()) {
            onResult(false, "Please enter the recovery code from your email.")
            return
        }
        if (newPassword.length < 8) {
            onResult(false, "New password must be at least 8 characters long.")
            return
        }

        viewModelScope.launch {
            val result = authRepository.resetPasswordWithRecoveryCode(trimmedEmail, trimmedCode, newPassword)
            result.onSuccess { user ->
                preferencesManager.setAuthIntroCompleted(true)
                preferencesManager.setFirstLaunchDone(true)
                if (!user.displayName.isNullOrBlank()) {
                    preferencesManager.setUserName(user.displayName)
                }
                _uiState.value = AuthUiState.Success(user)
                onResult(true, "Your password has been reset and you are now signed in.")
            }.onFailure { err ->
                onResult(false, err.localizedMessage ?: "Failed to reset password. Please check your recovery code.")
            }
        }
    }

    fun updatePassword(newPassword: String, onResult: (Boolean, String) -> Unit) {
        if (newPassword.length < 8) {
            onResult(false, "Password must be at least 8 characters long.")
            return
        }
        viewModelScope.launch {
            val result = authRepository.updatePassword(newPassword)
            result.onSuccess {
                onResult(true, "Password updated successfully!")
            }.onFailure { err ->
                onResult(false, err.localizedMessage ?: "Failed to update password.")
            }
        }
    }

    fun refreshUserProfile(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = authRepository.fetchUserProfile()
            result.onSuccess { profile ->
                if (!profile.fullName.isNullOrBlank()) {
                    preferencesManager.setUserName(profile.fullName)
                }
                if (!profile.stylePreference.isNullOrBlank()) {
                    preferencesManager.setStylePreference(profile.stylePreference)
                }
                val currentUser = (_uiState.value as? AuthUiState.Success)?.user
                if (currentUser != null) {
                    _uiState.value = AuthUiState.Success(
                        currentUser.copy(
                            displayName = profile.fullName?.ifBlank { null } ?: currentUser.displayName,
                            email = profile.email?.ifBlank { null } ?: currentUser.email,
                            avatarUrl = profile.avatarUrl ?: currentUser.avatarUrl,
                            stylePreference = profile.stylePreference ?: currentUser.stylePreference,
                            updatedAt = profile.updatedAt
                        )
                    )
                }
                onResult?.invoke(true, "Profile synced from Supabase.")
            }.onFailure { err ->
                onResult?.invoke(false, err.localizedMessage ?: "Could not fetch cloud profile.")
            }
        }
    }

    fun updateUserProfile(
        fullName: String? = null,
        stylePreference: String? = null,
        avatarUrl: String? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            if (!fullName.isNullOrBlank()) {
                preferencesManager.setUserName(fullName.trim())
            }
            if (!stylePreference.isNullOrBlank()) {
                preferencesManager.setStylePreference(stylePreference)
            }
            val result = authRepository.updateUserProfile(
                fullName = fullName,
                stylePreference = stylePreference,
                avatarUrl = avatarUrl
            )
            result.onSuccess { profile ->
                val currentUser = (_uiState.value as? AuthUiState.Success)?.user
                if (currentUser != null) {
                    _uiState.value = AuthUiState.Success(
                        currentUser.copy(
                            displayName = profile.fullName?.ifBlank { null } ?: currentUser.displayName,
                            stylePreference = profile.stylePreference ?: currentUser.stylePreference,
                            avatarUrl = profile.avatarUrl ?: currentUser.avatarUrl,
                            updatedAt = profile.updatedAt
                        )
                    )
                }
                onResult?.invoke(true, "Profile updated in Supabase!")
            }.onFailure { err ->
                onResult?.invoke(false, err.localizedMessage ?: "Saved locally (cloud sync requires active session).")
            }
        }
    }

    fun resetAuthIntro() {
        viewModelScope.launch {
            authRepository.signOut()
            preferencesManager.resetAuthIntro()
            _uiState.value = AuthUiState.Idle
        }
    }

    fun signOut(onSignedOut: (() -> Unit)? = null) {
        viewModelScope.launch {
            authRepository.signOut()
            preferencesManager.resetAuthIntro()
            _uiState.value = AuthUiState.Idle
            onSignedOut?.invoke()
        }
    }

    fun deleteAccount(password: String? = null, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            preferencesManager.resetAuthIntro()
            preferencesManager.setFirstLaunchDone(false)
            _uiState.value = AuthUiState.Idle
            onComplete(true, null)
        }
    }
}
