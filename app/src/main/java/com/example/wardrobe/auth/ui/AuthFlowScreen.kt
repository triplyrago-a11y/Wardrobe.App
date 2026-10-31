package com.example.wardrobe.auth.ui

import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.auth.model.AuthTab
import com.example.wardrobe.auth.model.AuthUiState
import com.example.wardrobe.auth.motion.AuthMotion
import com.example.wardrobe.auth.viewmodel.AuthViewModel
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.AppleGlassSurface
import com.example.wardrobe.ui.components.AppleInputField
import com.example.wardrobe.ui.components.LogoMode
import com.example.wardrobe.ui.components.WardrobeLogo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Dedicated First-Launch Experience:
 * 1. Dedicated "Auth Splash": Centered 96dp logo, Apple-style spring scale (0.85 -> 1.0),
 *    fade in (450ms), single halo pulse behind logo (~800ms), hold for 600ms.
 * 2. Continuous scene transition: Logo translates/scales to header position (~44dp),
 *    while Login/Sign-up card and elements fade and slide up from 32dp below.
 * 3. Complete Apple-styled Liquid Glass Login / Sign Up experience with Guest, Google, Apple options.
 * 4. Back navigation: exits the app (never returns to splash).
 */
@Composable
fun AuthFlowScreen(
    viewModel: AuthViewModel,
    onAuthComplete: () -> Unit,
    modifier: Modifier = Modifier,
    startWithForm: Boolean = true
) {
    val context = LocalContext.current
    val colors = AppleTheme.colors
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Reduced motion accessibility check
    val isReducedAnimation = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    // Auth Splash Animation States
    val splashLogoScale = remember { Animatable(if (isReducedAnimation || startWithForm) 1.0f else AuthMotion.SPLASH_SCALE_FROM) }
    val splashLogoAlpha = remember { Animatable(if (isReducedAnimation || startWithForm) 1.0f else 0f) }
    val haloScale = remember { Animatable(AuthMotion.HALO_SCALE_FROM) }
    val haloAlpha = remember { Animatable(0f) }
    val transitionProgress = remember { Animatable(if (isReducedAnimation || startWithForm) 1.0f else 0f) }

    var isSplashActive by rememberSaveable { mutableStateOf(!isReducedAnimation && !startWithForm) }

    // Form inputs state (preserved across configuration change / tab switch)
    var activeTab by rememberSaveable { mutableStateOf(AuthTab.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showForgotDialog by remember { mutableStateOf(false) }

    // Error shake animation
    val shakeOffset = remember { Animatable(0f) }
    val errorMessage = (uiState as? AuthUiState.Error)?.reason
    val isLoading = uiState is AuthUiState.Loading

    // Trigger gentle horizontal shake on validation or auth error
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = AuthMotion.SHAKE_DURATION_MS
                    0f at 0
                    -10f at 50
                    10f at 100
                    -8f at 150
                    8f at 200
                    0f at AuthMotion.SHAKE_DURATION_MS
                }
            )
        }
    }

    // Sequence the Dedicated Auth Splash and continuous scene transition (if not already starting with form)
    LaunchedEffect(isReducedAnimation, startWithForm) {
        if (isReducedAnimation || startWithForm) {
            splashLogoScale.snapTo(1f)
            splashLogoAlpha.snapTo(1f)
            transitionProgress.snapTo(1f)
            isSplashActive = false
            return@LaunchedEffect
        }

        // 1. Logo entrance: scale up from 0.85 to 1.0 with Apple-style spring, fade in over 450ms
        launch {
            splashLogoScale.animateTo(
                targetValue = AuthMotion.SPLASH_SCALE_TO,
                animationSpec = AuthMotion.appleEntranceSpring()
            )
        }
        launch {
            splashLogoAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(AuthMotion.SPLASH_FADE_IN_MS, easing = AuthMotion.Linear)
            )
        }

        // 2. Halo pulse behind logo: scale 0.9 to 1.15, alpha 0 to 0.25 to 0 over 800ms
        launch {
            haloScale.animateTo(
                targetValue = AuthMotion.HALO_SCALE_TO,
                animationSpec = tween(AuthMotion.HALO_PULSE_DURATION_MS, easing = AuthMotion.FastOutSlow)
            )
        }
        launch {
            haloAlpha.animateTo(
                targetValue = AuthMotion.HALO_PEAK_ALPHA,
                animationSpec = tween(AuthMotion.HALO_PULSE_DURATION_MS / 2, easing = AuthMotion.FastOutSlow)
            )
            haloAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(AuthMotion.HALO_PULSE_DURATION_MS / 2, easing = AuthMotion.FastOutSlow)
            )
        }

        // 3. Hold for ~600ms so user comfortably registers brand
        delay(AuthMotion.SPLASH_FADE_IN_MS + AuthMotion.SPLASH_HOLD_MS)

        // 4. Continuous transition to login: logo smoothly scales down/translates to header position
        // while Login/Sign Up elements fade and slide up from 32dp below
        transitionProgress.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(AuthMotion.TRANSITION_TO_FORM_MS, easing = AuthMotion.AppleEaseInOut)
        )
        isSplashActive = false
    }

    // Back navigation: exits the app (never returns to splash)
    BackHandler {
        (context as? Activity)?.finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("auth_flow_screen")
            .background(colors.bgPrimary)
    ) {
        val progress = transitionProgress.value

        // Splash Phase: Centered Logo and Glowing Halo (visible when progress < 1f)
        if (progress < 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(1f - progress),
                contentAlignment = Alignment.Center
            ) {
                // Subtle circular accent halo pulse
                if (haloAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .scale(haloScale.value)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        colors.accent.copy(alpha = haloAlpha.value),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Centered Splash Logo (~96dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    WardrobeLogo(
                        mode = LogoMode.Symbol,
                        size = 96.dp,
                        modifier = Modifier
                            .scale(splashLogoScale.value)
                            .alpha(splashLogoAlpha.value)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(splashLogoAlpha.value)
                    ) {
                        Text(
                            text = "Wardrobe",
                            style = AppleTheme.typography.title.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = ".AI",
                            style = AppleTheme.typography.title.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                }
            }
        }

        // Login / Sign Up Form Screen (slides up and fades in continuously)
        if (progress > 0f) {
            val formAlpha = progress
            val formSlideOffset = ((1f - progress) * AuthMotion.FORM_SLIDE_UP_OFFSET_DP).dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(formAlpha)
                    .offset(y = formSlideOffset)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(AppleSpacing.Space48))

                // Shared Header: Wardrobe AI Logo (compact ~44dp) + Wordmark + Calming Subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = AppleSpacing.Space24)
                ) {
                    WardrobeLogo(
                        mode = LogoMode.Lockup,
                        size = 44.dp,
                        fontSize = 24f,
                        modifier = Modifier.testTag("auth_header_logo")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            fadeIn(tween(AuthMotion.INTERACTION_FAST)) togetherWith fadeOut(tween(AuthMotion.INTERACTION_FAST))
                        },
                        label = "header_subtitle_crossfade"
                    ) { tab ->
                        Text(
                            text = if (tab == AuthTab.SIGN_IN) {
                                "Welcome back. Intelligently organize your style."
                            } else {
                                "Your personal style, intelligently organized."
                            },
                            style = AppleTheme.typography.body.copy(
                                fontSize = 15.sp,
                                color = colors.textSecondary
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                // Centered Card Container (max width 460dp for tablet/landscape polish)
                Column(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .padding(horizontal = AppleSpacing.Space24),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Apple-style Segmented Control (Pill Toggle)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.bgSecondary)
                            .border(1.dp, colors.cardBorder, CircleShape)
                            .padding(4.dp)
                            .testTag("auth_tab_selector")
                    ) {
                        Row(
                            modifier = Modifier.width(280.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val isSignIn = activeTab == AuthTab.SIGN_IN

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSignIn) colors.accent else Color.Transparent)
                                    .clickable {
                                        if (activeTab != AuthTab.SIGN_IN) {
                                            activeTab = AuthTab.SIGN_IN
                                            password = ""
                                            confirmPassword = ""
                                            viewModel.clearError()
                                        }
                                    }
                                    .testTag("tab_sign_in"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign In",
                                    style = AppleTheme.typography.caption.copy(
                                        fontWeight = if (isSignIn) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSignIn) Color.White else colors.textPrimary
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(CircleShape)
                                    .background(if (!isSignIn) colors.accent else Color.Transparent)
                                    .clickable {
                                        if (activeTab != AuthTab.CREATE_ACCOUNT) {
                                            activeTab = AuthTab.CREATE_ACCOUNT
                                            password = ""
                                            confirmPassword = ""
                                            viewModel.clearError()
                                        }
                                    }
                                    .testTag("tab_create_account"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Create Account",
                                    style = AppleTheme.typography.caption.copy(
                                        fontWeight = if (!isSignIn) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (!isSignIn) Color.White else colors.textPrimary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                    // Liquid Glass Form Card
                    AppleGlassSurface(
                        shape = RoundedCornerShape(AppleSpacing.CardCornerRadius),
                        elevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(shakeOffset.value.dp.roundToPx(), 0) }
                    ) {
                        Column(
                            modifier = Modifier.padding(AppleSpacing.Space24),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Full Name field (for Create Account only)
                            AnimatedVisibility(
                                visible = activeTab == AuthTab.CREATE_ACCOUNT,
                                enter = fadeIn(tween(AuthMotion.INTERACTION_NORMAL)),
                                exit = fadeOut(tween(AuthMotion.INTERACTION_NORMAL))
                            ) {
                                Column {
                                    AppleInputField(
                                        value = displayName,
                                        onValueChange = {
                                            displayName = it
                                            viewModel.clearError()
                                        },
                                        label = "Full Name",
                                        placeholder = "e.g. Alex Rivera",
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Text,
                                            imeAction = ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                        ),
                                        testTag = "input_full_name"
                                    )
                                    Spacer(modifier = Modifier.height(AppleSpacing.Space16))
                                }
                            }

                            // Email Field
                            AppleInputField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    viewModel.clearError()
                                },
                                label = "Email Address",
                                placeholder = if (activeTab == AuthTab.SIGN_IN) "alex@example.com" else "name@example.com",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                testTag = "input_email"
                            )

                            Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                            // Password Field (Liquid Glass with show/hide toggle)
                            ApplePasswordField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    viewModel.clearError()
                                },
                                label = "Password",
                                placeholder = if (activeTab == AuthTab.CREATE_ACCOUNT) "Min. 8 characters" else "Enter password",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = if (activeTab == AuthTab.SIGN_IN) ImeAction.Done else ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (activeTab == AuthTab.SIGN_IN) {
                                            viewModel.signIn(email, password, onAuthComplete)
                                        }
                                    }
                                ),
                                testTag = "input_password"
                            )

                            // Confirm Password & Strength Hint (for Create Account)
                            AnimatedVisibility(
                                visible = activeTab == AuthTab.CREATE_ACCOUNT,
                                enter = fadeIn(tween(AuthMotion.INTERACTION_NORMAL)),
                                exit = fadeOut(tween(AuthMotion.INTERACTION_NORMAL))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    PasswordStrengthBar(password = password)
                                    Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                                    ApplePasswordField(
                                        value = confirmPassword,
                                        onValueChange = {
                                            confirmPassword = it
                                            viewModel.clearError()
                                        },
                                        label = "Confirm Password",
                                        placeholder = "Re-enter password",
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                focusManager.clearFocus()
                                                viewModel.signUp(email, password, confirmPassword, displayName, onAuthComplete)
                                            }
                                        ),
                                        testTag = "input_confirm_password"
                                    )
                                }
                            }

                            // "Forgot Password?" subtle link (Sign In tab only)
                            if (activeTab == AuthTab.SIGN_IN) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "Forgot Password?",
                                        style = AppleTheme.typography.caption.copy(
                                            color = colors.accent,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { showForgotDialog = true }
                                            .padding(4.dp)
                                            .testTag("forgot_password_button")
                                    )
                                }
                            }

                            // Friendly Inline Error Message
                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = errorMessage,
                                    style = AppleTheme.typography.caption.copy(
                                        color = colors.destructive,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_error_message")
                                )
                            }

                            Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                            // Primary Action Button (Brand Accent Full-Width Pill)
                            val primaryButtonText = if (activeTab == AuthTab.SIGN_IN) "Sign In" else "Create Account"
                            val isFormValid = if (activeTab == AuthTab.SIGN_IN) {
                                email.isNotBlank() && password.isNotBlank() && !isLoading
                            } else {
                                displayName.isNotBlank() && email.isNotBlank() && password.length >= 8 && confirmPassword.isNotBlank() && !isLoading
                            }

                            AppleButton(
                                text = primaryButtonText,
                                onClick = {
                                    focusManager.clearFocus()
                                    if (activeTab == AuthTab.SIGN_IN) {
                                        viewModel.signIn(email, password, onAuthComplete)
                                    } else {
                                        viewModel.signUp(email, password, confirmPassword, displayName, onAuthComplete)
                                    }
                                },
                                isPrimary = true,
                                enabled = isFormValid,
                                isLoading = isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "primary_auth_button"
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Subtle "Continue as Guest" text button beneath primary button
                            Text(
                                text = "Continue as Guest",
                                style = AppleTheme.typography.body.copy(
                                    fontSize = 14.sp,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.continueAsGuest(onAuthComplete)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                                    .testTag("guest_login_button")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppleSpacing.Space24))

                    // Social / Quick Sign-In Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.cardBorder))
                        Text(
                            text = "or",
                            style = AppleTheme.typography.caption.copy(color = colors.textSecondary),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.cardBorder))
                    }

                    Spacer(modifier = Modifier.height(AppleSpacing.Space16))

                    // "Continue with Google" Secondary Glass Button
                    SocialAuthButton(
                        text = "Continue with Google",
                        icon = { GoogleMonochromeGlyph(tint = colors.textPrimary) },
                        onClick = { viewModel.signInWithGoogle(onAuthComplete) },
                        testTag = "google_auth_button"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // "Continue with Apple" Secondary Glass Button
                    SocialAuthButton(
                        text = "Continue with Apple",
                        icon = { AppleMonochromeGlyph(tint = colors.textPrimary) },
                        onClick = { viewModel.signInWithApple(onAuthComplete) },
                        testTag = "apple_auth_button"
                    )

                    Spacer(modifier = Modifier.height(AppleSpacing.Space32))

                    // Terms & Privacy Caption at bottom
                    Text(
                        text = "By continuing you agree to the Terms of Service and Privacy Policy",
                        style = AppleTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = AppleSpacing.Space48)
                            .testTag("auth_terms_caption")
                    )
                }
            }
        }
    }

    // Email Verification Required Dialog (when signup or login requires email confirmation)
    val verificationState = uiState as? AuthUiState.EmailVerificationRequired
    if (verificationState != null) {
        EmailVerificationDialog(
            email = verificationState.email,
            message = verificationState.message,
            hasEnteredPassword = password.isNotBlank(),
            onVerifyOtp = { otpCode, onErr ->
                viewModel.verifyEmailOtp(
                    email = verificationState.email,
                    otpCode = otpCode,
                    onSuccess = onAuthComplete,
                    onError = onErr
                )
            },
            onSignInAfterLinkVerification = {
                viewModel.dismissEmailVerification()
                activeTab = AuthTab.SIGN_IN
                email = verificationState.email
                if (password.isNotBlank()) {
                    viewModel.signIn(verificationState.email, password, onAuthComplete)
                }
            },
            onResendEmail = { onResendResult ->
                viewModel.resendVerificationEmail(verificationState.email, onResendResult)
            },
            onDismiss = {
                viewModel.dismissEmailVerification()
                activeTab = AuthTab.SIGN_IN
                email = verificationState.email
            }
        )
    }

    // Forgot Password & Password Reset Dialog
    if (showForgotDialog) {
        ForgotPasswordDialog(
            initialEmail = email,
            onDismiss = { showForgotDialog = false },
            onSendResetEmail = { resetEmail, onResult ->
                viewModel.sendPasswordReset(resetEmail, onResult)
            },
            onResetWithRecoveryCode = { resetEmail, code, newPass, onResult ->
                viewModel.resetPasswordWithRecoveryCode(resetEmail, code, newPass) { success, msg ->
                    onResult(success, msg)
                    if (success) {
                        showForgotDialog = false
                        onAuthComplete()
                    }
                }
            }
        )
    }
}

