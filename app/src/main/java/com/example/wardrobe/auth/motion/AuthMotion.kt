package com.example.wardrobe.auth.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Named motion constants, timing, springs, and easings for Apple-inspired auth & splash transitions.
 */
object AuthMotion {
    // Splash entrance
    const val SPLASH_FADE_IN_MS = 450
    const val SPLASH_SCALE_FROM = 0.85f
    const val SPLASH_SCALE_TO = 1.0f

    // Halo pulse behind logo
    const val HALO_PULSE_DURATION_MS = 800
    const val HALO_SCALE_FROM = 0.90f
    const val HALO_SCALE_TO = 1.15f
    const val HALO_PEAK_ALPHA = 0.25f

    // Hold before transition to form
    const val SPLASH_HOLD_MS = 600L

    // Continuous scene transition to form
    const val TRANSITION_TO_FORM_MS = 480
    const val FORM_SLIDE_UP_OFFSET_DP = 32

    // Simulated network delay for placeholder auth
    const val SIMULATED_NETWORK_DELAY_MS = 800L

    // Micro-interactions
    const val INTERACTION_FAST = 140
    const val INTERACTION_NORMAL = 200
    const val SHAKE_DURATION_MS = 280

    // Apple-style Easings & Springs
    val FastOutSlow = FastOutSlowInEasing
    val Linear = LinearEasing
    val AppleEaseInOut = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

    fun <T> appleEntranceSpring() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessLow
    )

    fun <T> buttonPressSpring() = spring<T>(
        dampingRatio = 0.72f,
        stiffness = 500f
    )
}
