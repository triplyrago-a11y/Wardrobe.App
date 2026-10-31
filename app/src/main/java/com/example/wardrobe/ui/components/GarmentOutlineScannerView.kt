package com.example.wardrobe.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppleTheme
import com.example.wardrobe.vision.GarmentCandidate
import com.example.wardrobe.vision.GarmentSegmentationResult
import com.example.wardrobe.vision.NormalizedPoint
import com.example.wardrobe.vision.SegmentationStage
import com.example.wardrobe.vision.SelectionStroke
import com.example.wardrobe.vision.SelectionToolMode
import com.example.wardrobe.vision.UserSelectionMarkup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive User-Controlled Clothing Selection, Guided Garment Segmentation,
 * Animated Silhouette Boundary Stroke & Transparent Cutout View.
 *
 * Workflow:
 * 1. Displays the original image in an interactive selection stage WITHOUT automatically
 *    selecting, outlining, or removing the background.
 * 2. Lets the user mark the desired clothing item using:
 *    - **Brush**: Paint directly over the garment.
 *    - **Circle / Freehand**: Draw a closed freehand loop or circle around the garment.
 *    - **Eraser**: Remove incorrectly marked regions.
 *    - **Zoom / Pan**: Pinch-to-zoom or pan while keeping all strokes 1:1 aligned with image coordinates.
 *    - **Undo**, **Clear**, and **Brush Size** adjustment.
 * 3. When the user taps **Extract Clothing**, passes the user's [UserSelectionMarkup] as seed prompts
 *    to segment the actual garment boundary, animates a glowing stroke along the segmented garment
 *    silhouette (never outlining the rough user circle/brush), removes the background, and reveals
 *    the isolated garment on a transparent backdrop.
 */
