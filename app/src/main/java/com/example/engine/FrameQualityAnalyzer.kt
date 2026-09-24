package com.example.engine

import android.graphics.Bitmap
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Result of comprehensive planetary quality analysis for a single frame.
 */
data class FrameQualityMetrics(
    val frameIndex: Int,
    val sharpnessScore: Float,      // 0.0 to 1.0: high-frequency spatial edge power & detail
    val contrastScore: Float,       // 0.0 to 1.0: planetary disc tonal range & feature dynamic range
    val jitterScore: Float,         // 0.0 to 1.0: atmospheric stability (1.0 = rock solid, 0.0 = violent jitter)
    val jitterOffsetPx: Float,      // Atmospheric jitter displacement in pixels from reference/trend
    val compositeQualityScore: Float,// 0.0 to 1.0: weighted planetary integration score
    val centroid: PointF,           // Planetary disc center of mass
    val discRadiusEstimate: Float,  // Estimated radius of planetary disc in pixels
    val rawSharpness: Double,
    val rawContrast: Double
)

/**
 * Service contract for planetary frame quality analysis.
 */
interface FrameQualityAnalysisService {
    /**
     * Analyzes a single frame given an optional reference centroid or expected disc position.
     */
    fun analyzeSingleFrame(
        bitmap: Bitmap,
        frameIndex: Int = 0,
        referenceCentroid: PointF? = null
    ): FrameQualityMetrics

    /**
     * Batch analyzes a sequence of planetary video frames, computing atmospheric jitter
     * relative to the smoothed telescope tracking trajectory and assigning normalized scores.
     */
    suspend fun analyzeFrames(
        frames: List<Bitmap>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<FrameQualityMetrics>
}

/**
 * Service and utility to analyze individual video frames for:
 * 1. Sharpness (Laplacian variance + discrete high-pass spatial edge energy on the planetary disc)
 * 2. Contrast (RMS luminance standard deviation and feature dynamic range across the disc)
 * 3. Atmospheric Jitter (Wobble displacement from smoothed centroid path and limb deformation)
 * 
 * Assigns an overall composite quality score optimized for planetary frame integration.
 */
object FrameQualityAnalyzer : FrameQualityAnalysisService {

    // Weighting configuration for planetary composite score
    private const val WEIGHT_SHARPNESS = 0.55f
    private const val WEIGHT_CONTRAST = 0.25f
    private const val WEIGHT_JITTER_STABILITY = 0.20f

    override fun analyzeSingleFrame(
        bitmap: Bitmap,
        frameIndex: Int,
        referenceCentroid: PointF?
    ): FrameQualityMetrics {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val lumaData = extractLumaAndCentroid(pixels, width, height)
        val centroid = lumaData.centroid
        val discRadius = lumaData.radiusEstimate

        val rawSharpness = computeDiscSharpness(lumaData, width, height)
        val rawContrast = computeDiscContrast(lumaData)

        // Calculate jitter relative to reference centroid if supplied
        val jitterOffset = if (referenceCentroid != null) {
            val dx = centroid.x - referenceCentroid.x
            val dy = centroid.y - referenceCentroid.y
            sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        } else {
            0f
        }

        // Stability decay factor: jitter of >6-8px reduces stability significantly
        val jitterStability = exp(-jitterOffset / max(5f, discRadius * 0.15f))

        // Initial single-frame normalization
        val normSharpness = (1.0 - exp(-rawSharpness / 30.0)).toFloat().coerceIn(0.1f, 1.0f)
        val normContrast = (rawContrast / 80.0).toFloat().coerceIn(0.1f, 1.0f)
        val composite = (
            WEIGHT_SHARPNESS * normSharpness +
            WEIGHT_CONTRAST * normContrast +
            WEIGHT_JITTER_STABILITY * jitterStability
        ).coerceIn(0.05f, 1.0f)

        return FrameQualityMetrics(
            frameIndex = frameIndex,
            sharpnessScore = normSharpness,
            contrastScore = normContrast,
            jitterScore = jitterStability,
            jitterOffsetPx = jitterOffset,
            compositeQualityScore = composite,
            centroid = centroid,
            discRadiusEstimate = discRadius,
            rawSharpness = rawSharpness,
            rawContrast = rawContrast
        )
    }

