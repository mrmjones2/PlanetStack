package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.data.model.PlanetaryFrame
import com.example.data.model.StackingConfig
import com.example.data.model.StackingMethod
import com.example.engine.CentroidAligner
import com.example.engine.FrameQualityAnalyzer
import com.example.engine.PlanetaryStacker
import com.example.engine.SeeingScorer
import com.example.engine.SubPixelAligner
import com.example.engine.WaveletProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PlanetStackEngineTest {

    @Test
    fun `seeing scorer scores high contrast edge higher than flat surface`() {
        val size = 64
        val flatBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val flatPixels = IntArray(size * size) { Color.GRAY }
        flatBmp.setPixels(flatPixels, 0, size, 0, 0, size, size)

        val sharpBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val sharpPixels = IntArray(size * size) { i ->
            val x = i % size
            if (x % 4 == 0) Color.WHITE else Color.BLACK
        }
        sharpBmp.setPixels(sharpPixels, 0, size, 0, 0, size, size)

        val flatScore = SeeingScorer.computeLaplacianVariance(flatBmp)
        val sharpScore = SeeingScorer.computeLaplacianVariance(sharpBmp)

        assertTrue("Sharp edge pattern must have higher seeing score than flat color", sharpScore > flatScore)
    }

    @Test
    fun `centroid aligner calculates disc center of gravity`() {
        val size = 100
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { Color.BLACK }
        val targetX = 40
        val targetY = 60
        val radius = 15

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - targetX
                val dy = y - targetY
                if (dx * dx + dy * dy <= radius * radius) {
                    pixels[y * size + x] = Color.WHITE
                }
            }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)

        val centroid = CentroidAligner.computeCentroid(bmp)
        assertTrue("Centroid X should be around 40px, was ${centroid.x}", centroid.x in 39f..41f)
        assertTrue("Centroid Y should be around 60px, was ${centroid.y}", centroid.y in 59f..61f)
    }

    @Test
    fun `planetary stacker computes planetary integration and SNR boost`() = runBlocking {
        val size = 32
        val frames = (1..16).map { idx ->
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.rgb(100, 150, 200))
            PlanetaryFrame(
                index = idx,
                timestampMs = idx * 33L,
                bitmap = bmp,
                qualityScore = 0.85f - (idx * 0.02f),
                centroidX = 16f,
                centroidY = 16f
            )
        }

        val result = PlanetaryStacker.stackFrames(
            selectedFrames = frames,
            totalFramesCount = 20,
            method = StackingMethod.SIGMA_CLIPPED,
            align = true
        )

        assertNotNull(result.stackedBitmap)
        assertEquals(16, result.framesStacked)
        assertEquals(20, result.totalFrames)
        assertTrue("16 frames should yield ~12 dB SNR boost", result.snrBoostDb in 11.5f..12.5f)
    }

    @Test
    fun `wavelet processor processes contrast and ADC shift`() = runBlocking {
        val size = 48
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(200, 180, 150))

        val config = StackingConfig(
            waveletFine = 1.2f,
            waveletMedium = 0.6f,
            waveletCoarse = 0.3f,
            contrast = 1.2f,
            brightness = 1.0f,
            redShiftY = 1,
            blueShiftY = -1
        )

        val processed = WaveletProcessor.process(bmp, config)
        assertNotNull(processed)
        assertEquals(size, processed.width)
        assertEquals(size, processed.height)
    }

    @Test
    fun `frame quality analyzer scores sharp frame higher than blurry frame`() {
        val size = 64
        val centerX = 32
        val centerY = 32
        val radius = 20

        // Create blurry/flat planetary disc
        val blurryBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val blurryPixels = IntArray(size * size) { Color.BLACK }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy <= radius * radius) {
                    blurryPixels[y * size + x] = Color.rgb(160, 160, 160)
                }
            }
        }
        blurryBmp.setPixels(blurryPixels, 0, size, 0, 0, size, size)

        // Create sharp disc with high frequency alternating cloud bands
        val sharpBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val sharpPixels = IntArray(size * size) { Color.BLACK }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy <= radius * radius) {
                    val band = if ((y / 3) % 2 == 0) 240 else 70
                    sharpPixels[y * size + x] = Color.rgb(band, band, band)
                }
            }
        }
        sharpBmp.setPixels(sharpPixels, 0, size, 0, 0, size, size)

        val blurryMetrics = FrameQualityAnalyzer.analyzeSingleFrame(blurryBmp)
        val sharpMetrics = FrameQualityAnalyzer.analyzeSingleFrame(sharpBmp)

        assertTrue(
            "Sharp band pattern must yield higher raw sharpness (${sharpMetrics.rawSharpness} vs ${blurryMetrics.rawSharpness})",
            sharpMetrics.rawSharpness > blurryMetrics.rawSharpness
        )
        assertTrue(
            "Sharp band pattern must yield higher raw contrast (${sharpMetrics.rawContrast} vs ${blurryMetrics.rawContrast})",
            sharpMetrics.rawContrast > blurryMetrics.rawContrast
        )
    }

    @Test
    fun `frame quality analyzer penalizes atmospheric jitter offset`() {
        val size = 64
        val radius = 16
        val targetX = 32
        val targetY = 32

        val steadyBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { Color.BLACK }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - targetX
                val dy = y - targetY
                if (dx * dx + dy * dy <= radius * radius) {
                    pixels[y * size + x] = Color.rgb(200, 200, 200)
                }
            }
        }
        steadyBmp.setPixels(pixels, 0, size, 0, 0, size, size)

        // Reference centroid at target center (32, 32)
        val refCentroid = android.graphics.PointF(32f, 32f)
        val steadyMetrics = FrameQualityAnalyzer.analyzeSingleFrame(steadyBmp, referenceCentroid = refCentroid)

        // Jittered reference centroid (e.g. simulated 10px atmospheric wobble)
        val displacedRef = android.graphics.PointF(42f, 32f)
        val jitteredMetrics = FrameQualityAnalyzer.analyzeSingleFrame(steadyBmp, referenceCentroid = displacedRef)

        assertTrue(
            "Steady frame should have jitter offset near 0, was ${steadyMetrics.jitterOffsetPx}",
            steadyMetrics.jitterOffsetPx < 1.0f
        )
        assertTrue(
            "Displaced frame should have jitter offset around 10px, was ${jitteredMetrics.jitterOffsetPx}",
            jitteredMetrics.jitterOffsetPx in 9.0f..11.0f
        )
        assertTrue(
            "Steady frame must have higher jitter stability score (${steadyMetrics.jitterScore} vs ${jitteredMetrics.jitterScore})",
            steadyMetrics.jitterScore > jitteredMetrics.jitterScore
        )
    }

    @Test
    fun `frame quality analyzer batch analysis ranks frames and assigns composite scores`() = runBlocking {
        val size = 48
        val frames = (0 until 8).map { idx ->
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(size * size) { Color.BLACK }
            val radius = 14
            // Frame 0 is sharpest, subsequent frames have lower band contrast
            val contrastFactor = 1.0f - (idx * 0.1f)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val dx = x - 24
                    val dy = y - 24
                    if (dx * dx + dy * dy <= radius * radius) {
                        val base = 120
                        val band = if ((y / 2) % 2 == 0) (base + 80 * contrastFactor).toInt() else (base - 40 * contrastFactor).toInt()
                        pixels[y * size + x] = Color.rgb(band, band, band)
                    }
                }
            }
            bmp.setPixels(pixels, 0, size, 0, 0, size, size)
            bmp
        }

        val metricsList = FrameQualityAnalyzer.analyzeFrames(frames)
        assertEquals(8, metricsList.size)
        assertTrue(
            "First frame should have higher composite quality score than last frame (${metricsList.first().compositeQualityScore} vs ${metricsList.last().compositeQualityScore})",
            metricsList.first().compositeQualityScore > metricsList.last().compositeQualityScore
        )
        metricsList.forEach { metric ->
            assertTrue("Composite quality score must be in [0.0, 1.0]", metric.compositeQualityScore in 0.0f..1.0f)
            assertTrue("Sharpness score must be in [0.0, 1.0]", metric.sharpnessScore in 0.0f..1.0f)
            assertTrue("Contrast score must be in [0.0, 1.0]", metric.contrastScore in 0.0f..1.0f)
            assertTrue("Jitter stability must be in [0.0, 1.0]", metric.jitterScore in 0.0f..1.0f)
        }
    }

    @Test
    fun `sub pixel aligner detects disc core anchor and planetary surface features`() {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { Color.BLACK }
        val cx = 48
        val cy = 48
        val radius = 30

        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= radius * radius) {
                    val bandBrightness = if ((y / 4) % 2 == 0) 220 else 130
                    pixels[y * size + x] = Color.rgb(bandBrightness, bandBrightness, bandBrightness)
                }
            }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)

        val features = SubPixelAligner.detectPlanetaryFeatures(bmp, maxFeatures = 8)
        assertTrue("Features list must not be empty", features.isNotEmpty())
        assertEquals("First feature must be Disc Core Anchor", "Disc Core Anchor", features.first().label)
        assertTrue("Core anchor X should be near center (48), was ${features.first().x}", features.first().x in 45f..51f)
        assertTrue("Core anchor Y should be near center (48), was ${features.first().y}", features.first().y in 45f..51f)
    }

    @Test
    fun `warpSubPixel translates image smoothly by fractional offsets`() {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size) { Color.BLACK }

        // Draw a bright square at (20, 20) with size 10x10
        for (y in 20..30) {
            for (x in 20..30) {
                pixels[y * size + x] = Color.WHITE
            }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)

        // Shift by +2.5px in X and +1.5px in Y
        val shifted = SubPixelAligner.warpSubPixel(bmp, shiftX = 2.5f, shiftY = 1.5f)
        assertNotNull(shifted)
        assertEquals(size, shifted.width)
        assertEquals(size, shifted.height)

        val shiftedPixels = IntArray(size * size)
        shifted.getPixels(shiftedPixels, 0, size, 0, 0, size, size)

        // The center of the shifted square should now be at roughly (27.5, 26.5)
        val centerPixel = shiftedPixels[27 * size + 28]
        val r = Color.red(centerPixel)
        assertTrue("Shifted center pixel should have high brightness, was $r", r > 150)
    }

    @Test
    fun `sub pixel registration computes fractional offsets with high correlation`() {
        val size = 96
        val refBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(refBmp)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = 48f
        val cy = 48f
        val radius = 28f
        paint.color = Color.rgb(220, 200, 180)
        canvas.drawCircle(cx, cy, radius, paint)

        // Draw horizontal belts
        paint.color = Color.rgb(150, 90, 60)
        canvas.drawRect(cx - 24f, cy - 8f, cx + 24f, cy - 2f, paint)
        canvas.drawRect(cx - 24f, cy + 4f, cx + 24f, cy + 10f, paint)

        // Draw distinctive high-contrast 2D surface feature spot (e.g. Great Red Spot)
        paint.color = Color.rgb(200, 50, 40)
        canvas.drawCircle(cx + 8f, cy + 7f, 6f, paint)

        // Synthesize target frame shifted by +1.5px X and -0.8px Y
        val targetBmp = SubPixelAligner.warpSubPixel(refBmp, shiftX = 1.5f, shiftY = -0.8f)

        val features = SubPixelAligner.detectPlanetaryFeatures(refBmp)
        val reg = SubPixelAligner.registerFrameSubPixel(refBmp, targetBmp, features)

        assertTrue("Registration correlation score should be high, was ${reg.correlationScore}", reg.correlationScore > 0.70f)
        // Target was shifted by (+1.5, -0.8), so aligning target back to ref requires (-1.5, +0.8) shift
        assertTrue("SubShiftX should be within [-2.5, -0.5], was ${reg.subShiftX}", reg.subShiftX in -2.5f..-0.5f)
        assertTrue("SubShiftY should be within [0.2, 1.8], was ${reg.subShiftY}", reg.subShiftY in 0.2f..1.8f)
    }

    @Test
    fun `streaming accumulator stacks high frame counts exceeding 120 frames successfully`() = runBlocking {
        val size = 24
        val count = 140
        val frames = (1..count).map { idx ->
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.rgb(100, 150, 200))
            PlanetaryFrame(
                index = idx,
                timestampMs = idx * 33L,
                bitmap = bmp,
                qualityScore = 0.8f,
                centroidX = 12f,
                centroidY = 12f
            )
        }

        val result = PlanetaryStacker.stackFrames(
            selectedFrames = frames,
            totalFramesCount = count,
            method = StackingMethod.QUALITY_WEIGHTED,
            align = false
        )

        assertNotNull(result.stackedBitmap)
        assertEquals(count, result.framesStacked)
        assertTrue(result.snrBoostDb > 20f)
    }
}
