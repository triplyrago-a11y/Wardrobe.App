package com.example.wardrobe.vision

import android.graphics.Bitmap

/**
 * Normalized 2D coordinate in image space ([0.0f, 1.0f] along width and height).
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
)

/**
 * Normalized bounding box in image space ([0.0f, 1.0f]).
 */
data class GarmentBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
}

/**
 * Interactive tool modes for user-controlled clothing selection before extraction.
 */
enum class SelectionToolMode(val label: String) {
    BRUSH("Brush"),
    CIRCLE_FREEHAND("Circle"),
    ERASER("Eraser"),
    PAN_ZOOM("Zoom")
}

/**
 * Represents a single user drawing gesture (brush stroke, freehand/circle loop, or eraser stroke)
 * stored in normalized [0..1] image coordinates so zooming and panning keep marks 1:1 aligned
 * with the underlying garment pixels.
 */
data class SelectionStroke(
    val id: Long = System.nanoTime(),
    val tool: SelectionToolMode,
    val points: List<NormalizedPoint>,
    val strokeWidthNormalized: Float = 0.065f
)

/**
 * Holds the ordered history of user drawing gestures for garment extraction.
 */
data class UserSelectionMarkup(
    val strokes: List<SelectionStroke> = emptyList()
) {
    /**
     * Returns true if the user has drawn at least one positive selection mark (Brush or Circle/Freehand)
     * with points inside the image bounds.
     */
    val hasPositiveSelection: Boolean
        get() = strokes.any { stroke ->
            (stroke.tool == SelectionToolMode.BRUSH || stroke.tool == SelectionToolMode.CIRCLE_FREEHAND) &&
                stroke.points.isNotEmpty()
        }

    fun addStroke(stroke: SelectionStroke): UserSelectionMarkup =
        copy(strokes = strokes + stroke)

    fun undoLast(): UserSelectionMarkup =
        if (strokes.isEmpty()) this else copy(strokes = strokes.dropLast(1))

    fun clearAll(): UserSelectionMarkup =
        copy(strokes = emptyList())
}

/**
 * Represents a single detected garment candidate from the segmentation pipeline,
 * including its high-precision alpha mask, traced silhouette contour, and
 * isolated transparent ARGB_8888 cutout bitmap.
 */
data class GarmentCandidate(
    val id: Int,
    val label: String,
    val prominenceScore: Float,
    val areaFraction: Float,
    val boundingBox: GarmentBoundingBox,
    val contourPoints: List<NormalizedPoint>,
    val maskWidth: Int,
    val maskHeight: Int,
    val alphaMask: ByteArray,
    val cutoutBitmap: Bitmap
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GarmentCandidate) return false
        return id == other.id &&
            label == other.label &&
            prominenceScore == other.prominenceScore &&
            boundingBox == other.boundingBox &&
            contourPoints == other.contourPoints
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + label.hashCode()
        result = 31 * result + prominenceScore.hashCode()
        result = 31 * result + boundingBox.hashCode()
        return result
    }
}

/**
 * User-controlled selection + Samsung AI-inspired contour tracing & background removal stages.
 */
enum class SegmentationStage(val statusLabel: String) {
    IDLE("Mark clothing to extract"),
    AWAITING_USER_SELECTION("Brush or circle clothing item"),
    PHASE_A_DETECTING("Extracting selected garment..."),
    PHASE_B_CONTOUR_APPEARING("Locking garment silhouette..."),
    PHASE_C_TRACING_OUTLINE("Tracing garment boundary..."),
    PHASE_D_REMOVING_BACKGROUND("Removing background..."),
    PHASE_E_ISOLATED_READY("Garment isolated"),
    ERROR("Could not extract garment")
}

/**
 * Result of running the garment segmentation and contour extraction pipeline.
 */
sealed class GarmentSegmentationResult {
    data class Success(
        val sourceBitmap: Bitmap,
        val candidates: List<GarmentCandidate>,
        val defaultCandidateIndex: Int = 0,
        val processingTimeMs: Long = 0L
    ) : GarmentSegmentationResult() {
        val primaryCandidate: GarmentCandidate
            get() = candidates.getOrElse(defaultCandidateIndex) { candidates.first() }
    }

    data class Failure(
        val message: String,
        val fallbackBitmap: Bitmap? = null
    ) : GarmentSegmentationResult()
}
