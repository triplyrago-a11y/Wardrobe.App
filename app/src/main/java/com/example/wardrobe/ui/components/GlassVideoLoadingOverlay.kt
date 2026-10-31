package com.example.wardrobe.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.AppleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Apple-style Liquid Glass Video Loading Overlay
 * - Shows when switching pages / loading next destination (4-5s duration, matching video length)
 * - 1st video (loading_light.mp4) for Light theme
 * - 2nd video (loading_dark.mp4) for Dark theme
 * - Blurred background scrim with a mid-small rounded square HUD in center
 * - Hardware-accelerated TextureView + MediaPlayer for zero-flicker playback
 */
@Composable
fun GlassVideoLoadingOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 4040L, // Exact length of the 4.04s video
    allowTapToDismiss: Boolean = true
) {
    if (!visible) return

    val context = LocalContext.current
    val colors = AppleTheme.colors
    val isDark = colors.isDark

    val rawResId = if (isDark) R.raw.loading_dark else R.raw.loading_light

    // Manage completion & smooth exit
    var isClosing by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(0f) }
    val hudScale = remember { Animatable(0.85f) }
    val scope = rememberCoroutineScope()

    fun dismissSmoothly() {
        if (!isClosing) {
            isClosing = true
            scope.launch {
                overlayAlpha.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                onDismiss()
            }
        }
    }

    // Entrance animation
    LaunchedEffect(Unit) {
        launch {
            overlayAlpha.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        }
        launch {
            hudScale.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    // Exact video duration timer (~4.04s)
    LaunchedEffect(durationMs) {
        delay(durationMs)
        dismissSmoothly()
    }

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
            .testTag("glass_video_loading_overlay")
            .alpha(overlayAlpha.value)
            .background(Color.Black.copy(alpha = if (isDark) 0.55f else 0.35f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    if (allowTapToDismiss) {
                        dismissSmoothly()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Mid-small rounded square HUD card (136dp x 136dp)
        val hudShape = RoundedCornerShape(26.dp)
        val hudBgColor = Color(0xFF0A0A0C).copy(alpha = 0.92f)
        val hudBorderColor = if (isDark) {
            Color.White.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.28f)
        }

        Box(
            modifier = Modifier
                .size(136.dp)
                .scale(hudScale.value)
                .shadow(elevation = 20.dp, shape = hudShape, spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(hudShape)
                .background(hudBgColor)
                .border(1.dp, hudBorderColor, hudShape)
                .testTag("loading_rounded_square"),
            contentAlignment = Alignment.Center
        ) {
            // Center the video inside the mid-small square:
            // Video is 1080x1920 (9:16). The hanger is at dead center (~230x212 px).
            // By scaling by 2.0x, the hanger is ~65dp wide and centered perfectly.
            AndroidView(
                modifier = Modifier
                    .size(136.dp)
                    .scale(2.0f)
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
                                            dismissSmoothly()
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            dismissSmoothly()
                                            true
                                        }
                                        prepareAsync()
                                    }
                                    mediaPlayerRef = mp
                                } catch (e: Exception) {
                                    dismissSmoothly()
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
        }
    }
}
