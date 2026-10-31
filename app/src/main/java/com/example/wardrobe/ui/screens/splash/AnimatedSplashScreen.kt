package com.example.wardrobe.ui.screens.splash

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.AppleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated Video Splash Screen:
 * - 1st video (splash_light.mp4) for Light theme (pure white background)
 * - 2nd video (splash_dark.mp4) for Dark theme (pure black background)
 *
 * Smooth hardware-accelerated playback with TextureView + MediaPlayer,
 * seamless border blending, tap-to-skip, safety timeout, and graceful fade-out transition.
 */
@Composable
fun AnimatedSplashScreen(
    onAnimationComplete: () -> Unit,
    isAppReady: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppleTheme.colors
    val isDark = colors.isDark

    val rawResId = if (isDark) R.raw.splash_dark else R.raw.splash_light
    val backgroundColor = if (isDark) Color.Black else Color.White
    val hintColor = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)

    var isCompleted by remember { mutableStateOf(false) }
    val exitAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    fun completeSplash() {
        if (!isCompleted) {
            isCompleted = true
            scope.launch {
                exitAlpha.animateTo(0f, tween(300))
                onAnimationComplete()
            }
        }
    }

    // Check system reduced-animation setting
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

    LaunchedEffect(isReducedAnimation) {
        if (isReducedAnimation) {
            onAnimationComplete()
        }
    }

    // Safety timeout: video is ~10s long; release cleanly if completion listener doesn't trigger
    LaunchedEffect(Unit) {
        delay(10500L)
        completeSplash()
    }

    // Reference to MediaPlayer for lifecycle cleanup
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
            } catch (ignored: Exception) {}
            mediaPlayerRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("animated_splash_screen")
            .background(backgroundColor)
            .alpha(exitAlpha.value)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { completeSplash() }
            ),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(9f / 16f)
                .align(Alignment.Center),
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            try {
                                val surface = Surface(surfaceTexture)
                                val mp = MediaPlayer().apply {
                                    setSurface(surface)
                                    val uri = Uri.parse("android.resource://${ctx.packageName}/$rawResId")
                                    setDataSource(ctx, uri)
                                    setVolume(0f, 0f)
                                    isLooping = false
                                    setOnPreparedListener { player ->
                                        player.start()
                                    }
                                    setOnCompletionListener {
                                        completeSplash()
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        completeSplash()
                                        true
                                    }
                                    prepareAsync()
                                }
                                mediaPlayerRef = mp
                            } catch (e: Exception) {
                                completeSplash()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            try {
                                mediaPlayerRef?.stop()
                                mediaPlayerRef?.release()
                            } catch (ignored: Exception) {}
                            mediaPlayerRef = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            }
        )

        // Subtle non-intrusive skip hint
        Text(
            text = "Tap to skip",
            style = AppleTheme.typography.caption.copy(
                fontSize = 12.sp,
                color = hintColor
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        )
    }
}