@Composable
fun GarmentOutlineScannerView(
    sourceBitmap: Bitmap?,
    segmentationResult: GarmentSegmentationResult?,
    isProcessing: Boolean,
    selectedCandidateIndex: Int,
    onSelectCandidate: (Int) -> Unit,
    onIsolationComplete: (GarmentCandidate) -> Unit,
    onRetrySegmentation: () -> Unit,
    modifier: Modifier = Modifier,
    darkBackdrop: Boolean = true,
    userMarkup: UserSelectionMarkup = UserSelectionMarkup(),
    onUserMarkupChange: ((UserSelectionMarkup) -> Unit)? = null,
    isManualSelectionMode: Boolean = false,
    onExtractClothing: ((UserSelectionMarkup) -> Unit)? = null,
    onRequestEditSelection: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = AppleTheme.colors
    val density = LocalDensity.current

    // Internal fallback state if caller does not hoist userMarkup
    var internalMarkup by remember(sourceBitmap) { mutableStateOf(UserSelectionMarkup()) }
    val effectiveMarkup = if (onUserMarkupChange != null) userMarkup else internalMarkup
    val updateMarkup: (UserSelectionMarkup) -> Unit = { updated ->
        if (onUserMarkupChange != null) {
            onUserMarkupChange(updated)
        } else {
            internalMarkup = updated
        }
    }

    var activeTool by remember { mutableStateOf(SelectionToolMode.BRUSH) }
    var brushSizeNormalized by remember { mutableFloatStateOf(0.065f) }
    var inProgressStroke by remember { mutableStateOf<SelectionStroke?>(null) }
    var lastTouchImagePoint by remember { mutableStateOf<NormalizedPoint?>(null) }
    var selectionHintWarning by remember { mutableStateOf<String?>(null) }

    // Zoom & pan state (kept in canvas space; strokes are stored in normalized [0..1] image space)
    var zoomScale by remember(sourceBitmap) { mutableFloatStateOf(1f) }
    var panOffset by remember(sourceBitmap) { mutableStateOf(Offset.Zero) }
    var canvasPixelSize by remember { mutableStateOf(IntSize.Zero) }

    var stage by remember {
        mutableStateOf(
            if (isManualSelectionMode) SegmentationStage.AWAITING_USER_SELECTION
            else SegmentationStage.IDLE
        )
    }
    var showOriginalComparison by remember { mutableStateOf(false) }
    var animationReplayTrigger by remember { mutableIntStateOf(0) }

    // Animation progress values for post-extraction garment boundary trace & background removal
    val baseContourAlpha = remember { Animatable(0f) }
    val strokeTraceProgress = remember { Animatable(0f) }
    val strokeGlowAlpha = remember { Animatable(0f) }
    val backgroundOpacity = remember { Animatable(1f) }
    val isolatedScale = remember { Animatable(1f) }

    val activeCandidate: GarmentCandidate? = when (segmentationResult) {
        is GarmentSegmentationResult.Success -> {
            segmentationResult.candidates.getOrElse(selectedCandidateIndex) {
                segmentationResult.candidates.firstOrNull()
            }
        }
        else -> null
    }

    // Synchronize stage and run 5-phase boundary animation only AFTER user triggers extraction
    LaunchedEffect(
        segmentationResult,
        selectedCandidateIndex,
        isProcessing,
        isManualSelectionMode,
        animationReplayTrigger
    ) {
        if (isManualSelectionMode && !isProcessing) {
            stage = SegmentationStage.AWAITING_USER_SELECTION
            baseContourAlpha.snapTo(0f)
            strokeTraceProgress.snapTo(0f)
            strokeGlowAlpha.snapTo(0f)
            backgroundOpacity.snapTo(1f)
            isolatedScale.snapTo(1f)
            return@LaunchedEffect
        }

        if (isProcessing) {
            stage = SegmentationStage.PHASE_A_DETECTING
            baseContourAlpha.snapTo(0f)
            strokeTraceProgress.snapTo(0f)
            strokeGlowAlpha.snapTo(0f)
            backgroundOpacity.snapTo(1f)
            isolatedScale.snapTo(1f)
            return@LaunchedEffect
        }

        when (segmentationResult) {
            is GarmentSegmentationResult.Success -> {
                val candidate = activeCandidate ?: return@LaunchedEffect
                showOriginalComparison = false
                zoomScale = 1f
                panOffset = Offset.Zero

                // Reset to start of Phase B
                stage = SegmentationStage.PHASE_B_CONTOUR_APPEARING
                backgroundOpacity.snapTo(1f)
                strokeTraceProgress.snapTo(0f)
                isolatedScale.snapTo(1f)

                // Phase B: Lock contour onto segmented garment silhouette (160ms)
                strokeGlowAlpha.snapTo(1f)
                baseContourAlpha.animateTo(
                    targetValue = 0.45f,
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                )

                // Phase C: Continuous luminous stroke tracing around actual garment perimeter (1080ms)
                stage = SegmentationStage.PHASE_C_TRACING_OUTLINE
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                strokeTraceProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 1080,
                        easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                    )
                )

                // Phase D: Smoothly fade out background while keeping garment stable (480ms)
                stage = SegmentationStage.PHASE_D_REMOVING_BACKGROUND
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                launch {
                    isolatedScale.animateTo(
                        targetValue = 1.016f,
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    )
                    isolatedScale.animateTo(
                        targetValue = 1.0f,
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    )
                }
                backgroundOpacity.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                )

                // Softly settle the luminous outline so the clean isolated garment takes center stage
                launch {
                    baseContourAlpha.animateTo(0.18f, tween(380))
                    strokeGlowAlpha.animateTo(0.32f, tween(420))
                }

                // Phase E: Final Isolated Transparent Result
                stage = SegmentationStage.PHASE_E_ISOLATED_READY
                onIsolationComplete(candidate)
            }

            is GarmentSegmentationResult.Failure -> {
                stage = SegmentationStage.ERROR
                backgroundOpacity.snapTo(1f)
                strokeGlowAlpha.snapTo(0f)
            }

            null -> {
                stage = if (isManualSelectionMode) {
                    SegmentationStage.AWAITING_USER_SELECTION
                } else {
                    SegmentationStage.IDLE
                }
            }
        }
    }

    var showDelayedProcessingBadge by remember { mutableStateOf(false) }
    LaunchedEffect(stage) {
        if (stage == SegmentationStage.PHASE_A_DETECTING) {
            delay(150)
            showDelayedProcessingBadge = true
        } else {
            showDelayedProcessingBadge = false
        }
    }

    val displaySource = (segmentationResult as? GarmentSegmentationResult.Success)?.sourceBitmap
        ?: (segmentationResult as? GarmentSegmentationResult.Failure)?.fallbackBitmap
        ?: sourceBitmap

    val paddingPx = with(density) { 12.dp.toPx() }

    // Helper that computes the unzoomed aspect-fitted image Rect inside the canvas
    fun computeBaseImageRect(canvasW: Float, canvasH: Float, bmp: Bitmap): Rect {
        val imgW = bmp.width.toFloat().coerceAtLeast(1f)
        val imgH = bmp.height.toFloat().coerceAtLeast(1f)
        val availW = (canvasW - paddingPx * 2f).coerceAtLeast(1f)
        val availH = (canvasH - paddingPx * 2f).coerceAtLeast(1f)
        val fitScale = min(availW / imgW, availH / imgH)
        val drawW = imgW * fitScale
        val drawH = imgH * fitScale
        val drawLeft = (canvasW - drawW) * 0.5f
        val drawTop = (canvasH - drawH) * 0.5f
        return Rect(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("garment_outline_scanner_view"),
        contentAlignment = Alignment.Center
    ) {
        if (displaySource != null) {
            val srcImageBitmap = remember(displaySource) { displaySource.asImageBitmap() }
            val cutoutImageBitmap = remember(activeCandidate) {
                activeCandidate?.cutoutBitmap?.asImageBitmap()
            }
            val contourPoints = activeCandidate?.contourPoints ?: emptyList()

            // Reserve space at the bottom when the interactive selection toolbar is visible
            val canvasBottomInset = if (isManualSelectionMode && !isProcessing) 168.dp else 0.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = canvasBottomInset)
                    .onSizeChanged { canvasPixelSize = it }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Offscreen compositing allows BlendMode.Clear for real-time Eraser strokes
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .testTag("garment_contour_canvas")
                        .pointerInput(
                            isManualSelectionMode,
                            isProcessing,
                            activeTool,
                            brushSizeNormalized,
                            displaySource
                        ) {
                            if (!isManualSelectionMode || isProcessing) return@pointerInput

                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                val cW = size.width.toFloat()
                                val cH = size.height.toFloat()
                                if (cW <= 1f || cH <= 1f) return@awaitEachGesture

                                val baseRect = computeBaseImageRect(cW, cH, displaySource)
                                val pivotX = cW * 0.5f
                                val pivotY = cH * 0.5f

                                fun screenToNormalized(screenPt: Offset): NormalizedPoint? {
                                    // Invert zoom and pan around canvas center
                                    val unzoomedX = (screenPt.x - pivotX - panOffset.x) / zoomScale + pivotX
                                    val unzoomedY = (screenPt.y - pivotY - panOffset.y) / zoomScale + pivotY
                                    val nx = (unzoomedX - baseRect.left) / baseRect.width.coerceAtLeast(1f)
                                    val ny = (unzoomedY - baseRect.top) / baseRect.height.coerceAtLeast(1f)
                                    if (nx < -0.05f || nx > 1.05f || ny < -0.05f || ny > 1.05f) return null
                                    return NormalizedPoint(
                                        x = nx.coerceIn(0f, 1f),
                                        y = ny.coerceIn(0f, 1f)
                                    )
                                }

                                selectionHintWarning = null

                                if (activeTool == SelectionToolMode.PAN_ZOOM) {
                                    // Pan/Zoom gesture mode
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (zoomChange != 1f || panChange != Offset.Zero) {
                                            val newScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
                                            val maxPanX = (cW * (newScale - 1f)) * 0.5f
                                            val maxPanY = (cH * (newScale - 1f)) * 0.5f
                                            zoomScale = newScale
                                            panOffset = Offset(
                                                x = (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                y = (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                            )
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } while (event.changes.any { it.pressed })
                                } else {
                                    // Drawing mode (BRUSH, CIRCLE_FREEHAND, or ERASER) with multi-touch pinch fallback
                                    val startNorm = screenToNormalized(firstDown.position)
                                    val pointsList = ArrayList<NormalizedPoint>()
                                    if (startNorm != null) {
                                        pointsList.add(startNorm)
                                        lastTouchImagePoint = startNorm
                                        inProgressStroke = SelectionStroke(
                                            tool = activeTool,
                                            points = pointsList.toList(),
                                            strokeWidthNormalized = brushSizeNormalized
                                        )
                                    }
                                    firstDown.consume()

                                    var switchedToMultiTouchZoom = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressedCount = event.changes.count { it.pressed }
                                        if (pressedCount >= 2) {
                                            // User placed a second finger to pinch-zoom / pan
                                            switchedToMultiTouchZoom = true
                                            inProgressStroke = null
                                            lastTouchImagePoint = null
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            val newScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
                                            val maxPanX = (cW * (newScale - 1f)) * 0.5f
                                            val maxPanY = (cH * (newScale - 1f)) * 0.5f
                                            zoomScale = newScale
                                            panOffset = Offset(
                                                x = (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                y = (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                            )
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        } else if (!switchedToMultiTouchZoom && pressedCount == 1) {
                                            val change = event.changes.firstOrNull { it.pressed }
                                            if (change != null) {
                                                val normPt = screenToNormalized(change.position)
                                                if (normPt != null) {
                                                    val lastPt = pointsList.lastOrNull()
                                                    if (lastPt == null ||
                                                        hypot(normPt.x - lastPt.x, normPt.y - lastPt.y) >= 0.004f
                                                    ) {
                                                        pointsList.add(normPt)
                                                        inProgressStroke = SelectionStroke(
                                                            tool = activeTool,
                                                            points = pointsList.toList(),
                                                            strokeWidthNormalized = brushSizeNormalized
                                                        )
                                                    }
                                                    lastTouchImagePoint = normPt
                                                }
                                                if (change.positionChanged()) {
                                                    change.consume()
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    lastTouchImagePoint = null
                                    if (!switchedToMultiTouchZoom && pointsList.isNotEmpty()) {
                                        val completedStroke = SelectionStroke(
                                            tool = activeTool,
                                            points = pointsList.toList(),
                                            strokeWidthNormalized = brushSizeNormalized
                                        )
                                        updateMarkup(effectiveMarkup.addStroke(completedStroke))
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    inProgressStroke = null
                                }
                            }
                        }
                ) {
                    val canvasW = size.width
                    val canvasH = size.height

                    // Draw subtle studio transparency checkerboard where background is removed
                    val bgAlpha = if (showOriginalComparison || isManualSelectionMode) 1f else backgroundOpacity.value
                    if (bgAlpha < 0.99f) {
                        drawTransparencyBackdrop(
                            canvasW = canvasW,
                            canvasH = canvasH,
                            revealFraction = 1f - bgAlpha,
                            darkMode = darkBackdrop
                        )
                    }

                    val baseRect = computeBaseImageRect(canvasW, canvasH, displaySource)
                    val drawLeft = baseRect.left
                    val drawTop = baseRect.top
                    val drawW = baseRect.width
                    val drawH = baseRect.height
                    val pivot = Offset(canvasW * 0.5f, canvasH * 0.5f)

                    withTransform({
                        translate(left = panOffset.x, top = panOffset.y)
                        scale(scaleX = zoomScale, scaleY = zoomScale, pivot = pivot)
                    }) {
                        // Layer 1: Original image (remains 100% visible during manual selection; fades out in Phase D)
                        if (bgAlpha > 0.01f) {
                            val dimFactor = if (!isManualSelectionMode && stage == SegmentationStage.PHASE_C_TRACING_OUTLINE) {
                                1f - (strokeTraceProgress.value * 0.18f)
                            } else {
                                1f
                            }
                            drawImage(
                                image = srcImageBitmap,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(displaySource.width, displaySource.height),
                                dstOffset = IntOffset(drawLeft.roundToInt(), drawTop.roundToInt()),
                                dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                                alpha = (bgAlpha * dimFactor).coerceIn(0f, 1f)
                            )
                        }

                        // Layer 2: Semi-transparent Manual Selection Overlay (Brush, Circle/Freehand, Eraser)
                        if (isManualSelectionMode) {
                            val allStrokesToRender = if (inProgressStroke != null) {
                                effectiveMarkup.strokes + inProgressStroke!!
                            } else {
                                effectiveMarkup.strokes
                            }

                            if (allStrokesToRender.isNotEmpty()) {
                                // Render user strokes in an isolated layer so Eraser strokes clear only user marks,
                                // never erasing the underlying photograph!
                                drawContext.canvas.saveLayer(
                                    Rect(0f, 0f, canvasW, canvasH),
                                    androidx.compose.ui.graphics.Paint()
                                )

                                val minDrawDim = min(drawW, drawH).coerceAtLeast(1f)
                                for (stroke in allStrokesToRender) {
                                    if (stroke.points.isEmpty()) continue
                                    val strokeWidthPx = (stroke.strokeWidthNormalized * minDrawDim).coerceAtLeast(6f)

                                    when (stroke.tool) {
                                        SelectionToolMode.BRUSH -> {
                                            val brushPath = buildOpenStrokePath(
                                                points = stroke.points,
                                                left = drawLeft,
                                                top = drawTop,
                                                width = drawW,
                                                height = drawH
                                            )
                                            if (stroke.points.size == 1) {
                                                val pt = stroke.points.first()
                                                drawCircle(
                                                    color = Color(0xFF2997FF).copy(alpha = 0.42f),
                                                    radius = strokeWidthPx * 0.5f,
                                                    center = Offset(drawLeft + pt.x * drawW, drawTop + pt.y * drawH)
                                                )
                                            } else {
                                                drawPath(
                                                    path = brushPath,
                                                    color = Color(0xFF2997FF).copy(alpha = 0.42f),
                                                    style = Stroke(
                                                        width = strokeWidthPx,
                                                        cap = StrokeCap.Round,
                                                        join = StrokeJoin.Round
                                                    )
                                                )
                                                drawPath(
                                                    path = brushPath,
                                                    color = Color(0xFF70D7FF).copy(alpha = 0.72f),
                                                    style = Stroke(
                                                        width = 2.dp.toPx(),
                                                        cap = StrokeCap.Round,
                                                        join = StrokeJoin.Round
                                                    )
                                                )
                                            }
                                        }

                                        SelectionToolMode.CIRCLE_FREEHAND -> {
                                            val loopPath = buildClosedLoopPath(
                                                points = stroke.points,
                                                left = drawLeft,
                                                top = drawTop,
                                                width = drawW,
                                                height = drawH
                                            )
                                            // Semi-transparent fill inside the freehand circle so garment beneath is visible
                                            if (stroke.points.size >= 3) {
                                                drawPath(
                                                    path = loopPath,
                                                    color = Color(0xFF2997FF).copy(alpha = 0.24f)
                                                )
                                            }
                                            // Glowing freehand loop outline
                                            drawPath(
                                                path = loopPath,
                                                color = Color(0xFF5AC8FA).copy(alpha = 0.92f),
                                                style = Stroke(
                                                    width = 3.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round,
                                                    pathEffect = PathEffect.dashPathEffect(
                                                        floatArrayOf(14f, 8f),
                                                        0f
                                                    )
                                                )
                                            )
                                        }

                                        SelectionToolMode.ERASER -> {
                                            val erasePath = buildOpenStrokePath(
                                                points = stroke.points,
                                                left = drawLeft,
                                                top = drawTop,
                                                width = drawW,
                                                height = drawH
                                            )
                                            if (stroke.points.size == 1) {
                                                val pt = stroke.points.first()
                                                drawCircle(
                                                    color = Color.Transparent,
                                                    radius = strokeWidthPx * 0.55f,
                                                    center = Offset(drawLeft + pt.x * drawW, drawTop + pt.y * drawH),
                                                    blendMode = BlendMode.Clear
                                                )
                                            } else {
                                                drawPath(
                                                    path = erasePath,
                                                    color = Color.Transparent,
                                                    style = Stroke(
                                                        width = strokeWidthPx * 1.1f,
                                                        cap = StrokeCap.Round,
                                                        join = StrokeJoin.Round
                                                    ),
                                                    blendMode = BlendMode.Clear
                                                )
                                            }
                                        }

                                        SelectionToolMode.PAN_ZOOM -> {}
                                    }
                                }

                                drawContext.canvas.restore()
                            }

                            // Active touch cursor ring for Brush / Eraser
                            val activeTouch = lastTouchImagePoint
                            if (activeTouch != null &&
                                (activeTool == SelectionToolMode.BRUSH || activeTool == SelectionToolMode.ERASER)
                            ) {
                                val minDrawDim = min(drawW, drawH).coerceAtLeast(1f)
                                val cursorRadius = (brushSizeNormalized * minDrawDim * 0.5f).coerceAtLeast(6f)
                                val center = Offset(
                                    drawLeft + activeTouch.x * drawW,
                                    drawTop + activeTouch.y * drawH
                                )
                                val ringColor = if (activeTool == SelectionToolMode.ERASER) {
                                    Color(0xFFFF453A)
                                } else {
                                    Color.White
                                }
                                drawCircle(
                                    color = ringColor,
                                    radius = cursorRadius,
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        // Layer 3: Isolated transparent garment cutout (after extraction)
                        if (!isManualSelectionMode && cutoutImageBitmap != null && !showOriginalComparison) {
                            val scaleVal = isolatedScale.value
                            val scaledW = drawW * scaleVal
                            val scaledH = drawH * scaleVal
                            val scaledLeft = (canvasW - scaledW) * 0.5f
                            val scaledTop = (canvasH - scaledH) * 0.5f

                            drawImage(
                                image = cutoutImageBitmap,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(
                                    activeCandidate!!.cutoutBitmap.width,
                                    activeCandidate.cutoutBitmap.height
                                ),
                                dstOffset = IntOffset(scaledLeft.roundToInt(), scaledTop.roundToInt()),
                                dstSize = IntSize(scaledW.roundToInt(), scaledH.roundToInt()),
                                alpha = 1f
                            )
                        }

                        // Layer 4: Animated Luminous Silhouette Contour Stroke (derived strictly from segmented garment mask)
                        if (!isManualSelectionMode &&
                            contourPoints.size >= 3 &&
                            strokeGlowAlpha.value > 0.01f &&
                            !showOriginalComparison
                        ) {
                            val fullContourPath = buildAlignedContourPath(
                                points = contourPoints,
                                left = drawLeft,
                                top = drawTop,
                                width = drawW,
                                height = drawH
                            )

                            val glowAlpha = strokeGlowAlpha.value
                            val baseAlpha = baseContourAlpha.value

                            if (baseAlpha > 0.01f) {
                                drawPath(
                                    path = fullContourPath,
                                    color = Color(0xFF70D7FF).copy(alpha = (baseAlpha * 0.55f).coerceIn(0f, 1f)),
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            val traceProgress = strokeTraceProgress.value
                            if (traceProgress > 0.005f) {
                                val pathMeasure = PathMeasure()
                                pathMeasure.setPath(fullContourPath, true)
                                val totalLength = pathMeasure.length

                                if (totalLength > 1f) {
                                    val tracedPath = Path()
                                    val stopDist = totalLength * traceProgress
                                    pathMeasure.getSegment(
                                        startDistance = 0f,
                                        stopDistance = stopDist,
                                        destination = tracedPath,
                                        startWithMoveTo = true
                                    )

                                    val luminousBrush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF5AC8FA).copy(alpha = glowAlpha),
                                            Color(0xFFE0F7FF).copy(alpha = glowAlpha),
                                            Color(0xFF7AE582).copy(alpha = glowAlpha * 0.92f),
                                            Color(0xFF5AC8FA).copy(alpha = glowAlpha)
                                        ),
                                        start = Offset(drawLeft, drawTop),
                                        end = Offset(drawLeft + drawW, drawTop + drawH)
                                    )

                                    // Soft Outer Luminous Halo
                                    drawPath(
                                        path = tracedPath,
                                        color = Color(0xFF5AC8FA).copy(alpha = (glowAlpha * 0.30f).coerceIn(0f, 1f)),
                                        style = Stroke(
                                            width = 7.5.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )

                                    // Primary Gradient Contour Stroke
                                    drawPath(
                                        path = tracedPath,
                                        brush = luminousBrush,
                                        style = Stroke(
                                            width = 3.2.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )

                                    // Crisp White Precision Core
                                    drawPath(
                                        path = tracedPath,
                                        color = Color.White.copy(alpha = (glowAlpha * 0.92f).coerceIn(0f, 1f)),
                                        style = Stroke(
                                            width = 1.4.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )

                                    // Leading-edge travelling light head during active tracing (Phase C)
                                    if (traceProgress < 0.99f) {
                                        val headPos = pathMeasure.getPosition(stopDist)
                                        if (headPos != Offset.Unspecified) {
                                            drawCircle(
                                                color = Color(0xFF5AC8FA).copy(alpha = 0.42f * glowAlpha),
                                                radius = 10.dp.toPx(),
                                                center = headPos
                                            )
                                            drawCircle(
                                                color = Color.White.copy(alpha = 0.98f * glowAlpha),
                                                radius = 3.8.dp.toPx(),
                                                center = headPos
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Top Status Pill + Zoom Reset / Post-Extraction Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stage Status Pill
            AnimatedVisibility(
                visible = stage != SegmentationStage.IDLE &&
                    (stage != SegmentationStage.PHASE_A_DETECTING || showDelayedProcessingBadge),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150))
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF10141C).copy(alpha = 0.82f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("segmentation_stage_badge"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (stage) {
                        SegmentationStage.AWAITING_USER_SELECTION -> {
                            Icon(
                                imageVector = if (activeTool == SelectionToolMode.CIRCLE_FREEHAND) {
                                    Icons.Default.RadioButtonUnchecked
                                } else {
                                    Icons.Default.Brush
                                },
                                contentDescription = null,
                                tint = Color(0xFF5AC8FA),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        SegmentationStage.PHASE_A_DETECTING,
                        SegmentationStage.PHASE_B_CONTOUR_APPEARING -> {
                            CircularProgressIndicator(
                                color = Color(0xFF5AC8FA),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        SegmentationStage.PHASE_C_TRACING_OUTLINE,
                        SegmentationStage.PHASE_D_REMOVING_BACKGROUND -> {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF5AC8FA),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        SegmentationStage.PHASE_E_ISOLATED_READY -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        SegmentationStage.ERROR -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        SegmentationStage.IDLE -> {}
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showOriginalComparison) "Original Photo" else stage.statusLabel,
                        style = AppleTheme.typography.caption.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            // Right utility pills
            if (isManualSelectionMode && zoomScale > 1.05f) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF10141C).copy(alpha = 0.82f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .testTag("reset_zoom_button")
                        .clickable {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${String.format("%.1fx", zoomScale)} · Reset",
                        style = AppleTheme.typography.caption.copy(
                            color = Color(0xFF5AC8FA),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            } else if (!isManualSelectionMode && stage == SegmentationStage.PHASE_E_ISOLATED_READY) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Adjust Selection button (returns to manual brush/circle selection)
                    if (onRequestEditSelection != null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF10141C).copy(alpha = 0.78f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .testTag("edit_selection_marks_button")
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onRequestEditSelection()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Adjust Selection",
                                    tint = Color(0xFF5AC8FA),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Redraw",
                                    style = AppleTheme.typography.caption.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    // Replay Contour Animation Button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF10141C).copy(alpha = 0.76f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .testTag("retrace_contour_button")
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    animationReplayTrigger++
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Replay Outline",
                                tint = Color(0xFF5AC8FA),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Trace",
                                style = AppleTheme.typography.caption.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Toggle Original vs Isolated Cutout
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (showOriginalComparison) colors.accent
                                else Color(0xFF10141C).copy(alpha = 0.76f)
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .testTag("toggle_original_cutout_button")
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showOriginalComparison = !showOriginalComparison
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Compare Original",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showOriginalComparison) "Cutout" else "Original",
                                style = AppleTheme.typography.caption.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // 3. Interactive Selection Toolbar (shown in Manual Selection Mode before extraction)
        if (isManualSelectionMode && !isProcessing) {
            ManualSelectionToolbarCard(
                activeTool = activeTool,
                onSelectTool = { tool ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    activeTool = tool
                },
                brushSizeNormalized = brushSizeNormalized,
                onBrushSizeChange = { brushSizeNormalized = it },
                canUndo = effectiveMarkup.strokes.isNotEmpty(),
                hasPositiveSelection = effectiveMarkup.hasPositiveSelection,
                warningMessage = selectionHintWarning,
                onUndo = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    updateMarkup(effectiveMarkup.undoLast())
                },
                onClear = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    updateMarkup(effectiveMarkup.clearAll())
                },
                onExtractClothing = {
                    if (!effectiveMarkup.hasPositiveSelection) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectionHintWarning = "Paint over the clothing item with the Brush or draw a Circle around it first."
                    } else {
                        selectionHintWarning = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onExtractClothing?.invoke(effectiveMarkup)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        // 4. Multi-Garment Candidate Selector Pills (shown if > 1 garment matched after extraction)
        if (!isManualSelectionMode &&
            segmentationResult is GarmentSegmentationResult.Success &&
            segmentationResult.candidates.size > 1
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 58.dp, start = 12.dp, end = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                segmentationResult.candidates.forEachIndexed { index, candidate ->
                    val isSelected = index == selectedCandidateIndex
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFF2997FF)
                                else Color(0xFF10141C).copy(alpha = 0.78f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.White.copy(alpha = 0.75f)
                                else Color.White.copy(alpha = 0.22f),
                                shape = CircleShape
                            )
                            .testTag("garment_candidate_chip_$index")
                            .clickable {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectCandidate(index)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = candidate.label,
                            style = AppleTheme.typography.caption.copy(
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }
        }

        // 5. Error / Failure Recovery Banner (if segmentation failed or selection was ambiguous)
        if (!isManualSelectionMode && segmentationResult is GarmentSegmentationResult.Failure) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141820).copy(alpha = 0.94f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                    .padding(18.dp)
                    .testTag("segmentation_failure_banner"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFFF9F0A),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = segmentationResult.message,
                    style = AppleTheme.typography.caption.copy(
                        color = Color.White,
                        fontSize = 12.5.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onRequestEditSelection != null) {
                        AppleButton(
                            text = "Adjust Selection",
                            onClick = onRequestEditSelection,
                            isPrimary = true,
                            modifier = Modifier.height(36.dp),
                            testTag = "adjust_selection_button"
                        )
                    }
                    AppleButton(
                        text = "Retry",
                        onClick = onRetrySegmentation,
                        isPrimary = onRequestEditSelection == null,
                        modifier = Modifier.height(36.dp),
                        testTag = "retry_garment_segmentation_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualSelectionToolbarCard(
    activeTool: SelectionToolMode,
    onSelectTool: (SelectionToolMode) -> Unit,
    brushSizeNormalized: Float,
    onBrushSizeChange: (Float) -> Unit,
    canUndo: Boolean,
    hasPositiveSelection: Boolean,
    warningMessage: String?,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onExtractClothing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF11151E).copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("manual_selection_toolbar"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (warningMessage != null) {
            Text(
                text = warningMessage,
                style = AppleTheme.typography.caption.copy(
                    color = Color(0xFFFF9F0A),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("selection_warning_text")
            )
        }

        // Row 1: Tool Mode Selector (Brush, Circle/Freehand, Eraser, Zoom) + Undo & Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionToolButton(
                label = "Brush",
                icon = Icons.Default.Brush,
                selected = activeTool == SelectionToolMode.BRUSH,
                onClick = { onSelectTool(SelectionToolMode.BRUSH) },
                testTag = "selection_tool_brush"
            )
            SelectionToolButton(
                label = "Circle",
                icon = Icons.Default.RadioButtonUnchecked,
                selected = activeTool == SelectionToolMode.CIRCLE_FREEHAND,
                onClick = { onSelectTool(SelectionToolMode.CIRCLE_FREEHAND) },
                testTag = "selection_tool_circle"
            )
            SelectionToolButton(
                label = "Eraser",
                icon = Icons.Default.CleaningServices,
                selected = activeTool == SelectionToolMode.ERASER,
                onClick = { onSelectTool(SelectionToolMode.ERASER) },
                testTag = "selection_tool_eraser"
            )
            SelectionToolButton(
                label = "Zoom",
                icon = Icons.Default.PanTool,
                selected = activeTool == SelectionToolMode.PAN_ZOOM,
                onClick = { onSelectTool(SelectionToolMode.PAN_ZOOM) },
                testTag = "selection_tool_pan_zoom"
            )

            // Undo action
            SelectionActionButton(
                label = "Undo",
                icon = Icons.AutoMirrored.Filled.Undo,
                enabled = canUndo,
                onClick = onUndo,
                testTag = "selection_action_undo"
            )

            // Clear action
            SelectionActionButton(
                label = "Clear",
                icon = Icons.Default.DeleteSweep,
                enabled = canUndo,
                onClick = onClear,
                testTag = "selection_action_clear"
            )
        }

        // Row 2: Brush Size Slider + Extract Clothing CTA Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Brush Size controls
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Size",
                    style = AppleTheme.typography.caption.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Slider(
                    value = brushSizeNormalized,
                    onValueChange = { onBrushSizeChange(it.coerceIn(0.025f, 0.14f)) },
                    valueRange = 0.025f..0.14f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF5AC8FA),
                        activeTrackColor = Color(0xFF2997FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .testTag("selection_brush_size_slider")
                )
            }

            // Primary Confirm Button: Extract Clothing
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasPositiveSelection) {
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2997FF), Color(0xFF5AC8FA))
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF2997FF).copy(alpha = 0.45f),
                                    Color(0xFF5AC8FA).copy(alpha = 0.45f)
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = if (hasPositiveSelection) 0.55f else 0.22f),
                        shape = CircleShape
                    )
                    .testTag("extract_clothing_button")
                    .clickable { onExtractClothing() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Extract Clothing",
                        style = AppleTheme.typography.caption.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(
                if (selected) Color(0xFF2997FF)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.65f)
                else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .testTag(testTag)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = AppleTheme.typography.caption.copy(
                    color = Color.White,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun SelectionActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val contentAlpha = if (enabled) 0.92f else 0.38f
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .testTag(testTag)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = contentAlpha),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = AppleTheme.typography.caption.copy(
                    color = Color.White.copy(alpha = contentAlpha),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            )
        }
    }
}

private fun buildOpenStrokePath(
    points: List<NormalizedPoint>,
    left: Float,
    top: Float,
    width: Float,
    height: Float
): Path {
    val path = Path()
    if (points.isEmpty()) return path
    fun mapX(nx: Float) = left + nx * width
    fun mapY(ny: Float) = top + ny * height

    val first = points.first()
    path.moveTo(mapX(first.x), mapY(first.y))
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val cx = mapX(prev.x)
        val cy = mapY(prev.y)
        val midX = (cx + mapX(curr.x)) * 0.5f
        val midY = (cy + mapY(curr.y)) * 0.5f
        path.quadraticTo(cx, cy, midX, midY)
    }
    val last = points.last()
    path.lineTo(mapX(last.x), mapY(last.y))
    return path
}

private fun buildClosedLoopPath(
    points: List<NormalizedPoint>,
    left: Float,
    top: Float,
    width: Float,
    height: Float
): Path {
    val path = buildOpenStrokePath(points, left, top, width, height)
    if (points.size >= 3) {
        path.close()
    }
    return path
}

/**
 * Converts normalized [0..1] contour points into a closed, smooth cubic/quadratic Compose Path
 * aligned with the displayed image bounds (`left`, `top`, `width`, `height`).
 */
private fun buildAlignedContourPath(
    points: List<NormalizedPoint>,
    left: Float,
    top: Float,
    width: Float,
    height: Float
): Path {
    val path = Path()
    if (points.isEmpty()) return path

    fun mapX(nx: Float) = left + nx * width
    fun mapY(ny: Float) = top + ny * height

    val first = points.first()
    val last = points.last()
    val startMidX = (mapX(last.x) + mapX(first.x)) * 0.5f
    val startMidY = (mapY(last.y) + mapY(first.y)) * 0.5f
    path.moveTo(startMidX, startMidY)

    val n = points.size
    for (i in 0 until n) {
        val curr = points[i]
        val next = points[(i + 1) % n]
        val cx = mapX(curr.x)
        val cy = mapY(curr.y)
        val midX = (cx + mapX(next.x)) * 0.5f
        val midY = (cy + mapY(next.y)) * 0.5f
        path.quadraticTo(cx, cy, midX, midY)
    }
    path.close()
    return path
}

/**
 * Draws a clean, subtle transparency checkerboard and studio vignette behind the isolated garment
 * so the user can visually confirm that the background is genuinely transparent.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTransparencyBackdrop(
    canvasW: Float,
    canvasH: Float,
    revealFraction: Float,
    darkMode: Boolean
) {
    val alpha = revealFraction.coerceIn(0f, 1f)
    val baseColor = if (darkMode) Color(0xFF12161F) else Color(0xFFF4F5F8)
    val cellAltColor = if (darkMode) Color(0xFF1A202C) else Color(0xFFE8EBF0)

    drawRect(
        color = baseColor.copy(alpha = alpha),
        topLeft = Offset.Zero,
        size = Size(canvasW, canvasH)
    )

    val cellSize = 14.dp.toPx().coerceAtLeast(8f)
    val cols = (canvasW / cellSize).toInt() + 1
    val rows = (canvasH / cellSize).toInt() + 1

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if ((r + c) % 2 == 1) {
                val x = c * cellSize
                val y = r * cellSize
                val w = min(cellSize, canvasW - x)
                val h = min(cellSize, canvasH - y)
                if (w > 0f && h > 0f) {
                    drawRect(
                        color = cellAltColor.copy(alpha = alpha * 0.85f),
                        topLeft = Offset(x, y),
                        size = Size(w, h)
                    )
                }
            }
        }
    }
}
