package com.example.wardrobe.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-Device Computer Vision Garment Segmentation, Silhouette Contour Tracer & Transparent PNG Exporter.
 *
 * Designed specifically for Wardrobe AI:
 * 1. Normalizes EXIF orientation and decodes bitmaps safely.
 * 2. Segments clothing items from plain, studio, flat-lay, hanger, or worn-on-person backgrounds
 *    using CIE LAB perceptual color modeling, illumination-invariant chromaticity distance,
 *    Sobel edge-gradient geodesic barriers, skin/head & hanger-hook exclusion, and 8-connected
 *    component extraction.
 * 3. Supports multiple garment candidates in a single image, ranking the primary garment first
 *    while allowing instant switching to secondary detected garments.
 * 4. Extracts the exact outer garment silhouette using Moore-Neighbor 8-connected contour tracing
 *    followed by Ramer-Douglas-Peucker simplification and Chaikin spline smoothing.
 * 5. Generates a genuine 32-bit ARGB_8888 transparent cutout and exports lossless PNG files.
 */
object GarmentSegmentationEngine {

    private const val WORKING_MAX_DIM = 240
    private const val OUTPUT_MAX_DIM = 1280
    private const val MIN_GARMENT_AREA_RATIO = 0.018f

    /**
     * Loads a Bitmap from a Uri or File, respecting EXIF rotation/flip metadata and capping maximum
     * resolution so memory usage remains safe on all Android devices.
     */
    fun loadOrientedBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = OUTPUT_MAX_DIM
    ): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            var sampleSize = 1
            while (
                boundsOptions.outWidth / sampleSize > maxDimension * 2 ||
                boundsOptions.outHeight / sampleSize > maxDimension * 2
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val exifOrientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val oriented = applyExifOrientation(rawBitmap, exifOrientation)
            scaleDownIfNeeded(oriented, maxDimension)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Runs the full automatic garment detection, mask generation, contour tracing, and transparent
     * cutout pipeline on a background coroutine dispatcher.
     */
    suspend fun segmentGarment(
        sourceBitmap: Bitmap
    ): GarmentSegmentationResult = withContext(Dispatchers.Default) {
        segmentGarmentSync(sourceBitmap)
    }

    /**
     * Synchronous entry point (useful for unit / Robolectric tests and direct worker calls).
     */
    fun segmentGarmentSync(sourceBitmap: Bitmap): GarmentSegmentationResult {
        val startTime = System.currentTimeMillis()
        if (sourceBitmap.width < 16 || sourceBitmap.height < 16) {
            return GarmentSegmentationResult.Failure(
                message = "Image is too small to detect a clothing item.",
                fallbackBitmap = sourceBitmap
            )
        }

        val argbSource = ensureArgb8888(scaleDownIfNeeded(sourceBitmap, OUTPUT_MAX_DIM))
        val srcW = argbSource.width
        val srcH = argbSource.height

        // Scale down to working grid for fast multi-cue segmentation
        val scale = min(
            1f,
            WORKING_MAX_DIM.toFloat() / max(srcW, srcH).toFloat()
        )
        val workW = (srcW * scale).roundToInt().coerceAtLeast(32)
        val workH = (srcH * scale).roundToInt().coerceAtLeast(32)

        val totalWorkPixels = workW * workH
        val pixels = sampleBitmapToWorkingGrid(argbSource, workW, workH)

        // 1. Convert working grid to CIE LAB + YCbCr skin likelihood
        val lArr = FloatArray(totalWorkPixels)
        val aArr = FloatArray(totalWorkPixels)
        val bArr = FloatArray(totalWorkPixels)
        val alphaArr = IntArray(totalWorkPixels)
        val skinScore = FloatArray(totalWorkPixels)

        var transparentBorderCount = 0
        var borderTotalCount = 0

        for (y in 0 until workH) {
            val isBorderY = y < 3 || y >= workH - 3
            for (x in 0 until workW) {
                val idx = y * workW + x
                val c = pixels[idx]
                val al = c ushr 24
                alphaArr[idx] = al

                if (isBorderY || x < 3 || x >= workW - 3) {
                    borderTotalCount++
                    if (al < 40) transparentBorderCount++
                }

                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val lab = rgbToLab(r, g, b)
                lArr[idx] = lab[0]
                aArr[idx] = lab[1]
                bArr[idx] = lab[2]

                skinScore[idx] = computeSkinLikelihood(r, g, b)
            }
        }

        // Fast-path: if the input image ALREADY has a transparent background (e.g. re-imported PNG)
        val alreadyTransparent = borderTotalCount > 0 &&
            transparentBorderCount.toFloat() / borderTotalCount.toFloat() > 0.65f

        val rawForegroundMask = if (alreadyTransparent) {
            BooleanArray(totalWorkPixels) { idx -> alphaArr[idx] >= 64 }
        } else {
            computeMultiCueForegroundMask(
                workW = workW,
                workH = workH,
                lArr = lArr,
                aArr = aArr,
                bArr = bArr,
                skinScore = skinScore
            )
        }

        // Clean mask: remove hanger hook at top-center, bridge 1px micro-seams, and fill interior holes
        val cleanedMask = refineGarmentMask(
            rawMask = rawForegroundMask,
            workW = workW,
            workH = workH,
            lArr = lArr,
            aArr = aArr,
            bArr = bArr,
            skinScore = skinScore
        )

        // Extract 8-connected components as individual garment candidates
        val components = extractCandidateComponents(cleanedMask, workW, workH)

        if (components.isEmpty()) {
            return GarmentSegmentationResult.Failure(
                message = "No distinct clothing silhouette could be separated from the background. Try placing the garment against a contrasting surface.",
                fallbackBitmap = argbSource
            )
        }

        val candidates = components.mapIndexed { index, comp ->
            val filledComponentMask = fillInteriorHoles(comp.mask, workW, workH)
            val contour = traceAndSmoothContour(filledComponentMask, workW, workH)
            val softAlpha = buildFeatheredAlphaMask(filledComponentMask, workW, workH)
            val highResCutout = createHighResCutout(
                sourceBitmap = argbSource,
                softAlphaMask = softAlpha,
                maskW = workW,
                maskH = workH
            )
            val label = if (index == 0) "Primary Garment" else "Garment #${index + 1}"

            GarmentCandidate(
                id = index,
                label = label,
                prominenceScore = comp.prominenceScore,
                areaFraction = comp.areaFraction,
                boundingBox = comp.boundingBox,
                contourPoints = contour,
                maskWidth = workW,
                maskHeight = workH,
                alphaMask = softAlpha,
                cutoutBitmap = highResCutout
            )
        }

        val elapsed = System.currentTimeMillis() - startTime
        return GarmentSegmentationResult.Success(
            sourceBitmap = argbSource,
            candidates = candidates,
            defaultCandidateIndex = 0,
            processingTimeMs = elapsed
        )
    }

    /**
     * Runs user-guided garment segmentation on a background coroutine dispatcher using the user's
     * manual brush, circle/freehand, and eraser strokes as selection seed prompts.
     */
    suspend fun segmentGarmentWithUserMarkup(
        sourceBitmap: Bitmap,
        markup: UserSelectionMarkup
    ): GarmentSegmentationResult = withContext(Dispatchers.Default) {
        segmentGarmentWithUserMarkupSync(sourceBitmap, markup)
    }

    /**
     * Synchronous entry point for user-guided garment segmentation:
     * - Does NOT cut out the user's raw brush/circle shape directly.
     * - Instead, rasterizes the user's Brush, Circle/Freehand, and Eraser strokes into spatial &
     *   color seed priors, models the garment vs. background in CIE LAB + Sobel edge space,
     *   grows/snaps the selection to the actual garment silhouette, excludes unselected garments
     *   and erased regions, and traces the smooth boundary contour of the segmented garment mask.
     */
    fun segmentGarmentWithUserMarkupSync(
        sourceBitmap: Bitmap,
        markup: UserSelectionMarkup
    ): GarmentSegmentationResult {
        val startTime = System.currentTimeMillis()
        if (sourceBitmap.width < 16 || sourceBitmap.height < 16) {
            return GarmentSegmentationResult.Failure(
                message = "Image is too small to extract a clothing item.",
                fallbackBitmap = sourceBitmap
            )
        }

        val argbSource = ensureArgb8888(scaleDownIfNeeded(sourceBitmap, OUTPUT_MAX_DIM))
        if (!markup.hasPositiveSelection) {
            return GarmentSegmentationResult.Failure(
                message = "Please brush over or draw a circle around the clothing item you want to extract first.",
                fallbackBitmap = argbSource
            )
        }

        val srcW = argbSource.width
        val srcH = argbSource.height

        val scale = min(
            1f,
            WORKING_MAX_DIM.toFloat() / max(srcW, srcH).toFloat()
        )
        val workW = (srcW * scale).roundToInt().coerceAtLeast(32)
        val workH = (srcH * scale).roundToInt().coerceAtLeast(32)
        val totalWorkPixels = workW * workH

        // 1. Rasterize user Brush, Circle/Freehand, and Eraser strokes in chronological order
        val rasterized = rasterizeUserStrokesInternal(markup, workW, workH)
        val inclusionCount = rasterized.inclusionMask.count { it }
        if (inclusionCount < 6) {
            return GarmentSegmentationResult.Failure(
                message = "Your selection area is empty or was erased. Please mark the clothing item with the Brush or Circle tool.",
                fallbackBitmap = argbSource
            )
        }

        val pixels = sampleBitmapToWorkingGrid(argbSource, workW, workH)
        val lArr = FloatArray(totalWorkPixels)
        val aArr = FloatArray(totalWorkPixels)
        val bArr = FloatArray(totalWorkPixels)
        val alphaArr = IntArray(totalWorkPixels)
        val skinScore = FloatArray(totalWorkPixels)

        for (y in 0 until workH) {
            for (x in 0 until workW) {
                val idx = y * workW + x
                val c = pixels[idx]
                alphaArr[idx] = c ushr 24
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val lab = rgbToLab(r, g, b)
                lArr[idx] = lab[0]
                aArr[idx] = lab[1]
                bArr[idx] = lab[2]
                skinScore[idx] = computeSkinLikelihood(r, g, b)
            }
        }

        // 2. Compute guided foreground mask from user's seed region + multi-cue color & edge barriers
        val guidedMask = computeUserGuidedForegroundMask(
            workW = workW,
            workH = workH,
            lArr = lArr,
            aArr = aArr,
            bArr = bArr,
            alphaArr = alphaArr,
            skinScore = skinScore,
            raster = rasterized
        )

        val guidedCount = guidedMask.count { it }
        if (guidedCount < max(16, (totalWorkPixels * 0.008f).toInt())) {
            return GarmentSegmentationResult.Failure(
                message = "Could not detect a clear clothing boundary inside your selection. Try brushing directly over the garment or drawing a closer circle around it.",
                fallbackBitmap = argbSource
            )
        }

        // 3. Refine mask: close 1px seams, fill interior holes (while respecting Eraser exclusions), trim hanger hook
        val refinedMask = refineGarmentMask(
            rawMask = guidedMask,
            workW = workW,
            workH = workH,
            lArr = lArr,
            aArr = aArr,
            bArr = bArr,
            skinScore = skinScore
        )
        for (i in 0 until totalWorkPixels) {
            if (rasterized.exclusionMask[i]) {
                refinedMask[i] = false
            }
        }

        // 4. Extract connected components and keep ONLY components selected by the user's marks
        val allComponents = extractUserSelectedComponents(
            mask = refinedMask,
            inclusionMask = rasterized.inclusionMask,
            coreSeedMask = rasterized.coreSeedMask,
            workW = workW,
            workH = workH
        )

        if (allComponents.isEmpty()) {
            return GarmentSegmentationResult.Failure(
                message = "No clothing item matched your marked region. Please adjust your Brush or Circle selection over the desired garment.",
                fallbackBitmap = argbSource
            )
        }

        val candidates = allComponents.mapIndexed { index, comp ->
            val filledMask = fillInteriorHoles(comp.mask, workW, workH)
            for (i in 0 until totalWorkPixels) {
                if (rasterized.exclusionMask[i]) {
                    filledMask[i] = false
                }
            }
            // Contour is ALWAYS traced from the final segmented garment mask (never from the user's rough circle/brush)
            val contour = traceAndSmoothContour(filledMask, workW, workH)
            val softAlpha = buildFeatheredAlphaMask(filledMask, workW, workH)
            val highResCutout = createHighResCutout(
                sourceBitmap = argbSource,
                softAlphaMask = softAlpha,
                maskW = workW,
                maskH = workH
            )
            val label = if (index == 0) "Selected Garment" else "Garment #${index + 1}"

            GarmentCandidate(
                id = index,
                label = label,
                prominenceScore = comp.prominenceScore,
                areaFraction = comp.areaFraction,
                boundingBox = comp.boundingBox,
                contourPoints = contour,
                maskWidth = workW,
                maskHeight = workH,
                alphaMask = softAlpha,
                cutoutBitmap = highResCutout
            )
        }

        val elapsed = System.currentTimeMillis() - startTime
        return GarmentSegmentationResult.Success(
            sourceBitmap = argbSource,
            candidates = candidates,
            defaultCandidateIndex = 0,
            processingTimeMs = elapsed
        )
    }

    /**
     * Public helper that rasterizes a [UserSelectionMarkup] onto a target grid of [width] x [height],
     * returning the net positive selection mask after applying Brush, Circle/Freehand, and Eraser strokes.
     */
    fun rasterizeUserMarkup(
        markup: UserSelectionMarkup,
        width: Int,
        height: Int
    ): BooleanArray {
        return rasterizeUserStrokesInternal(markup, width, height).inclusionMask
    }

    private class RasterizedUserMarkup(
        val inclusionMask: BooleanArray,
        val directBrushMask: BooleanArray,
        val circleHullMask: BooleanArray,
        val coreSeedMask: BooleanArray,
        val exclusionMask: BooleanArray,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int
    )

    private fun rasterizeUserStrokesInternal(
        markup: UserSelectionMarkup,
        width: Int,
        height: Int
    ): RasterizedUserMarkup {
        val total = width * height
        val inclusionMask = BooleanArray(total)
        val directBrushMask = BooleanArray(total)
        val circleHullMask = BooleanArray(total)
        val coreSeedMask = BooleanArray(total)
        val exclusionMask = BooleanArray(total)
        val minDim = min(width, height).toFloat()

        for (stroke in markup.strokes) {
            if (stroke.points.isEmpty()) continue
            val radiusPx = (stroke.strokeWidthNormalized * minDim * 0.5f).coerceIn(2f, minDim * 0.25f)

            when (stroke.tool) {
                SelectionToolMode.BRUSH -> {
                    val strokeMask = BooleanArray(total)
                    rasterizePolyline(stroke.points, width, height, radiusPx, strokeMask)
                    for (i in 0 until total) {
                        if (strokeMask[i]) {
                            inclusionMask[i] = true
                            directBrushMask[i] = true
                            coreSeedMask[i] = true
                            exclusionMask[i] = false
                        }
                    }
                }

                SelectionToolMode.CIRCLE_FREEHAND -> {
                    val polyMask = BooleanArray(total)
                    if (stroke.points.size >= 3) {
                        fillClosedPolygon(stroke.points, width, height, polyMask)
                    }
                    // Also include the drawn perimeter ribbon
                    rasterizePolyline(
                        points = if (stroke.points.size >= 3) stroke.points + stroke.points.first() else stroke.points,
                        width = width,
                        height = height,
                        radiusPx = max(2f, radiusPx * 0.65f),
                        targetMask = polyMask
                    )
                    // Compute inner core of the circle/lasso loop for high-confidence foreground seeding
                    val erodeRadius = max(2, (minDim * 0.045f).roundToInt())
                    val innerCore = morphErode(polyMask, width, height, radius = erodeRadius)
                    val hasInnerCore = innerCore.any { it }

                    for (i in 0 until total) {
                        if (polyMask[i]) {
                            inclusionMask[i] = true
                            circleHullMask[i] = true
                            if (hasInnerCore && innerCore[i]) {
                                coreSeedMask[i] = true
                            } else if (!hasInnerCore) {
                                coreSeedMask[i] = true
                            }
                            exclusionMask[i] = false
                        }
                    }
                }

                SelectionToolMode.ERASER -> {
                    val eraseMask = BooleanArray(total)
                    rasterizePolyline(stroke.points, width, height, max(2.5f, radiusPx * 1.1f), eraseMask)
                    for (i in 0 until total) {
                        if (eraseMask[i]) {
                            inclusionMask[i] = false
                            directBrushMask[i] = false
                            circleHullMask[i] = false
                            coreSeedMask[i] = false
                            exclusionMask[i] = true
                        }
                    }
                }

                SelectionToolMode.PAN_ZOOM -> {
                    // Navigation-only gesture; does not modify masks
                }
            }
        }

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (inclusionMask[y * width + x]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (minX > maxX) {
            minX = 0
            maxX = width - 1
            minY = 0
            maxY = height - 1
        }

        return RasterizedUserMarkup(
            inclusionMask = inclusionMask,
            directBrushMask = directBrushMask,
            circleHullMask = circleHullMask,
            coreSeedMask = coreSeedMask,
            exclusionMask = exclusionMask,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY
        )
    }

    private fun rasterizePolyline(
        points: List<NormalizedPoint>,
        width: Int,
        height: Int,
        radiusPx: Float,
        targetMask: BooleanArray
    ) {
        if (points.isEmpty()) return
        val rSq = radiusPx * radiusPx
        val rCeil = (radiusPx + 1f).toInt()

        fun stampDisc(cx: Float, cy: Float) {
            val x0 = (cx - rCeil).toInt().coerceIn(0, width - 1)
            val x1 = (cx + rCeil).toInt().coerceIn(0, width - 1)
            val y0 = (cy - rCeil).toInt().coerceIn(0, height - 1)
            val y1 = (cy + rCeil).toInt().coerceIn(0, height - 1)
            for (y in y0..y1) {
                val dy = (y + 0.5f) - cy
                val row = y * width
                for (x in x0..x1) {
                    val dx = (x + 0.5f) - cx
                    if (dx * dx + dy * dy <= rSq) {
                        targetMask[row + x] = true
                    }
                }
            }
        }

        val firstX = points.first().x.coerceIn(0f, 1f) * (width - 1)
        val firstY = points.first().y.coerceIn(0f, 1f) * (height - 1)
        stampDisc(firstX, firstY)

        for (i in 0 until points.lastIndex) {
            val ax = points[i].x.coerceIn(0f, 1f) * (width - 1)
            val ay = points[i].y.coerceIn(0f, 1f) * (height - 1)
            val bx = points[i + 1].x.coerceIn(0f, 1f) * (width - 1)
            val by = points[i + 1].y.coerceIn(0f, 1f) * (height - 1)
            val dist = hypot(bx - ax, by - ay)
            val steps = max(1, (dist / max(1f, radiusPx * 0.4f)).roundToInt())
            for (s in 1..steps) {
                val t = s.toFloat() / steps.toFloat()
                stampDisc(ax + (bx - ax) * t, ay + (by - ay) * t)
            }
        }
    }

    private fun fillClosedPolygon(
        points: List<NormalizedPoint>,
        width: Int,
        height: Int,
        targetMask: BooleanArray
    ) {
        if (points.size < 3) return
        val pxPts = FloatArray(points.size) { points[it].x.coerceIn(0f, 1f) * (width - 1) }
        val pyPts = FloatArray(points.size) { points[it].y.coerceIn(0f, 1f) * (height - 1) }

        var minY = height - 1
        var maxY = 0
        var minX = width - 1
        var maxX = 0
        for (i in points.indices) {
            minX = min(minX, pxPts[i].toInt().coerceIn(0, width - 1))
            maxX = max(maxX, (pxPts[i].toInt() + 1).coerceIn(0, width - 1))
            minY = min(minY, pyPts[i].toInt().coerceIn(0, height - 1))
            maxY = max(maxY, (pyPts[i].toInt() + 1).coerceIn(0, height - 1))
        }

        val n = points.size
        for (y in minY..maxY) {
            val py = y + 0.5f
            val row = y * width
            for (x in minX..maxX) {
                val px = x + 0.5f
                var inside = false
                var j = n - 1
                for (i in 0 until n) {
                    val xi = pxPts[i]
                    val yi = pyPts[i]
                    val xj = pxPts[j]
                    val yj = pyPts[j]
                    val intersect = ((yi > py) != (yj > py)) &&
                        (px < (xj - xi) * (py - yi) / ((yj - yi) + 1e-6f) + xi)
                    if (intersect) inside = !inside
                    j = i
                }
                if (inside) {
                    targetMask[row + x] = true
                }
            }
        }
    }

    /**
     * Combines the user's manual selection hints with multi-cue LAB color clustering,
     * Sobel edge barriers, and geodesic region growing to isolate the exact garment silhouette.
     */
    private fun computeUserGuidedForegroundMask(
        workW: Int,
        workH: Int,
        lArr: FloatArray,
        aArr: FloatArray,
        bArr: FloatArray,
        alphaArr: IntArray,
        skinScore: FloatArray,
        raster: RasterizedUserMarkup
    ): BooleanArray {
        val total = workW * workH

        // Fast-path if input already has a transparent background
        var borderCount = 0
        var transBorderCount = 0
        for (y in 0 until workH) {
            if (y < 3 || y >= workH - 3) {
                for (x in 0 until workW) {
                    borderCount++
                    if (alphaArr[y * workW + x] < 40) transBorderCount++
                }
            }
        }
        if (borderCount > 0 && transBorderCount.toFloat() / borderCount.toFloat() > 0.65f) {
            return BooleanArray(total) { idx ->
                alphaArr[idx] >= 64 && !raster.exclusionMask[idx]
            }
        }

        // 1. Run base multi-cue segmentation to identify natural garment vs. background boundaries
        val baseMultiCueMask = computeMultiCueForegroundMask(
            workW = workW,
            workH = workH,
            lArr = lArr,
            aArr = aArr,
            bArr = bArr,
            skinScore = skinScore
        )

        // 2. Sample background colors from image borders (outside user inclusion) + explicit Eraser pixels
        val bgIndices = ArrayList<Int>(workW * 4 + workH * 4)
        val margin = max(2, min(workW, workH) / 26)
        for (y in 0 until workH) {
            for (x in 0 until workW) {
                val idx = y * workW + x
                val isBorder = y < margin || y >= workH - margin || x < margin || x >= workW - margin
                if ((isBorder && !raster.inclusionMask[idx]) || raster.exclusionMask[idx]) {
                    bgIndices.add(idx)
                }
            }
        }
        val bgClusters = clusterBorderColors(bgIndices, lArr, aArr, bArr, k = 5)

        // 3. Compute Sobel edge magnitude on (L, a, b)
        val gradMag = FloatArray(total)
        var maxGrad = 1f
        for (y in 1 until workH - 1) {
            for (x in 1 until workW - 1) {
                val idx = y * workW + x
                val gxL = (lArr[idx + 1] - lArr[idx - 1]) * 0.8f
                val gyL = (lArr[idx + workW] - lArr[idx - workW]) * 0.8f
                val gxC = hypot(aArr[idx + 1] - aArr[idx - 1], bArr[idx + 1] - bArr[idx - 1]) * 1.35f
                val gyC = hypot(aArr[idx + workW] - aArr[idx - workW], bArr[idx + workW] - bArr[idx - workW]) * 1.35f
                val mag = sqrt(gxL * gxL + gyL * gyL + gxC * gxC + gyC * gyC)
                gradMag[idx] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }
        val edgeBarrierThreshold = max(5.8f, maxGrad * 0.15f)

        // 4. Compute distance of each pixel to background clusters
        val bgDist = FloatArray(total)
        for (i in 0 until total) {
            var minDist = Float.MAX_VALUE
            for (cl in bgClusters) {
                val dL = (lArr[i] - cl[0]) * 0.72f
                val da = (aArr[i] - cl[1]) * 1.25f
                val db = (bArr[i] - cl[2]) * 1.25f
                val d = sqrt(dL * dL + da * da + db * db)
                if (d < minDist) minDist = d
            }
            bgDist[i] = minDist
        }

        // 5. Identify true garment seed pixels inside the user's markings:
        //    - If the user circled a garment, the circle includes both the garment and some surrounding
        //      background. We filter out background pixels inside the circle so only actual garment pixels
        //      become seeds!
        //    - If the user painted directly with the Brush, brushed pixels that don't match erased/border
        //      background are strong garment seeds.
        val garmentSeedIndices = ArrayList<Int>()
        for (i in 0 until total) {
            if (raster.exclusionMask[i]) continue
            if (raster.inclusionMask[i]) {
                val isBaseFg = baseMultiCueMask[i]
                val isDistinctFromBg = bgDist[i] >= 5.2f
                val isDirectBrush = raster.directBrushMask[i] && bgDist[i] >= 2.8f
                if (isBaseFg || isDistinctFromBg || isDirectBrush) {
                    garmentSeedIndices.add(i)
                }
            }
        }

        // If the garment was very low-contrast against the background (e.g., cream shirt on white wall)
        // and baseMultiCueMask missed it, use coreSeedMask pixels that have any tonal/edge separation
        if (garmentSeedIndices.size < 8) {
            for (i in 0 until total) {
                if (!raster.exclusionMask[i] && raster.coreSeedMask[i] && (bgDist[i] >= 2.0f || gradMag[i] >= 3.5f)) {
                    garmentSeedIndices.add(i)
                }
            }
        }

        if (garmentSeedIndices.size < 6) {
            return BooleanArray(total)
        }

        // 6. Cluster the foreground garment colors from the user's garment seeds
        val fgClusters = clusterBorderColors(garmentSeedIndices, lArr, aArr, bArr, k = 4)

        // Determine spatial bounds constraint:
        // - If the user drew a closed Circle/Freehand loop (without brushing outside it), the garment
        //   is expected to reside inside or within a small margin (12% of frame) of the drawn loop,
        //   which cleanly prevents nearby unselected objects or background clutter from being merged.
        // - If the user used the Brush tool (e.g., a quick stroke across a shirt chest), allow growth
        //   across the entire connected garment silhouette up to its Sobel edges.
        val hasBrushStrokes = raster.directBrushMask.any { it }
        val hasCircleLoop = raster.circleHullMask.any { it }
        val expandMarginX = if (hasCircleLoop && !hasBrushStrokes) {
            max(8, (workW * 0.12f).roundToInt())
        } else {
            workW
        }
        val expandMarginY = if (hasCircleLoop && !hasBrushStrokes) {
            max(8, (workH * 0.12f).roundToInt())
        } else {
            workH
        }
        val allowedMinX = (raster.minX - expandMarginX).coerceAtLeast(0)
        val allowedMaxX = (raster.maxX + expandMarginX).coerceAtMost(workW - 1)
        val allowedMinY = (raster.minY - expandMarginY).coerceAtLeast(0)
        val allowedMaxY = (raster.maxY + expandMarginY).coerceAtMost(workH - 1)

        // 7. Edge-Aware Geodesic Seeded Region Growing from garmentSeedIndices
        val grownMask = BooleanArray(total)
        val queue = IntArray(total)
        var qH = 0
        var qT = 0

        for (seedIdx in garmentSeedIndices) {
            if (!grownMask[seedIdx] && !raster.exclusionMask[seedIdx]) {
                grownMask[seedIdx] = true
                queue[qT++] = seedIdx
            }
        }

        val dx8 = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val dy8 = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        while (qH < qT) {
            val curr = queue[qH++]
            val cx = curr % workW
            val cy = curr / workW
            val currL = lArr[curr]
            val currA = aArr[curr]
            val currB = bArr[curr]

            for (d in 0..7) {
                val nx = cx + dx8[d]
                val ny = cy + dy8[d]
                if (nx !in allowedMinX..allowedMaxX || ny !in allowedMinY..allowedMaxY) continue
                val nIdx = ny * workW + nx
                if (grownMask[nIdx] || raster.exclusionMask[nIdx]) continue

                // Distance to closest user-seeded garment color cluster
                var fgDist = Float.MAX_VALUE
                for (cl in fgClusters) {
                    val dL = (lArr[nIdx] - cl[0]) * 0.72f
                    val da = (aArr[nIdx] - cl[1]) * 1.25f
                    val db = (bArr[nIdx] - cl[2]) * 1.25f
                    val dist = sqrt(dL * dL + da * da + db * db)
                    if (dist < fgDist) fgDist = dist
                }

                val stepDiff = abs(lArr[nIdx] - currL) * 0.65f +
                    hypot(aArr[nIdx] - currA, bArr[nIdx] - currB) * 1.2f

                val insideUserMarkup = raster.inclusionMask[nIdx]
                val notStrongEdge = gradMag[nIdx] < edgeBarrierThreshold

                // A neighbor pixel belongs to the selected garment if:
                // (a) It is inside the user's markup AND is part of baseMultiCueMask or closer to fgClusters than bgClusters, OR
                // (b) It is connected outside the initial brush stroke with no strong edge barrier AND matches both
                //     the seeded garment color (fgDist < 15.5) and is distinctly closer to foreground than background.
                val acceptInsideMarkup = insideUserMarkup &&
                    (baseMultiCueMask[nIdx] || (fgDist < bgDist[nIdx] * 1.15f && bgDist[nIdx] > 4.0f))

                val acceptConnectedGarment = !insideUserMarkup &&
                    notStrongEdge &&
                    stepDiff < 7.5f &&
                    (baseMultiCueMask[nIdx] || (fgDist < 14.5f && fgDist < bgDist[nIdx] * 0.88f))

                if (acceptInsideMarkup || acceptConnectedGarment) {
                    grownMask[nIdx] = true
                    queue[qT++] = nIdx
                }
            }
        }

        // Exclude person skin/head/neck if the garment is worn by a person
        excludePersonSkinRegions(grownMask, skinScore, lArr, aArr, bArr, workW, workH)

        return grownMask
    }

    /**
     * Filters 8-connected components so ONLY garments that overlap the user's manual selection
     * marks are returned, ranked by user selection overlap.
     */
    private fun extractUserSelectedComponents(
        mask: BooleanArray,
        inclusionMask: BooleanArray,
        coreSeedMask: BooleanArray,
        workW: Int,
        workH: Int
    ): List<InternalComponent> {
        val total = workW * workH
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        val minPixels = (total * 0.008f).toInt().coerceAtLeast(16)

        val selectedComponents = ArrayList<InternalComponent>()
        val dx8 = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val dy8 = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        for (startIdx in 0 until total) {
            if (!mask[startIdx] || visited[startIdx]) continue

            var qH = 0
            var qT = 0
            visited[startIdx] = true
            queue[qT++] = startIdx

            var minX = workW
            var maxX = 0
            var minY = workH
            var maxY = 0
            var inclusionOverlap = 0
            var coreOverlap = 0

            while (qH < qT) {
                val curr = queue[qH++]
                val cx = curr % workW
                val cy = curr / workW

                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                if (inclusionMask[curr]) inclusionOverlap++
                if (coreSeedMask[curr]) coreOverlap++

                for (d in 0..7) {
                    val nx = cx + dx8[d]
                    val ny = cy + dy8[d]
                    if (nx in 0 until workW && ny in 0 until workH) {
                        val nIdx = ny * workW + nx
                        if (mask[nIdx] && !visited[nIdx]) {
                            visited[nIdx] = true
                            queue[qT++] = nIdx
                        }
                    }
                }
            }

            val pixelCount = qT
            // Component MUST genuinely overlap the user's selection marks
            if (pixelCount >= minPixels && inclusionOverlap >= max(4, (pixelCount * 0.04f).toInt())) {
                val compMask = BooleanArray(total)
                for (i in 0 until pixelCount) {
                    compMask[queue[i]] = true
                }

                val areaFrac = pixelCount.toFloat() / total.toFloat()
                val overlapRatio = inclusionOverlap.toFloat() / pixelCount.toFloat()
                val coreRatio = coreOverlap.toFloat() / max(1, inclusionOverlap).toFloat()

                val bbox = GarmentBoundingBox(
                    left = (minX.toFloat() / workW.toFloat()).coerceIn(0f, 1f),
                    top = (minY.toFloat() / workH.toFloat()).coerceIn(0f, 1f),
                    right = ((maxX + 1).toFloat() / workW.toFloat()).coerceIn(0f, 1f),
                    bottom = ((maxY + 1).toFloat() / workH.toFloat()).coerceIn(0f, 1f)
                )

                // Rank primarily by how strongly the user marked this component
                val score = (inclusionOverlap.toFloat() / total.toFloat()) * 2.5f +
                    overlapRatio * 0.8f +
                    coreRatio * 0.5f +
                    areaFrac * 0.4f

                selectedComponents.add(
                    InternalComponent(
                        mask = compMask,
                        areaFraction = areaFrac,
                        prominenceScore = score,
                        boundingBox = bbox
                    )
                )
            }
        }

        selectedComponents.sortByDescending { it.prominenceScore }
        return selectedComponents.take(3)
    }

    /**
     * Exports a genuine 32-bit ARGB_8888 transparent PNG file into internal storage
     * (`context.filesDir/wardrobe_photos/garment_cutout_<timestamp>.png`).
     */
    fun exportTransparentPng(
        context: Context,
        cutoutBitmap: Bitmap,
        prefix: String = "garment_cutout"
    ): File {
        val photosDir = File(context.filesDir, "wardrobe_photos").apply {
            if (!exists()) mkdirs()
        }
        val outputFile = File(photosDir, "${prefix}_${System.currentTimeMillis()}.png")
        val pngBitmap = ensureArgb8888(cutoutBitmap).apply {
            setHasAlpha(true)
        }
        FileOutputStream(outputFile).use { out ->
            pngBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return outputFile
    }

    // =========================================================================
    // STAGE 1: MULTI-CUE FOREGROUND SEGMENTATION (LAB + GRADIENT + GEODESIC)
    // =========================================================================

    private fun computeMultiCueForegroundMask(
        workW: Int,
        workH: Int,
        lArr: FloatArray,
        aArr: FloatArray,
        bArr: FloatArray,
        skinScore: FloatArray
    ): BooleanArray {
        val total = workW * workH

        // 1. Sample border pixels to build background color clusters (up to 4 clusters)
        val borderIndices = ArrayList<Int>(workW * 4 + workH * 4)
        val margin = max(2, min(workW, workH) / 28)
        for (y in 0 until workH) {
            for (x in 0 until workW) {
                val onTopOrBottom = y < margin || y >= workH - margin
                val onLeftOrRight = x < margin || x >= workW - margin
                // Avoid bottom-center if a worn garment extends to bottom edge
                val isBottomCenter = y >= workH - margin && x in (workW / 4)..(workW * 3 / 4)
                if ((onTopOrBottom || onLeftOrRight) && !isBottomCenter) {
                    borderIndices.add(y * workW + x)
                }
            }
        }

        val bgClusters = clusterBorderColors(borderIndices, lArr, aArr, bArr, k = 4)

        // 2. Compute Sobel edge magnitude on (L, a, b) to capture low-contrast seams/edges
        val gradMag = FloatArray(total)
        var maxGrad = 1f
        for (y in 1 until workH - 1) {
            for (x in 1 until workW - 1) {
                val idx = y * workW + x
                val lLeft = lArr[idx - 1]
                val lRight = lArr[idx + 1]
                val lUp = lArr[idx - workW]
                val lDown = lArr[idx + workW]

                val aLeft = aArr[idx - 1]
                val aRight = aArr[idx + 1]
                val aUp = aArr[idx - workW]
                val aDown = aArr[idx + workW]

                val bLeft = bArr[idx - 1]
                val bRight = bArr[idx + 1]
                val bUp = bArr[idx - workW]
                val bDown = bArr[idx + workW]

                val gxL = (lRight - lLeft) * 0.8f
                val gyL = (lDown - lUp) * 0.8f
                val gxC = hypot(aRight - aLeft, bRight - bLeft) * 1.35f
                val gyC = hypot(aDown - aUp, bDown - bUp) * 1.35f

                val mag = sqrt(gxL * gxL + gyL * gyL + gxC * gxC + gyC * gyC)
                gradMag[idx] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        // 3. Compute perceptual color distance from each pixel to the closest background cluster
        val bgDist = FloatArray(total)
        val borderDistValues = FloatArray(borderIndices.size)

        for (i in 0 until total) {
            val l = lArr[i]
            val a = aArr[i]
            val b = bArr[i]
            var minDist = Float.MAX_VALUE
            for (cl in bgClusters) {
                // Weight chrominance (da, db) slightly higher than luminance (dL) so soft cast shadows
                // on the background floor/wall don't get misclassified as garment pixels.
                val dL = (l - cl[0]) * 0.72f
                val da = (a - cl[1]) * 1.25f
                val db = (b - cl[2]) * 1.25f
                val d = sqrt(dL * dL + da * da + db * db)
                if (d < minDist) minDist = d
            }
            bgDist[i] = minDist
        }

        for (k in borderIndices.indices) {
            borderDistValues[k] = bgDist[borderIndices[k]]
        }
        borderDistValues.sort()
        val borderP85 = if (borderDistValues.isNotEmpty()) {
            borderDistValues[(borderDistValues.size * 0.85f).toInt().coerceIn(0, borderDistValues.lastIndex)]
        } else {
            6f
        }

        // Adaptive color threshold: handles both high-contrast and subtle low-contrast backgrounds (Case 5)
        val adaptiveColorThreshold = (borderP85 * 1.65f).coerceIn(5.5f, 19.0f)
        val lowContrastThreshold = (borderP85 * 1.18f).coerceIn(3.8f, 11.5f)

        // 4. Edge-barrier flood propagation from outer border inward
        // Pixels reachable from the image perimeter with low color difference AND no strong edge barrier
        // are marked as definite background.
        val isBackground = BooleanArray(total)
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        var qHead = 0
        var qTail = 0

        // Edge barrier threshold: strong local gradient stops background flood fill from leaking
        // across a garment's hem, collar, or sleeve even when garment color is close to background color.
        val edgeBarrierThreshold = max(6.2f, maxGrad * 0.16f)

        fun trySeed(idx: Int) {
            if (!visited[idx] && bgDist[idx] <= adaptiveColorThreshold * 1.15f && gradMag[idx] < edgeBarrierThreshold * 1.35f) {
                visited[idx] = true
                isBackground[idx] = true
                queue[qTail++] = idx
            }
        }

        for (x in 0 until workW) {
            trySeed(x) // Top row
            trySeed((workH - 1) * workW + x) // Bottom row
        }
        for (y in 0 until workH) {
            trySeed(y * workW) // Left column
            trySeed(y * workW + (workW - 1)) // Right column
        }

        val dx4 = intArrayOf(1, -1, 0, 0)
        val dy4 = intArrayOf(0, 0, 1, -1)

        while (qHead < qTail) {
            val curr = queue[qHead++]
            val cx = curr % workW
            val cy = curr / workW
            val currL = lArr[curr]
            val currA = aArr[curr]
            val currB = bArr[curr]

            for (dir in 0..3) {
                val nx = cx + dx4[dir]
                val ny = cy + dy4[dir]
                if (nx in 0 until workW && ny in 0 until workH) {
                    val nIdx = ny * workW + nx
                    if (!visited[nIdx]) {
                        val stepDL = abs(lArr[nIdx] - currL) * 0.7f
                        val stepDC = hypot(aArr[nIdx] - currA, bArr[nIdx] - currB) * 1.25f
                        val stepDiff = stepDL + stepDC

                        val withinBgModel = bgDist[nIdx] < adaptiveColorThreshold
                        val smoothContinuation = bgDist[nIdx] < adaptiveColorThreshold * 1.35f && stepDiff < 4.2f
                        val noStrongEdge = gradMag[nIdx] < edgeBarrierThreshold

                        if ((withinBgModel || smoothContinuation) && noStrongEdge) {
                            visited[nIdx] = true
                            isBackground[nIdx] = true
                            queue[qTail++] = nIdx
                        }
                    }
                }
            }
        }

        // 5. Construct foreground mask from non-background pixels + edge-enclosed regions
        val fgMask = BooleanArray(total)
        val minFgDelta = min(lowContrastThreshold, 2.2f)
        for (y in 0 until workH) {
            for (x in 0 until workW) {
                val idx = y * workW + x
                if (!isBackground[idx]) {
                    if (bgDist[idx] >= minFgDelta || gradMag[idx] >= edgeBarrierThreshold * 0.55f) {
                        fgMask[idx] = true
                    }
                }
            }
        }

        // If the garment was very close in color to the background (Case 5) and too few pixels survived,
        // fall back to edge-enclosed + local contrast segmentation.
        val fgCount = fgMask.count { it }
        if (fgCount < total * MIN_GARMENT_AREA_RATIO) {
            return buildEdgeAssistedLowContrastMask(workW, workH, bgDist, gradMag, borderP85)
        }

        // 6. Exclude person skin/head/arms when clothing is worn by a person (Case 8)
        excludePersonSkinRegions(fgMask, skinScore, lArr, aArr, bArr, workW, workH)

        return fgMask
    }

    /**
     * Specialized fallback for Case 5: a garment photographed on a very similar-colored background
     * where silhouette edges (Sobel gradients) and subtle tonal shifts define the garment boundary.
     */
    private fun buildEdgeAssistedLowContrastMask(
        workW: Int,
        workH: Int,
        bgDist: FloatArray,
        gradMag: FloatArray,
        borderBaseline: Float
    ): BooleanArray {
        val total = workW * workH
        val mask = BooleanArray(total)
        val relaxedThreshold = max(2.2f, borderBaseline * 0.95f)

        for (y in 2 until workH - 2) {
            for (x in 2 until workW - 2) {
                val idx = y * workW + x
                if (bgDist[idx] > relaxedThreshold || gradMag[idx] > 4.2f) {
                    mask[idx] = true
                }
            }
        }

        val dilated = morphDilate(mask, workW, workH, radius = 2)
        val closed = fillInteriorHoles(dilated, workW, workH)
        return morphErode(closed, workW, workH, radius = 1)
    }

    /**
     * Excludes person skin/head/neck/limb regions when clothing is worn by a person (Case 8),
     * while preserving beige/tan/warm-toned clothing if the garment itself is the entire subject.
     */
    private fun excludePersonSkinRegions(
        fgMask: BooleanArray,
        skinScore: FloatArray,
        lArr: FloatArray,
        aArr: FloatArray,
        bArr: FloatArray,
        workW: Int,
        workH: Int
    ) {
        val total = workW * workH
        var fgTotal = 0
        var highSkinCount = 0
        for (i in 0 until total) {
            if (fgMask[i]) {
                fgTotal++
                if (skinScore[i] > 0.65f) highSkinCount++
            }
        }
        if (fgTotal == 0) return

        // Only strip skin regions when skin makes up a partial fraction (e.g. head/neck/hands < 45% of foreground)
        // so that a camel/beige trench coat or tan sweater (which would be > 65% uniform color) is NOT erased!
        val skinFraction = highSkinCount.toFloat() / fgTotal.toFloat()
        if (skinFraction in 0.02f..0.46f) {
            // Seed skin removal from upper head/neck zone (y < 0.32) and outer arm zones
            val skinSeedMask = BooleanArray(total)
            val queue = IntArray(total)
            var qH = 0
            var qT = 0

            for (y in 0 until workH) {
                val ny = y.toFloat() / workH.toFloat()
                for (x in 0 until workW) {
                    val nx = x.toFloat() / workW.toFloat()
                    val idx = y * workW + x
                    val isHeadOrNeckZone = ny < 0.28f && nx in 0.22f..0.78f
                    val isOuterArmOrLegZone = (nx < 0.18f || nx > 0.82f || ny > 0.85f)
                    if (fgMask[idx] && skinScore[idx] > 0.68f && (isHeadOrNeckZone || isOuterArmOrLegZone)) {
                        skinSeedMask[idx] = true
                        queue[qT++] = idx
                    }
                }
            }

            val dx4 = intArrayOf(1, -1, 0, 0)
            val dy4 = intArrayOf(0, 0, 1, -1)
            while (qH < qT) {
                val curr = queue[qH++]
                val cx = curr % workW
                val cy = curr / workW
                for (d in 0..3) {
                    val nx = cx + dx4[d]
                    val ny = cy + dy4[d]
                    if (nx in 0 until workW && ny in 0 until workH) {
                        val nIdx = ny * workW + nx
                        if (fgMask[nIdx] && !skinSeedMask[nIdx] && skinScore[nIdx] > 0.55f) {
                            val colorStep = hypot(aArr[nIdx] - aArr[curr], bArr[nIdx] - bArr[curr]) +
                                abs(lArr[nIdx] - lArr[curr]) * 0.5f
                            if (colorStep < 10.5f) {
                                skinSeedMask[nIdx] = true
                                queue[qT++] = nIdx
                            }
                        }
                    }
                }
            }

            // Also remove hair/head region directly above a detected neck skin region
            var maxHeadY = 0
            for (y in 0 until (workH * 0.28f).toInt()) {
                for (x in 0 until workW) {
                    if (skinSeedMask[y * workW + x]) {
                        if (y > maxHeadY) maxHeadY = y
                    }
                }
            }

            for (i in 0 until total) {
                if (skinSeedMask[i]) {
                    fgMask[i] = false
                }
            }
            if (maxHeadY > 4) {
                // Trim head region above the upper neck line
                val neckCutY = (maxHeadY * 0.72f).toInt()
                for (y in 0 until neckCutY) {
                    for (x in (workW * 0.25f).toInt()..(workW * 0.75f).toInt()) {
                        fgMask[y * workW + x] = false
                    }
                }
            }
        }
    }

    /**
     * Cleans the raw foreground mask:
     * - Removes thin hanger hooks at the top center (Case 2: shirt on a hanger).
     * - Applies light morphological closing to bridge 1px gaps along seams while preserving
     *   narrow straps and sleeve hems (Case 6).
     * - Fills enclosed interior holes so patterned / multi-colored garments (Case 4) remain solid.
     */
    private fun refineGarmentMask(
        rawMask: BooleanArray,
        workW: Int,
        workH: Int,
        lArr: FloatArray,
        aArr: FloatArray,
        bArr: FloatArray,
        skinScore: FloatArray
    ): BooleanArray {
        // 1. Morphological close (radius 1) to seal tiny 1px seam gaps without losing straps
        val closed = morphErode(morphDilate(rawMask, workW, workH, radius = 1), workW, workH, radius = 1)

        // 2. Fill interior holes (protects buttons, logos, stripes, and interior patterns)
        val holeFilled = fillInteriorHoles(closed, workW, workH)

        // 3. Remove thin wire/wooden hanger hook at the top of the garment (Case 2)
        // A hanger hook is characterized by very narrow horizontal span (< 6.5% of image width)
        // in the top 22% of the frame before the collar/shoulders widen out.
        val hangerTrimmed = holeFilled.copyOf()
        val maxHookWidth = max(3, (workW * 0.065f).roundToInt())
        val topScanLimit = (workH * 0.24f).toInt()

        for (y in 0 until topScanLimit) {
            var minX = workW
            var maxX = -1
            var count = 0
            for (x in (workW / 4)..(workW * 3 / 4)) {
                if (hangerTrimmed[y * workW + x]) {
                    count++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
            val span = if (maxX >= minX) (maxX - minX + 1) else 0
            if (count > 0 && span <= maxHookWidth) {
                for (x in minX..maxX) {
                    hangerTrimmed[y * workW + x] = false
                }
            } else if (span > maxHookWidth * 2) {
                // Reached the garment collar/shoulders — stop trimming
                break
            }
        }

        return hangerTrimmed
    }

    // =========================================================================
    // STAGE 2: CONNECTED COMPONENT LABELING & MULTI-CANDIDATE RANKING
    // =========================================================================

    private class InternalComponent(
        val mask: BooleanArray,
        val areaFraction: Float,
        val prominenceScore: Float,
        val boundingBox: GarmentBoundingBox
    )

    private fun extractCandidateComponents(
        mask: BooleanArray,
        workW: Int,
        workH: Int
    ): List<InternalComponent> {
        val total = workW * workH
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        val minPixels = (total * MIN_GARMENT_AREA_RATIO).toInt().coerceAtLeast(24)

        val components = ArrayList<InternalComponent>()
        val dx8 = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        val dy8 = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        for (startIdx in 0 until total) {
            if (!mask[startIdx] || visited[startIdx]) continue

            var qH = 0
            var qT = 0
            visited[startIdx] = true
            queue[qT++] = startIdx

            var minX = workW
            var maxX = 0
            var minY = workH
            var maxY = 0
            var sumX = 0L
            var sumY = 0L

            while (qH < qT) {
                val curr = queue[qH++]
                val cx = curr % workW
                val cy = curr / workW

                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                sumX += cx
                sumY += cy

                for (d in 0..7) {
                    val nx = cx + dx8[d]
                    val ny = cy + dy8[d]
                    if (nx in 0 until workW && ny in 0 until workH) {
                        val nIdx = ny * workW + nx
                        if (mask[nIdx] && !visited[nIdx]) {
                            visited[nIdx] = true
                            queue[qT++] = nIdx
                        }
                    }
                }
            }

            val pixelCount = qT
            if (pixelCount >= minPixels) {
                val compMask = BooleanArray(total)
                for (i in 0 until pixelCount) {
                    compMask[queue[i]] = true
                }

                val areaFrac = pixelCount.toFloat() / total.toFloat()
                val centroidX = (sumX.toFloat() / pixelCount) / workW.toFloat()
                val centroidY = (sumY.toFloat() / pixelCount) / workH.toFloat()
                val distFromCenter = hypot(centroidX - 0.5f, centroidY - 0.5f)
                val centralityWeight = exp(-distFromCenter * 2.2f)

                val bbox = GarmentBoundingBox(
                    left = (minX.toFloat() / workW.toFloat()).coerceIn(0f, 1f),
                    top = (minY.toFloat() / workH.toFloat()).coerceIn(0f, 1f),
                    right = ((maxX + 1).toFloat() / workW.toFloat()).coerceIn(0f, 1f),
                    bottom = ((maxY + 1).toFloat() / workH.toFloat()).coerceIn(0f, 1f)
                )

                val score = areaFrac * (0.65f + 0.35f * centralityWeight)
                components.add(
                    InternalComponent(
                        mask = compMask,
                        areaFraction = areaFrac,
                        prominenceScore = score,
                        boundingBox = bbox
                    )
                )
            }
        }

        components.sortByDescending { it.prominenceScore }
        return components.take(4)
    }

    // =========================================================================
    // STAGE 3: EXACT SILHOUETTE CONTOUR TRACING & SPLINE SMOOTHING
    // =========================================================================

    /**
     * Traces the exact outer boundary of a binary garment mask using Moore-Neighbor 8-connected
     * contour tracing, then applies Ramer-Douglas-Peucker simplification and Chaikin corner-cutting
     * spline smoothing to produce a fluid, continuous silhouette path in normalized [0..1] coords.
     */
    fun traceAndSmoothContour(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): List<NormalizedPoint> {
        val rawBoundary = traceMooreNeighborContour(mask, width, height)
        if (rawBoundary.size < 6) {
            return buildFallbackContourFromMaskBounds(mask, width, height)
        }

        // Simplify 1px pixel staircases while keeping sleeve corners, collars, and trouser legs sharp
        val simplified = rdpSimplifyClosed(rawBoundary, epsilon = 1.10f)

        // Apply 2 passes of Chaikin corner-cutting to produce a smooth organic garment silhouette
        val smoothed = chaikinSmoothClosed(simplified, iterations = 2)

        return smoothed.map { pt ->
            NormalizedPoint(
                x = (pt.first / width.toFloat()).coerceIn(0f, 1f),
                y = (pt.second / height.toFloat()).coerceIn(0f, 1f)
            )
        }
    }

    /**
     * Moore-Neighbor 8-connected outer boundary contour tracer.
     * Guaranteed to follow the true perimeter of sleeves, collars, hems, straps, and trouser legs.
     */
    private fun traceMooreNeighborContour(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): List<Pair<Float, Float>> {
        // Clockwise 8-neighborhood offsets: N, NE, E, SE, S, SW, W, NW
        val dx = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val dy = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)

        fun isFg(x: Int, y: Int): Boolean {
            return x in 0 until width && y in 0 until height && mask[y * width + x]
        }

        // Find top-most, left-most foreground pixel
        var startX = -1
        var startY = -1
        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (isFg(x, y)) {
                    startX = x
                    startY = y
                    break@outer
                }
            }
        }
        if (startX == -1) return emptyList()

        val contour = ArrayList<Pair<Float, Float>>(width * 2 + height * 2)
        var currX = startX
        var currY = startY
        // We entered (startX, startY) from West (dir = 6)
        var backtrackDir = 6
        val maxSteps = width * height * 2
        var steps = 0

        do {
            contour.add(Pair(currX + 0.5f, currY + 0.5f))
            var foundNext = false
            // Search clockwise starting from (backtrackDir + 1) % 8
            val startSearchDir = (backtrackDir + 1) and 7
            for (k in 0..7) {
                val dir = (startSearchDir + k) and 7
                val nx = currX + dx[dir]
                val ny = currY + dy[dir]
                if (isFg(nx, ny)) {
                    currX = nx
                    currY = ny
                    // Opposite direction of dir is (dir + 4) % 8
                    backtrackDir = (dir + 4) and 7
                    foundNext = true
                    break
                }
            }
            if (!foundNext) break
            steps++
        } while ((currX != startX || currY != startY) && steps < maxSteps)

        return contour
    }

    private fun rdpSimplifyClosed(
        points: List<Pair<Float, Float>>,
        epsilon: Float
    ): List<Pair<Float, Float>> {
        if (points.size < 8) return points
        // Split closed loop at the two farthest points so both halves are simplified cleanly
        val first = points.first()
        var farthestIdx = points.size / 2
        var maxDist = -1f
        for (i in 1 until points.size) {
            val d = hypot(points[i].first - first.first, points[i].second - first.second)
            if (d > maxDist) {
                maxDist = d
                farthestIdx = i
            }
        }

        val half1 = rdpSimplifyOpen(points.subList(0, farthestIdx + 1), epsilon)
        val half2 = rdpSimplifyOpen(points.subList(farthestIdx, points.size) + first, epsilon)

        return (half1.dropLast(1) + half2.dropLast(1))
    }

    private fun rdpSimplifyOpen(
        pts: List<Pair<Float, Float>>,
        epsilon: Float
    ): List<Pair<Float, Float>> {
        if (pts.size < 3) return pts
        val start = pts.first()
        val end = pts.last()
        var maxDist = 0f
        var index = 0

        for (i in 1 until pts.lastIndex) {
            val d = perpendicularDistance(pts[i], start, end)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val left = rdpSimplifyOpen(pts.subList(0, index + 1), epsilon)
            val right = rdpSimplifyOpen(pts.subList(index, pts.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistance(
        pt: Pair<Float, Float>,
        lineStart: Pair<Float, Float>,
        lineEnd: Pair<Float, Float>
    ): Float {
        val dx = lineEnd.first - lineStart.first
        val dy = lineEnd.second - lineStart.second
        val mag = hypot(dx, dy)
        if (mag < 1e-5f) {
            return hypot(pt.first - lineStart.first, pt.second - lineStart.second)
        }
        return abs(dy * pt.first - dx * pt.second + lineEnd.first * lineStart.second - lineEnd.second * lineStart.first) / mag
    }

    private fun chaikinSmoothClosed(
        points: List<Pair<Float, Float>>,
        iterations: Int
    ): List<Pair<Float, Float>> {
        var current = points
        repeat(iterations) {
            if (current.size < 4) return current
            val next = ArrayList<Pair<Float, Float>>(current.size * 2)
            val n = current.size
            for (i in 0 until n) {
                val p0 = current[i]
                val p1 = current[(i + 1) % n]
                val qx = 0.75f * p0.first + 0.25f * p1.first
                val qy = 0.75f * p0.second + 0.25f * p1.second
                val rx = 0.25f * p0.first + 0.75f * p1.first
                val ry = 0.25f * p0.second + 0.75f * p1.second
                next.add(Pair(qx, qy))
                next.add(Pair(rx, ry))
            }
            current = next
        }
        // Cap maximum points to ~320 so PathMeasure animation stays 60fps
        if (current.size > 320) {
            val step = current.size.toFloat() / 320f
            return List(320) { idx -> current[(idx * step).toInt().coerceIn(0, current.lastIndex)] }
        }
        return current
    }

    private fun buildFallbackContourFromMaskBounds(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): List<NormalizedPoint> {
        // Radial hull scan if Moore-Neighbor returned too few points
        var sumX = 0f
        var sumY = 0f
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    sumX += x
                    sumY += y
                    count++
                }
            }
        }
        if (count == 0) return emptyList()
        val cx = sumX / count
        val cy = sumY / count
        val rays = 72
        val maxRadius = hypot(width.toFloat(), height.toFloat())
        val pts = ArrayList<NormalizedPoint>(rays)
        for (i in 0 until rays) {
            val angle = (i.toFloat() / rays) * (2.0 * Math.PI)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            var farthestR = 2f
            var r = 1f
            while (r < maxRadius) {
                val rx = (cx + cosA * r).roundToInt()
                val ry = (cy + sinA * r).roundToInt()
                if (rx !in 0 until width || ry !in 0 until height) break
                if (mask[ry * width + rx]) {
                    farthestR = r
                }
                r += 1f
            }
            pts.add(
                NormalizedPoint(
                    x = ((cx + cosA * farthestR) / width).coerceIn(0f, 1f),
                    y = ((cy + sinA * farthestR) / height).coerceIn(0f, 1f)
                )
            )
        }
        return pts
    }

    // =========================================================================
    // STAGE 4: FEATHERED ALPHA MASK & HIGH-RES TRANSPARENT CUTOUT GENERATION
    // =========================================================================

    /**
     * Converts a binary foreground mask into a smooth 0..255 alpha mask with anti-aliased edge
     * feathering so isolated garments have clean, natural edges without jagged pixel halos.
     */
    private fun buildFeatheredAlphaMask(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): ByteArray {
        val total = width * height
        val rawAlpha = FloatArray(total) { idx -> if (mask[idx]) 255f else 0f }
        val feathered = ByteArray(total)

        // 3x3 Gaussian-weighted edge anti-aliasing applied along boundary pixels
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    feathered[idx] = rawAlpha[idx].toInt().toByte()
                    continue
                }

                val center = mask[idx]
                val hasOppositeNeighbor =
                    mask[idx - 1] != center ||
                        mask[idx + 1] != center ||
                        mask[idx - width] != center ||
                        mask[idx + width] != center

                if (!hasOppositeNeighbor) {
                    feathered[idx] = if (center) 255.toByte() else 0.toByte()
                } else {
                    var sum = rawAlpha[idx] * 4f
                    sum += (rawAlpha[idx - 1] + rawAlpha[idx + 1] + rawAlpha[idx - width] + rawAlpha[idx + width]) * 2f
                    sum += rawAlpha[idx - width - 1] + rawAlpha[idx - width + 1] +
                        rawAlpha[idx + width - 1] + rawAlpha[idx + width + 1]
                    val avg = (sum / 16f).roundToInt().coerceIn(0, 255)
                    // Bias interior boundary pixels slightly upward so garment edges stay crisp
                    val biased = if (center) max(165, avg) else min(85, avg)
                    feathered[idx] = biased.toByte()
                }
            }
        }
        return feathered
    }

    /**
     * Applies the feathered alpha mask (via bilinear interpolation) to the full-resolution
     * source bitmap, producing a genuine ARGB_8888 Bitmap with transparent background (`alpha = 0`)
     * and original garment colors preserved (`alpha = 255`).
     */
    private fun createHighResCutout(
        sourceBitmap: Bitmap,
        softAlphaMask: ByteArray,
        maskW: Int,
        maskH: Int
    ): Bitmap {
        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height
        val output = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
        output.setHasAlpha(true)

        val rowPixels = IntArray(srcW)
        val scaleX = (maskW - 1).toFloat() / max(1, srcW - 1).toFloat()
        val scaleY = (maskH - 1).toFloat() / max(1, srcH - 1).toFloat()

        for (y in 0 until srcH) {
            sourceBitmap.getPixels(rowPixels, 0, srcW, 0, y, srcW, 1)
            val my = y * scaleY
            val y0 = my.toInt().coerceIn(0, maskH - 1)
            val y1 = (y0 + 1).coerceIn(0, maskH - 1)
            val fy = my - y0

            for (x in 0 until srcW) {
                val mx = x * scaleX
                val x0 = mx.toInt().coerceIn(0, maskW - 1)
                val x1 = (x0 + 1).coerceIn(0, maskW - 1)
                val fx = mx - x0

                val a00 = softAlphaMask[y0 * maskW + x0].toInt() and 0xFF
                val a10 = softAlphaMask[y0 * maskW + x1].toInt() and 0xFF
                val a01 = softAlphaMask[y1 * maskW + x0].toInt() and 0xFF
                val a11 = softAlphaMask[y1 * maskW + x1].toInt() and 0xFF

                val topInterp = a00 + (a10 - a00) * fx
                val botInterp = a01 + (a11 - a01) * fx
                val interpAlpha = (topInterp + (botInterp - topInterp) * fy).roundToInt().coerceIn(0, 255)

                if (interpAlpha <= 12) {
                    // Genuine transparent pixel (alpha = 0)
                    rowPixels[x] = Color.TRANSPARENT
                } else {
                    val srcColor = rowPixels[x]
                    val srcAlpha = (srcColor ushr 24) and 0xFF
                    val finalAlpha = ((srcAlpha * interpAlpha) / 255).coerceIn(0, 255)
                    rowPixels[x] = (srcColor and 0x00FFFFFF) or (finalAlpha shl 24)
                }
            }
            output.setPixels(rowPixels, 0, srcW, 0, y, srcW, 1)
        }

        return output
    }

    // =========================================================================
    // MORPHOLOGICAL & COLOR SPACE HELPERS
    // =========================================================================

    private fun fillInteriorHoles(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): BooleanArray {
        val total = width * height
        val exteriorBg = BooleanArray(total)
        val queue = IntArray(total)
        var qH = 0
        var qT = 0

        fun seedIfBg(idx: Int) {
            if (!mask[idx] && !exteriorBg[idx]) {
                exteriorBg[idx] = true
                queue[qT++] = idx
            }
        }

        for (x in 0 until width) {
            seedIfBg(x)
            seedIfBg((height - 1) * width + x)
        }
        for (y in 0 until height) {
            seedIfBg(y * width)
            seedIfBg(y * width + (width - 1))
        }

        val dx4 = intArrayOf(1, -1, 0, 0)
        val dy4 = intArrayOf(0, 0, 1, -1)
        while (qH < qT) {
            val curr = queue[qH++]
            val cx = curr % width
            val cy = curr / width
            for (d in 0..3) {
                val nx = cx + dx4[d]
                val ny = cy + dy4[d]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (!mask[nIdx] && !exteriorBg[nIdx]) {
                        exteriorBg[nIdx] = true
                        queue[qT++] = nIdx
                    }
                }
            }
        }

        // Any non-mask pixel that is NOT connected to the exterior border is an interior hole
        val result = mask.copyOf()
        for (i in 0 until total) {
            if (!result[i] && !exteriorBg[i]) {
                result[i] = true
            }
        }
        return result
    }

    private fun morphDilate(
        mask: BooleanArray,
        width: Int,
        height: Int,
        radius: Int
    ): BooleanArray {
        val out = BooleanArray(width * height)
        for (y in 0 until height) {
            val yMin = (y - radius).coerceAtLeast(0)
            val yMax = (y + radius).coerceAtMost(height - 1)
            for (x in 0 until width) {
                val xMin = (x - radius).coerceAtLeast(0)
                val xMax = (x + radius).coerceAtMost(width - 1)
                var hit = false
                outer@ for (ny in yMin..yMax) {
                    val rowOffset = ny * width
                    for (nx in xMin..xMax) {
                        if (mask[rowOffset + nx]) {
                            hit = true
                            break@outer
                        }
                    }
                }
                out[y * width + x] = hit
            }
        }
        return out
    }

    private fun morphErode(
        mask: BooleanArray,
        width: Int,
        height: Int,
        radius: Int
    ): BooleanArray {
        val out = BooleanArray(width * height)
        for (y in 0 until height) {
            val yMin = (y - radius).coerceAtLeast(0)
            val yMax = (y + radius).coerceAtMost(height - 1)
            for (x in 0 until width) {
                val xMin = (x - radius).coerceAtLeast(0)
                val xMax = (x + radius).coerceAtMost(width - 1)
                var allSet = true
                outer@ for (ny in yMin..yMax) {
                    val rowOffset = ny * width
                    for (nx in xMin..xMax) {
                        if (!mask[rowOffset + nx]) {
                            allSet = false
                            break@outer
                        }
                    }
                }
                out[y * width + x] = allSet
            }
        }
        return out
    }

    private fun clusterBorderColors(
        indices: List<Int>,
        lArr: FloatArray,
        aArr: FloatArray,
        bArr: FloatArray,
        k: Int
    ): List<FloatArray> {
        if (indices.isEmpty()) {
            return listOf(floatArrayOf(95f, 0f, 0f))
        }
        val numClusters = min(k, indices.size)
        val centers = Array(numClusters) { c ->
            val sampleIdx = indices[(c * indices.size) / numClusters]
            floatArrayOf(lArr[sampleIdx], aArr[sampleIdx], bArr[sampleIdx])
        }

        val counts = IntArray(numClusters)
        val sumL = FloatArray(numClusters)
        val sumA = FloatArray(numClusters)
        val sumB = FloatArray(numClusters)

        repeat(5) {
            counts.fill(0)
            sumL.fill(0f)
            sumA.fill(0f)
            sumB.fill(0f)

            for (idx in indices) {
                val l = lArr[idx]
                val a = aArr[idx]
                val b = bArr[idx]
                var bestC = 0
                var bestDist = Float.MAX_VALUE
                for (c in 0 until numClusters) {
                    val dL = l - centers[c][0]
                    val da = a - centers[c][1]
                    val db = b - centers[c][2]
                    val d = dL * dL + da * da + db * db
                    if (d < bestDist) {
                        bestDist = d
                        bestC = c
                    }
                }
                counts[bestC]++
                sumL[bestC] += l
                sumA[bestC] += a
                sumB[bestC] += b
            }

            for (c in 0 until numClusters) {
                if (counts[c] > 0) {
                    centers[c][0] = sumL[c] / counts[c]
                    centers[c][1] = sumA[c] / counts[c]
                    centers[c][2] = sumB[c] / counts[c]
                }
            }
        }

        // Return clusters that represent at least 8% of border pixels
        val minCount = max(1, (indices.size * 0.08f).toInt())
        val valid = centers.filterIndexed { index, _ -> counts[index] >= minCount }
        return valid.ifEmpty { listOf(centers[0]) }
    }

    private fun rgbToLab(r: Int, g: Int, b: Int): FloatArray {
        var rn = r / 255.0f
        var gn = g / 255.0f
        var bn = b / 255.0f

        rn = if (rn > 0.04045f) ((rn + 0.055f) / 1.055f).pow(2.4f) else rn / 12.92f
        gn = if (gn > 0.04045f) ((gn + 0.055f) / 1.055f).pow(2.4f) else gn / 12.92f
        bn = if (bn > 0.04045f) ((bn + 0.055f) / 1.055f).pow(2.4f) else bn / 12.92f

        val x = (rn * 0.4124f + gn * 0.3576f + bn * 0.1805f) / 0.95047f
        val y = (rn * 0.2126f + gn * 0.7152f + bn * 0.0722f) / 1.00000f
        val z = (rn * 0.0193f + gn * 0.1192f + bn * 0.9505f) / 1.08883f

        val fx = if (x > 0.008856f) x.pow(1f / 3f) else (7.787f * x) + (16f / 116f)
        val fy = if (y > 0.008856f) y.pow(1f / 3f) else (7.787f * y) + (16f / 116f)
        val fz = if (z > 0.008856f) z.pow(1f / 3f) else (7.787f * z) + (16f / 116f)

        val l = (116f * fy) - 16f
        val a = 500f * (fx - fy)
        val bVal = 200f * (fy - fz)
        return floatArrayOf(l, a, bVal)
    }

    private fun computeSkinLikelihood(r: Int, g: Int, b: Int): Float {
        // YCbCr chromaticity skin model
        val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
        val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b
        val isRgbSkinRule = r > 95 && g > 40 && b > 20 &&
            (max(r, max(g, b)) - min(r, min(g, b))) > 15 &&
            abs(r - g) > 15 && r > g && r > b

        if (!isRgbSkinRule) return 0f
        val cbDist = abs(cb - 110f) / 25f
        val crDist = abs(cr - 152f) / 25f
        val d = hypot(cbDist, crDist)
        return (1f - d * 0.65f).coerceIn(0f, 1f)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun sampleBitmapToWorkingGrid(
        source: Bitmap,
        workW: Int,
        workH: Int
    ): IntArray {
        val srcW = source.width
        val srcH = source.height
        val out = IntArray(workW * workH)
        if (srcW == workW && srcH == workH) {
            source.getPixels(out, 0, workW, 0, 0, workW, workH)
            return out
        }
        val srcRow = IntArray(srcW)
        var lastRowY = -1
        for (wy in 0 until workH) {
            val sy = ((wy.toFloat() / max(1, workH - 1)) * (srcH - 1)).roundToInt().coerceIn(0, srcH - 1)
            if (sy != lastRowY) {
                source.getPixels(srcRow, 0, srcW, 0, sy, srcW, 1)
                lastRowY = sy
            }
            val rowOffset = wy * workW
            for (wx in 0 until workW) {
                val sx = ((wx.toFloat() / max(1, workW - 1)) * (srcW - 1)).roundToInt().coerceIn(0, srcW - 1)
                out[rowOffset + wx] = srcRow[sx]
            }
        }
        return out
    }
}