/**
 * Secondary Glass Button for Google & Apple quick sign-in
 */
@Composable
private fun SocialAuthButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = AppleTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(colors.bgSecondary)
            .border(1.dp, colors.cardBorder, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = AppleTheme.typography.body.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            )
        }
    }
}

/**
 * Pixel-crisp Google 'G' glyph in monochrome/theme tint
 */
@Composable
private fun GoogleMonochromeGlyph(tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(w * 0.95f, h * 0.50f)
            cubicTo(w * 0.95f, h * 0.45f, w * 0.94f, h * 0.41f, w * 0.93f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.56f)
            lineTo(w * 0.76f, h * 0.56f)
            cubicTo(w * 0.74f, h * 0.65f, w * 0.69f, h * 0.73f, w * 0.60f, h * 0.78f)
            lineTo(w * 0.60f, h * 0.96f)
            cubicTo(w * 0.75f, h * 0.88f, w * 0.95f, h * 0.74f, w * 0.95f, h * 0.50f)
            close()

            moveTo(w * 0.50f, h * 0.98f)
            cubicTo(w * 0.64f, h * 0.98f, w * 0.75f, h * 0.93f, w * 0.84f, h * 0.85f)
            lineTo(w * 0.70f, h * 0.74f)
            cubicTo(w * 0.65f, h * 0.78f, w * 0.58f, h * 0.80f, w * 0.50f, h * 0.80f)
            cubicTo(w * 0.36f, h * 0.80f, w * 0.24f, h * 0.70f, w * 0.19f, h * 0.58f)
            lineTo(w * 0.05f, h * 0.69f)
            cubicTo(w * 0.14f, h * 0.86f, w * 0.31f, h * 0.98f, w * 0.50f, h * 0.98f)
            close()

            moveTo(w * 0.19f, h * 0.58f)
            cubicTo(w * 0.18f, h * 0.54f, w * 0.17f, h * 0.50f, w * 0.17f, h * 0.46f)
            cubicTo(w * 0.17f, h * 0.42f, w * 0.18f, h * 0.38f, w * 0.19f, h * 0.34f)
            lineTo(w * 0.05f, h * 0.23f)
            cubicTo(w * 0.02f, h * 0.30f, 0f, h * 0.38f, 0f, h * 0.46f)
            cubicTo(0f, h * 0.54f, w * 0.02f, h * 0.62f, w * 0.05f, h * 0.69f)
            lineTo(w * 0.19f, h * 0.58f)
            close()

            moveTo(w * 0.50f, h * 0.12f)
            cubicTo(w * 0.61f, h * 0.12f, w * 0.70f, h * 0.16f, w * 0.77f, h * 0.23f)
            lineTo(w * 0.87f, h * 0.13f)
            cubicTo(w * 0.78f, h * 0.05f, w * 0.65f, 0f, w * 0.50f, 0f)
            cubicTo(w * 0.31f, 0f, w * 0.14f, h * 0.12f, w * 0.05f, h * 0.29f)
            lineTo(w * 0.19f, h * 0.40f)
            cubicTo(w * 0.24f, h * 0.28f, w * 0.36f, h * 0.12f, w * 0.50f, h * 0.12f)
            close()
        }
        drawPath(path, color = tint, style = Fill)
    }
}

