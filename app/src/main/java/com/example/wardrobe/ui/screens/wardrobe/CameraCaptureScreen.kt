package com.example.wardrobe.ui.screens.wardrobe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AppleSpacing
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.ui.components.AppleButton
import com.example.wardrobe.ui.components.GarmentOutlineScannerView
import com.example.wardrobe.vision.GarmentSegmentationEngine
import com.example.wardrobe.vision.GarmentSegmentationResult
import com.example.wardrobe.vision.UserSelectionMarkup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Full-featured CameraX Garment Capture Screen:
 * - Requests `Manifest.permission.CAMERA` at runtime.
 * - Uses `ProcessCameraProvider`, `Preview`, `PreviewView`, and `ImageCapture` to capture high-res garment photos.
 * - Persists captured photos in `context.filesDir/wardrobe_photos` so they survive app restarts.
 * - Supports Flash cycling (Auto / On / Off), Front/Back lens switching, and Tap-to-Focus with an animated focus ring.
 * - Provides an interactive post-capture manual selection screen where the user draws a circle or brushes over
 *   the desired garment, taps "Extract Clothing" to segment & animate the garment boundary, and then adds the
 *   transparent PNG cutout to My Wardrobe or edits details.
 */
@Composable
fun CameraCaptureScreen(
    isSavingQuickItem: Boolean = false,
    onPhotoConfirmedForForm: (Uri, Bitmap?) -> Unit,
    onQuickSaveToWardrobe: (Uri, Bitmap?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val colors = AppleTheme.colors

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isCapturing by remember { mutableStateOf(false) }
    var showShutterFlash by remember { mutableStateOf(false) }
    var captureErrorMessage by remember { mutableStateOf<String?>(null) }

    // Captured photo state for review before saving
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Tap-to-focus indicator coordinates (in pixels inside PreviewView)
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    BackHandler {
        if (capturedUri != null && !isSavingQuickItem) {
            capturedFile?.delete()
            capturedFile = null
            capturedUri = null
            capturedBitmap = null
        } else {
            onCancel()
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(950)
            focusPoint = null
        }
    }

    LaunchedEffect(showShutterFlash) {
        if (showShutterFlash) {
            delay(140)
            showShutterFlash = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            // Permission Request / Rationale UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.18f))
                        .border(1.5.dp, colors.accent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera Access",
                        tint = colors.accent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Camera Access Required",
                    style = AppleTheme.typography.title.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Grant camera permission to photograph your clothing items, automatically analyze fabric and color details, and save them to your digital wardrobe.",
                    style = AppleTheme.typography.body.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                AppleButton(
                    text = "Grant Camera Permission",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    isPrimary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp),
                    testTag = "grant_camera_permission_button"
                )

                Spacer(modifier = Modifier.height(12.dp))

                AppleButton(
                    text = "Cancel",
                    onClick = onCancel,
                    isPrimary = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 320.dp),
                    testTag = "camerax_cancel_permission_button"
                )
            }
        } else if (capturedUri != null) {
            // Post-Capture Manual Garment Selection, Animated Outline & Review Screen
            CapturedGarmentReviewContent(
                capturedUri = capturedUri!!,
                capturedBitmap = capturedBitmap,
                isSavingQuickItem = isSavingQuickItem,
                onRetake = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    capturedFile?.delete()
                    capturedFile = null
                    capturedUri = null
                    capturedBitmap = null
                },
                onEditDetails = { finalUri, finalBitmap ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPhotoConfirmedForForm(finalUri, finalBitmap)
                },
                onQuickSave = { finalUri, finalBitmap ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onQuickSaveToWardrobe(finalUri, finalBitmap)
                }
            )
        } else {
            // Live CameraX Viewfinder & Controls
            val previewView = remember {
                PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            val imageCapture = remember {
                ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flashMode)
                    .build()
            }

            var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

            LaunchedEffect(flashMode) {
                imageCapture.flashMode = flashMode
            }

            LaunchedEffect(lensFacing) {
                try {
                    val cameraProvider = context.getCameraProvider()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val requestedSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    val activeSelector = if (cameraProvider.hasCamera(requestedSelector)) {
                        requestedSelector
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        activeSelector,
                        preview,
                        imageCapture
                    )
                    cameraControl = camera.cameraControl
                    captureErrorMessage = null
                } catch (e: Exception) {
                    captureErrorMessage = "Camera initialization unavailable on this device."
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    try {
                        ProcessCameraProvider.getInstance(context).get().unbindAll()
                    } catch (_: Exception) {
                    }
                }
            }

            // 1. CameraX PreviewView with Tap-to-Focus
            AndroidView(
                factory = {
                    previewView.setOnTouchListener { view, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            view.performClick()
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(event.x, event.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            )
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            cameraControl?.startFocusAndMetering(action)
                            focusPoint = Offset(event.x, event.y)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        true
                    }
                    previewView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camerax_preview_view")
            )

            // 2. Garment Framing Guide + Tap-to-Focus Reticle Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val frameW = (size.width * 0.78f).coerceAtMost(size.height * 0.56f)
                val frameH = frameW * 1.15f
                val left = (size.width - frameW) / 2f
                val top = (size.height - frameH) / 2f - 24.dp.toPx()
                val right = left + frameW
                val bottom = top + frameH
                val bracketLen = 28.dp.toPx()
                val strokeW = 2.5.dp.toPx()
                val guideColor = Color.White.copy(alpha = 0.65f)

                // Subtle rounded guide outline
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(left, top),
                    size = Size(frameW, frameH),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Top-left corner bracket
                drawLine(guideColor, Offset(left, top + bracketLen), Offset(left, top + 8.dp.toPx()), strokeW, StrokeCap.Round)
                drawLine(guideColor, Offset(left + 8.dp.toPx(), top), Offset(left + bracketLen, top), strokeW, StrokeCap.Round)

                // Top-right corner bracket
                drawLine(guideColor, Offset(right - bracketLen, top), Offset(right - 8.dp.toPx(), top), strokeW, StrokeCap.Round)
                drawLine(guideColor, Offset(right, top + 8.dp.toPx()), Offset(right, top + bracketLen), strokeW, StrokeCap.Round)

                // Bottom-left corner bracket
                drawLine(guideColor, Offset(left, bottom - bracketLen), Offset(left, bottom - 8.dp.toPx()), strokeW, StrokeCap.Round)
                drawLine(guideColor, Offset(left + 8.dp.toPx(), bottom), Offset(left + bracketLen, bottom), strokeW, StrokeCap.Round)

                // Bottom-right corner bracket
                drawLine(guideColor, Offset(right - bracketLen, bottom), Offset(right - 8.dp.toPx(), bottom), strokeW, StrokeCap.Round)
                drawLine(guideColor, Offset(right, bottom - 8.dp.toPx()), Offset(right, bottom - bracketLen), strokeW, StrokeCap.Round)

                // Tap-to-focus ring
                focusPoint?.let { pt ->
                    drawCircle(
                        color = Color(0xFF2997FF),
                        radius = 28.dp.toPx(),
                        center = pt,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 4.dp.toPx(),
                        center = pt
                    )
                }
            }

            // 3. Top Controls Bar (Close, Framing Tip Badge, Flash Toggle)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close Button
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .testTag("camerax_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Camera",
                            tint = Color.White
                        )
                    }

                    // AI Garment Scanner Pill Badge
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.60f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF2997FF),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Garment Scanner",
                            style = AppleTheme.typography.caption.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    // Flash Mode Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                else -> ImageCapture.FLASH_MODE_AUTO
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .testTag("camerax_flash_button")
                    ) {
                        val flashIcon = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                            else -> Icons.Default.FlashAuto
                        }
                        val flashDesc = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> "Flash On"
                            ImageCapture.FLASH_MODE_OFF -> "Flash Off"
                            else -> "Flash Auto"
                        }
                        Icon(
                            imageVector = flashIcon,
                            contentDescription = flashDesc,
                            tint = if (flashMode == ImageCapture.FLASH_MODE_ON) Color(0xFFFFD60A) else Color.White
                        )
                    }
                }

                // Bottom Camera Controls Dock
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (captureErrorMessage != null) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF3B30).copy(alpha = 0.85f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = captureErrorMessage!!,
                                style = AppleTheme.typography.caption.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    } else {
                        Text(
                            text = "Center clothing item inside the frame",
                            style = AppleTheme.typography.caption.copy(
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.48f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    // Pill-shaped Camera Control Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 380.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .padding(horizontal = 28.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Spacer / Flash status label on left for symmetry
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (flashMode) {
                                    ImageCapture.FLASH_MODE_ON -> "ON"
                                    ImageCapture.FLASH_MODE_OFF -> "OFF"
                                    else -> "AUTO"
                                },
                                style = AppleTheme.typography.caption.copy(
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // Shutter Button with Spring Physics
                        val shutterInteraction = remember { MutableInteractionSource() }
                        val isShutterPressed by shutterInteraction.collectIsPressedAsState()
                        val shutterScale by animateFloatAsState(
                            targetValue = if (isShutterPressed || isCapturing) 0.86f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "shutter_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .graphicsLayer {
                                    scaleX = shutterScale
                                    scaleY = shutterScale
                                }
                                .clip(CircleShape)
                                .border(4.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(if (isCapturing) Color.White.copy(alpha = 0.6f) else Color.White)
                                .testTag("camerax_shutter_button")
                                .clickable(
                                    interactionSource = shutterInteraction,
                                    indication = null,
                                    enabled = !isCapturing
                                ) {
                                    isCapturing = true
                                    showShutterFlash = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                    val photosDir = File(context.filesDir, "wardrobe_photos").apply {
                                        if (!exists()) mkdirs()
                                    }
                                    val photoFile = File(
                                        photosDir,
                                        "garment_${System.currentTimeMillis()}.jpg"
                                    )
                                    val outputOptions = ImageCapture.OutputFileOptions
                                        .Builder(photoFile)
                                        .build()

                                    imageCapture.takePicture(
                                        outputOptions,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                val savedUri = Uri.fromFile(photoFile)
                                                val decodedBitmap = decodeCapturedBitmap(context, photoFile, savedUri)
                                                capturedFile = photoFile
                                                capturedBitmap = decodedBitmap
                                                capturedUri = savedUri
                                                isCapturing = false
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                isCapturing = false
                                                captureErrorMessage = "Could not capture photo. Please try again."
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Switch Front / Back Camera Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .testTag("camerax_switch_lens_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera Lens",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Brief White Shutter Flash Overlay
            AnimatedVisibility(
                visible = showShutterFlash,
                enter = fadeIn(tween(40)),
                exit = fadeOut(tween(120))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.75f))
                )
            }
        }
    }
}

@Composable
private fun CapturedGarmentReviewContent(
    capturedUri: Uri,
    capturedBitmap: Bitmap?,
    isSavingQuickItem: Boolean,
    onRetake: () -> Unit,
    onEditDetails: (Uri, Bitmap?) -> Unit,
    onQuickSave: (Uri, Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val colors = AppleTheme.colors
    val coroutineScope = rememberCoroutineScope()

    var sourceBitmap by remember(capturedUri) { mutableStateOf(capturedBitmap) }
    var isManualSelectionMode by remember(capturedUri) { mutableStateOf(true) }
    var userMarkup by remember(capturedUri) { mutableStateOf(UserSelectionMarkup()) }
    var isProcessingSegmentation by remember(capturedUri) { mutableStateOf(false) }
    var segmentationResult by remember(capturedUri) { mutableStateOf<GarmentSegmentationResult?>(null) }
    var selectedCandidateIndex by remember(capturedUri) { mutableIntStateOf(0) }
    var isolatedPngUri by remember(capturedUri) { mutableStateOf<Uri?>(null) }
    var isolatedCutoutBitmap by remember(capturedUri) { mutableStateOf<Bitmap?>(null) }

    // Load oriented bitmap without automatically selecting or extracting any garment
    LaunchedEffect(capturedUri) {
        if (sourceBitmap == null) {
            sourceBitmap = withContext(Dispatchers.IO) {
                GarmentSegmentationEngine.loadOrientedBitmap(context, capturedUri)
            }
        }
    }

    fun runManualExtraction(markup: UserSelectionMarkup) {
        val bmp = sourceBitmap ?: return
        isManualSelectionMode = false
        isProcessingSegmentation = true
        segmentationResult = null
        isolatedPngUri = null
        isolatedCutoutBitmap = null
        selectedCandidateIndex = 0
        coroutineScope.launch {
            val res = GarmentSegmentationEngine.segmentGarmentWithUserMarkup(bmp, markup)
            segmentationResult = res
            if (res is GarmentSegmentationResult.Success) {
                val primary = res.primaryCandidate
                val exportedFile = withContext(Dispatchers.IO) {
                    GarmentSegmentationEngine.exportTransparentPng(context, primary.cutoutBitmap)
                }
                isolatedPngUri = Uri.fromFile(exportedFile)
                isolatedCutoutBitmap = primary.cutoutBitmap
            }
            isProcessingSegmentation = false
        }
    }

    // Export updated PNG when user switches between multiple detected garments
    LaunchedEffect(selectedCandidateIndex, segmentationResult) {
        val success = segmentationResult as? GarmentSegmentationResult.Success ?: return@LaunchedEffect
        val candidate = success.candidates.getOrNull(selectedCandidateIndex) ?: return@LaunchedEffect
        val exportedFile = withContext(Dispatchers.IO) {
            GarmentSegmentationEngine.exportTransparentPng(context, candidate.cutoutBitmap)
        }
        isolatedPngUri = Uri.fromFile(exportedFile)
        isolatedCutoutBitmap = candidate.cutoutBitmap
    }

    val isCutoutReady = !isManualSelectionMode &&
        !isProcessingSegmentation &&
        segmentationResult is GarmentSegmentationResult.Success

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        // Center Interactive Manual Selection & Animated Outline Cutout Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 72.dp,
                    bottom = if (isCutoutReady) 190.dp else 16.dp
                )
        ) {
            GarmentOutlineScannerView(
                sourceBitmap = sourceBitmap,
                segmentationResult = segmentationResult,
                isProcessing = isProcessingSegmentation,
                selectedCandidateIndex = selectedCandidateIndex,
                onSelectCandidate = { idx -> selectedCandidateIndex = idx },
                onIsolationComplete = { candidate ->
                    isolatedCutoutBitmap = candidate.cutoutBitmap
                },
                onRetrySegmentation = {
                    if (userMarkup.hasPositiveSelection) {
                        runManualExtraction(userMarkup)
                    } else {
                        isManualSelectionMode = true
                    }
                },
                userMarkup = userMarkup,
                onUserMarkupChange = { userMarkup = it },
                isManualSelectionMode = isManualSelectionMode,
                onExtractClothing = { markup ->
                    userMarkup = markup
                    runManualExtraction(markup)
                },
                onRequestEditSelection = {
                    isManualSelectionMode = true
                    segmentationResult = null
                },
                darkBackdrop = true,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Retake Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onRetake,
                enabled = !isSavingQuickItem,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.60f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .testTag("camerax_retake_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retake Photo",
                    tint = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = when {
                        isManualSelectionMode -> "Mark Garment to Extract"
                        isProcessingSegmentation -> "Extracting Garment..."
                        isolatedPngUri != null -> "AI Cutout Ready"
                        else -> "Adjust Selection"
                    },
                    style = AppleTheme.typography.caption.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.size(48.dp))
        }

        // Bottom Action Card (displayed after user confirms extraction and garment is isolated)
        if (isCutoutReady) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = AppleSpacing.MaxContentWidth)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF141820).copy(alpha = 0.94f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Isolated Garment Ready",
                    style = AppleTheme.typography.title.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Your selected garment was extracted into a transparent PNG. Add directly to My Wardrobe or edit details first.",
                    style = AppleTheme.typography.caption.copy(
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val effectiveUri = isolatedPngUri ?: capturedUri
                    val effectiveBitmap = isolatedCutoutBitmap ?: sourceBitmap

                    // Edit Details in AddItemScreen (with Gemini auto-fill + isolated transparent PNG)
                    AppleButton(
                        text = "Edit Details",
                        onClick = { onEditDetails(effectiveUri, effectiveBitmap) },
                        isPrimary = false,
                        enabled = !isSavingQuickItem && !isProcessingSegmentation,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "camerax_use_photo_button"
                    )

                    // Add to Wardrobe directly (saves transparent PNG + metadata to Room DB)
                    AppleButton(
                        text = "Add to Wardrobe",
                        onClick = { onQuickSave(effectiveUri, effectiveBitmap) },
                        isPrimary = true,
                        isLoading = isSavingQuickItem,
                        enabled = !isSavingQuickItem && !isProcessingSegmentation,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1.3f),
                        testTag = "camerax_quick_save_button"
                    )
                }
            }
        }
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                continuation.resume(cameraProviderFuture.get())
            },
            ContextCompat.getMainExecutor(this)
        )
    }

private fun decodeCapturedBitmap(context: Context, file: File, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, info, _ ->
                decoder.isMutableRequired = true
                val maxDim = maxOf(info.size.width, info.size.height)
                if (maxDim > 1280) {
                    val scale = 1280f / maxDim
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
    } catch (_: Exception) {
        null
    }
}