    override suspend fun analyzeFrames(
        frames: List<Bitmap>,
        onProgress: (Int, Int) -> Unit
    ): List<FrameQualityMetrics> = withContext(Dispatchers.Default) {
        val total = frames.size
        if (total == 0) return@withContext emptyList()

        // 1. Parallel extraction of luma, centroids, raw sharpness & contrast for each frame
        data class IntermediateFrameData(
            val index: Int,
            val centroid: PointF,
            val radiusEstimate: Float,
            val rawSharpness: Double,
            val rawContrast: Double
        )

        val intermediate = ArrayList<IntermediateFrameData>(total)
        val batchSize = 32
        val chunks = frames.chunked(batchSize)
        var processedCount = 0

        for (chunk in chunks) {
            val chunkResults = chunk.mapIndexed { chunkIdx, bitmap ->
                val globalIdx = processedCount + chunkIdx
                async {
                    val width = bitmap.width
                    val height = bitmap.height
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                    val lumaData = extractLumaAndCentroid(pixels, width, height)
                    val sharpness = computeDiscSharpness(lumaData, width, height)
                    val contrast = computeDiscContrast(lumaData)

                    IntermediateFrameData(
                        index = globalIdx,
                        centroid = lumaData.centroid,
                        radiusEstimate = lumaData.radiusEstimate,
                        rawSharpness = sharpness,
                        rawContrast = contrast
                    )
                }
            }.awaitAll()
            intermediate.addAll(chunkResults)
            processedCount += chunk.size
            onProgress(processedCount, total)
        }

        // 2. Compute smoothed centroid trajectory across the capture session
        // Atmospheric jitter is high-frequency displacement deviating from smooth mount tracking drift
        val windowSize = min(7, total)
        val halfWindow = windowSize / 2
        val smoothedCentroids = intermediate.indices.map { i ->
            val start = max(0, i - halfWindow)
            val end = min(total - 1, i + halfWindow)
            var sumX = 0.0
            var sumY = 0.0
            var count = 0
            for (w in start..end) {
                sumX += intermediate[w].centroid.x
                sumY += intermediate[w].centroid.y
                count++
            }
            PointF((sumX / count).toFloat(), (sumY / count).toFloat())
        }

        // Calculate jitter offsets relative to smooth trajectory
        val jitterOffsets = intermediate.indices.map { i ->
            val actual = intermediate[i].centroid
            val smooth = smoothedCentroids[i]
            val dx = actual.x - smooth.x
            val dy = actual.y - smooth.y
            sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }

        // 3. Normalize individual metrics across the full frame collection
        val minSharp = intermediate.minOfOrNull { it.rawSharpness } ?: 0.0
        val maxSharp = intermediate.maxOfOrNull { it.rawSharpness } ?: 1.0
        val sharpRange = (maxSharp - minSharp).coerceAtLeast(1e-5)

        val minContrast = intermediate.minOfOrNull { it.rawContrast } ?: 0.0
        val maxContrast = intermediate.maxOfOrNull { it.rawContrast } ?: 1.0
        val contrastRange = (maxContrast - minContrast).coerceAtLeast(1e-5)

        val avgRadius = intermediate.map { it.radiusEstimate }.average().toFloat().coerceAtLeast(15f)
        val jitterScale = max(3f, avgRadius * 0.12f)

        intermediate.mapIndexed { i, data ->
            // Sharpness: 0.15 - 1.0 with non-linear boost for the very sharpest seeing moments
            val normSharp = ((data.rawSharpness - minSharp) / sharpRange).toFloat()
            val finalSharpness = (0.20f + 0.80f * (normSharp * normSharp)).coerceIn(0.15f, 1.0f)

            // Contrast: 0.20 - 1.0
            val normCont = ((data.rawContrast - minContrast) / contrastRange).toFloat()
            val finalContrast = (0.25f + 0.75f * normCont).coerceIn(0.20f, 1.0f)

            // Jitter stability: lower offset -> higher stability score
            val offset = jitterOffsets[i]
            val jitterStability = exp(-offset / jitterScale).coerceIn(0.1f, 1.0f)

            // Combined frame quality score
            val composite = (
                WEIGHT_SHARPNESS * finalSharpness +
                WEIGHT_CONTRAST * finalContrast +
                WEIGHT_JITTER_STABILITY * jitterStability
            ).coerceIn(0.15f, 1.0f)

            FrameQualityMetrics(
                frameIndex = i,
                sharpnessScore = finalSharpness,
                contrastScore = finalContrast,
                jitterScore = jitterStability,
                jitterOffsetPx = offset,
                compositeQualityScore = composite,
                centroid = data.centroid,
                discRadiusEstimate = data.radiusEstimate,
                rawSharpness = data.rawSharpness,
                rawContrast = data.rawContrast
            )
        }
    }