/**
 * Pixel-crisp Apple glyph
 */
@Composable
private fun AppleMonochromeGlyph(tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Scale Y into 0.04..0.96 so the Apple silhouette & leaf never clip at top or bottom
        fun sx(x: Float) = w * (0.04f + x * 0.92f)
        fun sy(y: Float) = h * (0.02f + y * 0.88f)

        val appleBody = Path().apply {
            moveTo(sx(0.77f), sy(0.53f))
            cubicTo(sx(0.77f), sy(0.41f), sx(0.87f), sy(0.35f), sx(0.87f), sy(0.34f))
            cubicTo(sx(0.82f), sy(0.26f), sx(0.73f), sy(0.25f), sx(0.70f), sy(0.24f))
            cubicTo(sx(0.62f), sy(0.23f), sx(0.53f), sy(0.29f), sx(0.49f), sy(0.29f))
            cubicTo(sx(0.44f), sy(0.29f), sx(0.38f), sy(0.24f), sx(0.31f), sy(0.24f))
            cubicTo(sx(0.22f), sy(0.24f), sx(0.13f), sy(0.30f), sx(0.08f), sy(0.39f))
            cubicTo(sx(-0.01f), sy(0.55f), sx(0.05f), sy(0.80f), sx(0.15f), sy(0.94f))
            cubicTo(sx(0.20f), sy(1.01f), sx(0.25f), sy(1.08f), sx(0.33f), sy(1.08f))
            cubicTo(sx(0.40f), sy(1.08f), sx(0.43f), sy(1.03f), sx(0.51f), sy(1.03f))
            cubicTo(sx(0.60f), sy(1.03f), sx(0.63f), sy(1.08f), sx(0.70f), sy(1.08f))
            cubicTo(sx(0.78f), sy(1.08f), sx(0.83f), sy(1.01f), sx(0.88f), sy(0.94f))
            cubicTo(sx(0.93f), sy(0.86f), sx(0.96f), sy(0.78f), sx(0.96f), sy(0.77f))
            cubicTo(sx(0.96f), sy(0.77f), sx(0.77f), sy(0.70f), sx(0.77f), sy(0.53f))
            close()
        }

        val appleLeaf = Path().apply {
            moveTo(sx(0.58f), sy(0.15f))
            cubicTo(sx(0.62f), sy(0.10f), sx(0.65f), sy(0.03f), sx(0.64f), sy(0f))
            cubicTo(sx(0.58f), sy(0.01f), sx(0.50f), sy(0.05f), sx(0.46f), sy(0.10f))
            cubicTo(sx(0.43f), sy(0.14f), sx(0.40f), sy(0.20f), sx(0.41f), sy(0.24f))
            cubicTo(sx(0.48f), sy(0.24f), sx(0.55f), sy(0.20f), sx(0.58f), sy(0.15f))
            close()
        }

        drawPath(appleBody, color = tint, style = Fill)
        drawPath(appleLeaf, color = tint, style = Fill)
    }
}

