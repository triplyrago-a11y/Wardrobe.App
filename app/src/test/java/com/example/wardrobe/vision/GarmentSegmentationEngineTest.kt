package com.example.wardrobe.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GarmentSegmentationEngineTest {

    /**
     * Deterministic pixel-buffer builder so synthetic garment test images are populated directly
     * into the Bitmap's ARGB_8888 pixel buffer.
     */
    private class PixelCanvas(val width: Int, val height: Int, bgColor: Int) {
        val pixels = IntArray(width * height) { bgColor }

        fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            val x0 = left.coerceIn(0, width - 1)
            val x1 = right.coerceIn(0, width - 1)
            val y0 = top.coerceIn(0, height - 1)
            val y1 = bottom.coerceIn(0, height - 1)
            for (y in y0..y1) {
                val row = y * width
                for (x in x0..x1) {
                    pixels[row + x] = color
                }
            }
        }

        fun strokeRect(left: Int, top: Int, right: Int, bottom: Int, strokeWidth: Int, color: Int) {
            fillRect(left, top, right, top + strokeWidth, color)
            fillRect(left, bottom - strokeWidth, right, bottom, color)
            fillRect(left, top, left + strokeWidth, bottom, color)
            fillRect(right - strokeWidth, top, right, bottom, color)
        }

        fun fillPolygon(points: List<Pair<Float, Float>>, color: Int) {
            if (points.size < 3) return
            for (y in 0 until height) {
                val py = y + 0.5f
                for (x in 0 until width) {
                    val px = x + 0.5f
                    if (isPointInPolygon(px, py, points)) {
                        pixels[y * width + x] = color
                    }
                }
            }
        }

        private fun isPointInPolygon(px: Float, py: Float, pts: List<Pair<Float, Float>>): Boolean {
            var inside = false
            var j = pts.lastIndex
            for (i in pts.indices) {
                val xi = pts[i].first
                val yi = pts[i].second
                val xj = pts[j].first
                val yj = pts[j].second
                val intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / ((yj - yi) + 1e-7f) + xi)
                if (intersect) inside = !inside
                j = i
            }
            return inside
        }

        fun toBitmap(): Bitmap {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            return bmp
        }
    }

    /**
     * Case 1: A T-shirt on a plain background.
     * Verifies silhouette detection, non-rectangular contour extraction following sleeves/collar,
     * genuine alpha=0 transparent background, and preserved garment RGB colors.
     */
    @Test
    fun segmentGarment_tShirtOnPlainBackground_extractsSilhouetteAndTransparentCutout() {
        val pc = PixelCanvas(240, 240, Color.rgb(242, 242, 245))
        val navyColor = Color.rgb(30, 64, 138)

        val tShirtPolygon = listOf(
            85f to 45f,
            105f to 58f, // Collar dip
            135f to 58f,
            155f to 45f,
            205f to 80f, // Right sleeve outer
            185f to 110f, // Right sleeve cuff
            160f to 92f, // Right armpit
            160f to 195f, // Right hem
            80f to 195f, // Bottom hem
            80f to 92f, // Left armpit
            55f to 110f, // Left sleeve cuff
            35f to 80f // Left sleeve outer
        )
        pc.fillPolygon(tShirtPolygon, navyColor)
        val bmp = pc.toBitmap()

        val result = GarmentSegmentationEngine.segmentGarmentSync(bmp)
        assertTrue("Expected segmentation success", result is GarmentSegmentationResult.Success)
        val success = result as GarmentSegmentationResult.Success
        val primary = success.primaryCandidate

        // Contour must have detailed silhouette points
        assertTrue("Expected rich contour points (${primary.contourPoints.size})", primary.contourPoints.size >= 24)

        // Corner background pixel MUST be genuinely transparent (alpha == 0)
        val cornerPixel = primary.cutoutBitmap.getPixel(5, 5)
        assertEquals("Background corner must have alpha = 0", 0, Color.alpha(cornerPixel))

        // Center chest pixel MUST be opaque and preserve navy color
        val chestPixel = primary.cutoutBitmap.getPixel(120, 130)
        assertEquals("Garment interior must be fully opaque", 255, Color.alpha(chestPixel))
        assertEquals(30, Color.red(chestPixel))
        assertEquals(64, Color.green(chestPixel))
        assertEquals(138, Color.blue(chestPixel))
    }

    /**
     * Case 2: A shirt on a hanger.
     * Verifies the thin wire hanger hook at the top center is trimmed while the shirt body is kept.
     */
    @Test
    fun segmentGarment_shirtOnHanger_trimsThinTopHangerHook() {
        val pc = PixelCanvas(240, 240, Color.rgb(235, 235, 235))
        // Thin wire hook at top center (x = 118..122, y = 12..46)
        pc.fillRect(118, 12, 122, 46, Color.rgb(90, 90, 95))
        // Shirt body (y = 46..200)
        pc.fillRect(65, 46, 175, 200, Color.rgb(185, 45, 55))

        val result = GarmentSegmentationEngine.segmentGarmentSync(pc.toBitmap())
        assertTrue(result is GarmentSegmentationResult.Success)
        val cutout = (result as GarmentSegmentationResult.Success).primaryCandidate.cutoutBitmap

        // Top hanger hook at (120, 20) should be trimmed to transparent
        assertEquals("Thin top hanger hook should be transparent", 0, Color.alpha(cutout.getPixel(120, 20)))
        // Shirt torso at (120, 120) must be opaque
        assertEquals("Shirt torso must remain opaque", 255, Color.alpha(cutout.getPixel(120, 120)))
    }

    /**
     * Case 3: Trousers laid flat.
     * Verifies two trouser legs and the background gap between the legs are accurately segmented.
     */
    @Test
    fun segmentGarment_trousersLaidFlat_preservesBothLegsAndSeparatesBackground() {
        val pc = PixelCanvas(240, 240, Color.rgb(245, 240, 232))
        val trousersPolygon = listOf(
            75f to 35f, // Waistband left
            165f to 35f, // Waistband right
            175f to 210f, // Right ankle outer
            135f to 210f, // Right ankle inner
            120f to 105f, // Inseam crotch
            105f to 210f, // Left ankle inner
            65f to 210f // Left ankle outer
        )
        pc.fillPolygon(trousersPolygon, Color.rgb(42, 75, 115))

        val result = GarmentSegmentationEngine.segmentGarmentSync(pc.toBitmap())
        assertTrue(result is GarmentSegmentationResult.Success)
        val cutout = (result as GarmentSegmentationResult.Success).primaryCandidate.cutoutBitmap

        // Waistband & both legs should be opaque
        assertEquals(255, Color.alpha(cutout.getPixel(120, 60)))
        assertEquals(255, Color.alpha(cutout.getPixel(85, 180)))
        assertEquals(255, Color.alpha(cutout.getPixel(155, 180)))
        // Background between the lower trouser legs (120, 195) should be transparent
        assertEquals(0, Color.alpha(cutout.getPixel(120, 195)))
    }

    /**
     * Case 4: A patterned or multi-colored garment (including interior stripes matching background).
     * Verifies that enclosed interior patterns/stripes are preserved via hole-filling.
     */
    @Test
    fun segmentGarment_patternedGarment_preservesInteriorStripesAndPatterns() {
        val bgColor = Color.rgb(240, 240, 240)
        val pc = PixelCanvas(240, 240, bgColor)

        // Outer sweater body in dark charcoal
        pc.fillRect(60, 45, 180, 195, Color.rgb(35, 38, 45))
        // Interior horizontal stripes in white (matching background!) and emerald inside the sweater
        pc.fillRect(72, 85, 168, 105, bgColor)
        pc.fillRect(72, 120, 168, 140, Color.rgb(25, 150, 95))
        pc.fillRect(72, 155, 168, 170, bgColor)

        val result = GarmentSegmentationEngine.segmentGarmentSync(pc.toBitmap())
        assertTrue(result is GarmentSegmentationResult.Success)
        val cutout = (result as GarmentSegmentationResult.Success).primaryCandidate.cutoutBitmap

        // Enclosed white stripe at (120, 95) must be preserved (alpha == 255)
        assertEquals("Interior pattern stripe must stay opaque", 255, Color.alpha(cutout.getPixel(120, 95)))
        assertEquals("Interior green stripe must stay opaque", 255, Color.alpha(cutout.getPixel(120, 130)))
    }

    /**
     * Case 5: A garment near a similar-colored background.
     * Verifies that Sobel edge barriers and low-contrast tonal separation isolate an ivory shirt on a light wall.
     */
    @Test
    fun segmentGarment_similarColoredBackground_usesEdgeBarriersToIsolateGarment() {
        val pc = PixelCanvas(240, 240, Color.rgb(232, 232, 228))
        pc.fillRect(65, 45, 175, 195, Color.rgb(252, 244, 212))
        pc.strokeRect(65, 45, 175, 195, 3, Color.rgb(165, 155, 135))

        val result = GarmentSegmentationEngine.segmentGarmentSync(pc.toBitmap())
        assertTrue(result is GarmentSegmentationResult.Success)
        val cutout = (result as GarmentSegmentationResult.Success).primaryCandidate.cutoutBitmap

        assertEquals(0, Color.alpha(cutout.getPixel(10, 10)))
        assertEquals(255, Color.alpha(cutout.getPixel(120, 120)))
    }

    /**
     * Case 6: A garment with narrow straps, sleeves, or irregular edges.
     * Verifies cami/dress shoulder straps are preserved alongside the main dress silhouette.
     */
    @Test
    fun segmentGarment_narrowStrapsAndIrregularHem_preservesFineDetails() {
        val pc = PixelCanvas(240, 240, Color.rgb(245, 245, 247))
        val plumColor = Color.rgb(142, 45, 110)

        // Left & right shoulder straps
        pc.fillRect(86, 35, 95, 80, plumColor)
        pc.fillRect(145, 35, 154, 80, plumColor)
        // Dress bodice & flared skirt
        pc.fillRect(75, 78, 165, 200, plumColor)

        val result = GarmentSegmentationEngine.segmentGarmentSync(pc.toBitmap())
        assertTrue(result is GarmentSegmentationResult.Success)
        val cutout = (result as GarmentSegmentationResult.Success).primaryCandidate.cutoutBitmap

        // Dress bodice is opaque
        assertEquals(255, Color.alpha(cutout.getPixel(120, 140)))
        // Left strap at (90, 55) is preserved
        assertTrue("Narrow strap should be preserved", Color.alpha(cutout.getPixel(90, 55)) > 160)
    }

    /**
     * Case 7: Multiple clothing items in one image.
     * Verifies that multiple distinct garments are detected as separate candidates,
     * with the most prominent garment selected first and secondary candidates available.
     */
    @Test
    fun segmentGarment_multipleClothingItems_detectsMultipleCandidatesAndSelectsProminent() {
        val pc = PixelCanvas(260, 240, Color.rgb(242, 242, 245))
        // Garment 1 (Larger jacket in left-center)
        pc.fillRect(25, 40, 135, 200, Color.rgb(25, 85, 65))
        // Garment 2 (Smaller skirt on the right, separated by background gap)
        pc.fillRect(165, 65, 235, 180, Color.rgb(195, 85, 45))

        val result = GarmentSegmentationEngine.segmentGarmentSync(pc.toBitmap())
        assertTrue(result is GarmentSegmentationResult.Success)
        val success = result as GarmentSegmentationResult.Success

        assertTrue("Expected at least 2 garment candidates, got ${success.candidates.size}", success.candidates.size >= 2)
        // Primary candidate should be the larger jacket
        assertEquals(255, Color.alpha(success.candidates[0].cutoutBitmap.getPixel(80, 120)))
        assertEquals(0, Color.alpha(success.candidates[0].cutoutBitmap.getPixel(200, 120)))

        // Secondary candidate should be the skirt on the right
        assertEquals(255, Color.alpha(success.candidates[1].cutoutBitmap.getPixel(200, 120)))
        assertEquals(0, Color.alpha(success.candidates[1].cutoutBitmap.getPixel(80, 120)))
    }

    /**
     * Case 8: A person wearing clothing.
     * Verifies that skin-toned head/neck regions at the top are excluded so only the worn garment is isolated.
     */
    @Test
    fun segmentGarment_personWearingClothing_excludesSkinNeckAndIsolatesGarment() {
        val pc = PixelCanvas(240, 240, Color.rgb(235, 238, 242))
        // Person neck/face skin tone at top (y = 20..64)
        pc.fillRect(98, 20, 142, 64, Color.rgb(218, 160, 132))
        // Worn cobalt blue sweater (y = 64..205)
        pc.fillRect(60, 64, 180, 205, Color.rgb(28, 72, 165))

        val result = GarmentSegmentationEngine.segmentGarmentSync(pc.toBitmap())
        assertTrue(result is GarmentSegmentationResult.Success)
        val cutout = (result as GarmentSegmentationResult.Success).primaryCandidate.cutoutBitmap

        // Worn sweater torso must be opaque
        assertEquals(255, Color.alpha(cutout.getPixel(120, 140)))
        // Person neck/face at (120, 32) should be excluded (transparent)
        assertEquals("Person skin/head region should be excluded", 0, Color.alpha(cutout.getPixel(120, 32)))
    }

    /**
     * Transparent PNG Export Verification:
     * Verifies that exportTransparentPng saves a lossless PNG file that reloads with genuine alpha = 0
     * background transparency and intact garment RGB values.
     */
    @Test
    fun exportTransparentPng_writesValidArgb8888PngWithGenuineAlphaChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pc = PixelCanvas(160, 160, Color.rgb(245, 245, 245))
        pc.fillRect(40, 30, 120, 130, Color.rgb(40, 120, 90))

        val segResult = GarmentSegmentationEngine.segmentGarmentSync(pc.toBitmap()) as GarmentSegmentationResult.Success
        val exportedFile = GarmentSegmentationEngine.exportTransparentPng(
            context = context,
            cutoutBitmap = segResult.primaryCandidate.cutoutBitmap
        )

        assertTrue("Exported PNG file must exist", exportedFile.exists())
        assertTrue("Exported file must have .png extension", exportedFile.name.endsWith(".png"))
        assertTrue("Exported PNG must be non-empty", exportedFile.length() > 100L)

        val reloaded = BitmapFactory.decodeFile(exportedFile.absolutePath)
        assertNotNull(reloaded)
        assertTrue("Reloaded PNG must have alpha channel", reloaded.hasAlpha())
        assertEquals("Reloaded PNG background must be transparent (alpha=0)", 0, Color.alpha(reloaded.getPixel(5, 5)))
        assertEquals("Reloaded PNG garment center must be opaque (alpha=255)", 255, Color.alpha(reloaded.getPixel(80, 80)))
    }

    /**
     * User-Controlled Clothing Selection Verification:
     * When two garments are present and the user draws a Brush / Circle selection over the second
     * garment on the right, segmentGarmentWithUserMarkupSync extracts the user-marked garment
     * rather than defaulting to the left garment.
     */
    @Test
    fun segmentGarmentWithUserMarkup_extractsUserMarkedGarment() {
        val pc = PixelCanvas(260, 240, Color.rgb(242, 242, 245))
        // Garment 1 (Larger jacket on the left: x = 25..135)
        pc.fillRect(25, 40, 135, 200, Color.rgb(25, 85, 65))
        // Garment 2 (Smaller terracotta top on the right: x = 165..235, y = 65..180)
        pc.fillRect(165, 65, 235, 180, Color.rgb(195, 85, 45))

        // User brushes over the right-hand garment around normalized x = 0.77, y = 0.50
        val markup = UserSelectionMarkup(
            strokes = listOf(
                SelectionStroke(
                    tool = SelectionToolMode.BRUSH,
                    points = listOf(
                        NormalizedPoint(190f / 260f, 100f / 240f),
                        NormalizedPoint(200f / 260f, 120f / 240f),
                        NormalizedPoint(210f / 260f, 140f / 240f)
                    ),
                    strokeWidthNormalized = 0.065f
                )
            )
        )

        val result = GarmentSegmentationEngine.segmentGarmentWithUserMarkupSync(pc.toBitmap(), markup)
        assertTrue("Expected guided segmentation success", result is GarmentSegmentationResult.Success)
        val primaryCutout = (result as GarmentSegmentationResult.Success).primaryCandidate.cutoutBitmap

        // The user-marked right garment (200, 120) must be opaque
        assertEquals("User-selected right garment must be opaque", 255, Color.alpha(primaryCutout.getPixel(200, 120)))
        // The unselected left garment (80, 120) must be excluded (transparent)
        assertEquals("Unselected left garment must be transparent", 0, Color.alpha(primaryCutout.getPixel(80, 120)))
    }
}