    /**
     * Preprocessed luma buffer and planetary disc metadata.
     */
    private data class LumaAnalysisData(
        val luma: FloatArray,
        val backgroundThreshold: Float,
        val centroid: PointF,
        val radiusEstimate: Float,
        val diskIndices: IntArray
    )

    private fun extractLumaAndCentroid(
        pixels: IntArray,
        width: Int,
        height: Int
    ): LumaAnalysisData {
        val total = pixels.size
        val luma = FloatArray(total)
        var maxLuma = 0f

        for (i in 0 until total) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // ITU-R BT.601 perceptual luminance
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            luma[i] = y
            if (y > maxLuma) maxLuma = y
        }

        val backgroundThreshold = max(14f, maxLuma * 0.12f)

        // Compute Center of Gravity (Centroid) across the disc
        var sumWeight = 0.0
        var sumX = 0.0
        var sumY = 0.0
        val diskList = ArrayList<Int>(total / 4)

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val idx = row + x
                val v = luma[idx]
                if (v >= backgroundThreshold) {
                    val weight = (v - backgroundThreshold).toDouble()
                    sumWeight += weight
                    sumX += x * weight
                    sumY += y * weight
                    diskList.add(idx)
                }
            }
        }

        val cx = if (sumWeight > 0.0) (sumX / sumWeight).toFloat() else (width / 2f)
        val cy = if (sumWeight > 0.0) (sumY / sumWeight).toFloat() else (height / 2f)

        // Disc radius estimate from disc pixel area: Area ≈ π r² -> r ≈ √(Area / π)
        val area = diskList.size.toFloat()
        val radius = sqrt(max(4f, area / Math.PI.toFloat()))

        return LumaAnalysisData(
            luma = luma,
            backgroundThreshold = backgroundThreshold,
            centroid = PointF(cx, cy),
            radiusEstimate = radius,
            diskIndices = diskList.toIntArray()
        )
    }

    /**
     * Sharpness analyzer: Computes high-frequency spatial edge power using 2D discrete Laplacian
     * and gradient energy restricted to planetary disk features.
     */
    private fun computeDiscSharpness(data: LumaAnalysisData, width: Int, height: Int): Double {
        val luma = data.luma
        val threshold = data.backgroundThreshold
        val step = if (width > 640) 2 else 1

        var sumSq = 0.0
        var count = 0

        for (y in 1 until height - 1 step step) {
            val row = y * width
            val rowUp = (y - 1) * width
            val rowDown = (y + 1) * width

            for (x in 1 until width - 1 step step) {
                val center = luma[row + x]
                if (center >= threshold) {
                    // Laplacian operator (sum of 2nd spatial derivatives)
                    val laplacian = (
                        luma[rowUp + x] +
                        luma[rowDown + x] +
                        luma[row + x - 1] +
                        luma[row + x + 1] -
                        4f * center
                    ).toDouble()

                    // Horizontal & Vertical gradient difference
                    val dx = (luma[row + x + 1] - luma[row + x - 1]).toDouble()
                    val dy = (luma[rowDown + x] - luma[rowUp + x]).toDouble()
                    val gradSq = (dx * dx + dy * dy) * 0.25

                    sumSq += (laplacian * laplacian) + gradSq
                    count++
                }
            }
        }

        if (count < 10) return 0.0
        // Root-mean-square spatial high frequency edge energy
        return sqrt(sumSq / count)
    }

    /**
     * Contrast analyzer: Computes RMS luminance standard deviation and tonal range
     * over the planetary surface (ignoring the black sky background).
     */
    private fun computeDiscContrast(data: LumaAnalysisData): Double {
        val indices = data.diskIndices
        val count = indices.size
        if (count < 10) return 0.0

        val luma = data.luma
        var sum = 0.0
        var sumSq = 0.0

        for (i in 0 until count) {
            val v = luma[indices[i]].toDouble()
            sum += v
            sumSq += v * v
        }

        val mean = sum / count
        val variance = max(0.0, (sumSq / count) - (mean * mean))
        val rmsContrast = sqrt(variance)

        return rmsContrast
    }
}