/**
 * 3-stage password strength bar
 */
@Composable
private fun PasswordStrengthBar(password: String) {
    val colors = AppleTheme.colors

    val strength = remember(password) {
        when {
            password.length < 8 -> 0
            password.length < 10 || !password.any { it.isDigit() } -> 1
            password.any { it.isDigit() } && password.any { it.isUpperCase() } && password.length >= 10 -> 3
            else -> 2
        }
    }

    val (label, labelColor) = when (strength) {
        0 -> "Min 8 characters required" to colors.textSecondary
        1 -> "Fair" to Color(0xFFFF9500)
        2 -> "Good" to colors.accent
        else -> "Strong" to Color(0xFF34C759)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                val active = strength > index
                val barColor = when {
                    !active -> colors.cardBorder
                    strength == 1 -> Color(0xFFFF9500)
                    strength == 2 -> colors.accent
                    else -> Color(0xFF34C759)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = AppleTheme.typography.caption.copy(
                fontSize = 11.sp,
                color = labelColor,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/**
 * Email Verification Dialog shown when Supabase requires email confirmation after signup
 * or when an unverified account attempts to sign in.
 */
@Composable
private fun EmailVerificationDialog(
    email: String,
    message: String,
    hasEnteredPassword: Boolean,
    onVerifyOtp: (String, (String) -> Unit) -> Unit,
    onSignInAfterLinkVerification: () -> Unit,
    onResendEmail: ((Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppleTheme.colors
    var otpCode by remember { mutableStateOf("") }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        AppleGlassSurface(
            shape = RoundedCornerShape(22.dp),
            elevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("email_verification_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verify Your Email",
                    style = AppleTheme.typography.title.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = AppleTheme.typography.caption.copy(
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                AppleInputField(
                    value = otpCode,
                    onValueChange = {
                        otpCode = it
                        statusBanner = null
                    },
                    label = "Confirmation Code (Optional if using Email Link)",
                    placeholder = "Enter 6-digit code if provided",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    testTag = "input_verification_otp"
                )

                if (statusBanner != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusBanner!!,
                        style = AppleTheme.typography.caption.copy(
                            color = if (isStatusError) colors.destructive else colors.accent,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (otpCode.trim().length >= 6) {
                    AppleButton(
                        text = if (isVerifying) "Verifying..." else "Verify Code & Continue",
                        onClick = {
                            isVerifying = true
                            statusBanner = null
                            onVerifyOtp(otpCode) { err ->
                                isVerifying = false
                                isStatusError = true
                                statusBanner = err
                            }
                        },
                        isPrimary = true,
                        isLoading = isVerifying,
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "verify_otp_button"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                AppleButton(
                    text = if (hasEnteredPassword) "I've Confirmed My Email — Sign In" else "Continue to Sign In",
                    onClick = onSignInAfterLinkVerification,
                    isPrimary = otpCode.trim().length < 6,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "verified_sign_in_button"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isResending) "Sending..." else "Resend Email",
                        style = AppleTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isResending) {
                                isResending = true
                                onResendEmail { ok, msg ->
                                    isResending = false
                                    isStatusError = !ok
                                    statusBanner = msg
                                }
                            }
                            .padding(6.dp)
                            .testTag("resend_verification_button")
                    )

                    Text(
                        text = "Back to Sign In",
                        style = AppleTheme.typography.caption.copy(
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(6.dp)
                            .testTag("dismiss_verification_button")
                    )
                }
            }
        }
    }
}

/**
 * Forgot password and password reset dialog supporting both:
 * 1. Requesting a password reset email from Supabase
 * 2. Completing the password reset with the recovery code + new password (or via email link)
 */
@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSendResetEmail: (String, (Boolean, String) -> Unit) -> Unit,
    onResetWithRecoveryCode: (String, String, String, (Boolean, String) -> Unit) -> Unit
) {
    val colors = AppleTheme.colors
    var emailInput by remember { mutableStateOf(initialEmail) }
    var recoveryCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showCodeResetStep by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        AppleGlassSurface(
            shape = RoundedCornerShape(20.dp),
            elevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("forgot_password_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (showCodeResetStep) "Set New Password" else "Reset Password",
                    style = AppleTheme.typography.title.copy(fontSize = 20.sp, color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (showCodeResetStep) {
                        "Click the recovery link in your email, or enter the recovery code from your email along with your new password below."
                    } else {
                        "Enter your account email address to receive a password reset link and recovery code from Supabase."
                    },
                    style = AppleTheme.typography.caption.copy(color = colors.textSecondary),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                AppleInputField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it
                        feedbackMessage = null
                    },
                    label = "Email Address",
                    placeholder = "name@example.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    testTag = "reset_email_input"
                )

                if (showCodeResetStep) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AppleInputField(
                        value = recoveryCode,
                        onValueChange = {
                            recoveryCode = it
                            feedbackMessage = null
                        },
                        label = "Recovery Code (from Email)",
                        placeholder = "Enter recovery code",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        testTag = "reset_recovery_code_input"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ApplePasswordField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            feedbackMessage = null
                        },
                        label = "New Password",
                        placeholder = "Min. 8 characters",
                        testTag = "reset_new_password_input"
                    )
                }

                if (feedbackMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = feedbackMessage!!,
                        style = AppleTheme.typography.caption.copy(
                            color = if (isError) colors.destructive else colors.accent,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!showCodeResetStep) {
                    AppleButton(
                        text = if (isProcessing) "Sending..." else "Send Reset Instructions",
                        onClick = {
                            isProcessing = true
                            feedbackMessage = null
                            onSendResetEmail(emailInput) { ok, msg ->
                                isProcessing = false
                                isError = !ok
                                feedbackMessage = msg
                                if (ok) {
                                    showCodeResetStep = true
                                }
                            }
                        },
                        isPrimary = true,
                        isLoading = isProcessing,
                        enabled = emailInput.isNotBlank() && !isProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "send_reset_instructions_button"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Already have a recovery code?",
                        style = AppleTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showCodeResetStep = true }
                            .padding(4.dp)
                    )
                } else {
                    AppleButton(
                        text = if (isProcessing) "Updating Password..." else "Update Password & Sign In",
                        onClick = {
                            isProcessing = true
                            feedbackMessage = null
                            onResetWithRecoveryCode(emailInput, recoveryCode, newPassword) { ok, msg ->
                                isProcessing = false
                                isError = !ok
                                feedbackMessage = msg
                            }
                        },
                        isPrimary = true,
                        isLoading = isProcessing,
                        enabled = emailInput.isNotBlank() && recoveryCode.isNotBlank() && newPassword.length >= 8 && !isProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "confirm_password_reset_button"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Resend Reset Email",
                        style = AppleTheme.typography.caption.copy(
                            color = colors.accent,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isProcessing) {
                                isProcessing = true
                                onSendResetEmail(emailInput) { ok, msg ->
                                    isProcessing = false
                                    isError = !ok
                                    feedbackMessage = msg
                                }
                            }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Close",
                    style = AppleTheme.typography.caption.copy(
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss)
                        .padding(6.dp)
                )
            }
        }
    }
}

