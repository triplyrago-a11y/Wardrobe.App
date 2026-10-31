package com.example.wardrobe.auth.model

/**
 * Clean data model representing an authenticated user.
 * Independent of third-party SDK dependencies.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean = true,
    val avatarUrl: String? = null,
    val stylePreference: String? = null,
    val updatedAt: String? = null,
    val isGuest: Boolean = false
)

/**
 * Represents a row in `public.profiles` fetched/updated with Supabase RLS.
 */
data class UserProfile(
    val id: String,
    val email: String? = null,
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val stylePreference: String? = null,
    val updatedAt: String? = null
)

/**
 * Tab switcher selection on the authentication screen.
 */
enum class AuthTab {
    SIGN_IN,
    CREATE_ACCOUNT
}

/**
 * Clean UI state machine for auth operations (login, signup, email verification, password reset).
 */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data class Loading(val message: String = "Authenticating...") : AuthUiState
    data class Error(val reason: String) : AuthUiState
    data class EmailVerificationRequired(
        val email: String,
        val fullName: String? = null,
        val message: String = "We sent a confirmation link to your email. Please verify your email address to activate your account."
    ) : AuthUiState
    data class Success(val user: AuthUser) : AuthUiState
}

/**
 * Backwards compatibility alias if referenced elsewhere.
 */
typealias AuthState = AuthUiState
