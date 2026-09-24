package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Planetary Seeing / Atmospheric Turbulence Scorer.
 * Implements planetary image quality grading using Laplacian variance and
 * gradient sharpness on the planetary disk.
 */
object SeeingScorer {

    suspend fun gradeFrames(
        frames: List<Bitmap>,
        onProgress: (Int, Int) -> Unit
    ): List<Float> = withContext(Dispatchers.Default) {
        val total = frames.size
        if (total == 0) return@withContext emptyList()

        // Score frames in parallel chunks for fast performance
        val rawScores = frames.mapIndexed { index, bitmap ->
            async {
                val score = computeLaplacianVariance(bitmap)
                onProgress(index + 1, total)
                score
            }
        }.awaitAll()

        // Normalize scores to 0.0 - 1.0 (with 1.0 being the best frame)
        val minScore = rawScores.minOrNull() ?: 0.0
        val maxScore = rawScores.maxOrNull() ?: 1.0
        val range = (maxScore - minScore).coerceAtLeast(1e-6)

        rawScores.map { raw ->
            // Quality scale: map to 0.15 - 0.99 range so even lowest frame has relative score
            val normalized = ((raw - minScore) / range).toFloat()
            (0.20f + 0.79f * (normalized * normalized)) // Non-linear response highlighting the best frames
        }
    }

    /**
     * Computes the variance of the discrete 2D Laplacian operator over the planetary disc.
     */
    fun computeLaplacianVariance(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to luminance buffer (0-255)
        val luma = FloatArray(width * height)
        var maxLuma = 0f
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // Perceptual luminance
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            luma[i] = y
            if (y > maxLuma) maxLuma = y
        }

        // Planetary threshold: ignore the black space background to avoid scoring empty space
        val backgroundThreshold = max(15f, maxLuma * 0.12f)

        // Discrete Laplacian kernel:
        //  0  1  0
        //  1 -4  1
        //  0  1  0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        // Subsample for efficiency if large frame
        val step = if (width > 640) 2 else 1

        for (y in 1 until height - 1 step step) {
            val row = y * width
            val rowUp = (y - 1) * width
            val rowDown = (y + 1) * width

            for (x in 1 until width - 1 step step) {
                val center = luma[row + x]
                // Only evaluate on the planet disk or nearby boundary
                if (center >= backgroundThreshold) {
                    val laplacian = (
                        luma[rowUp + x] +
                        luma[rowDown + x] +
                        luma[row + x - 1] +
                        luma[row + x + 1] -
                        4f * center
                    ).toDouble()

                    sum += laplacian
                    sumSq += laplacian * laplacian
                    count++
                }
            }
        }

        if (count < 10) return 0.0
        // Root-mean-square (RMS) Laplacian energy:
        // Measures high-frequency spatial edge power on the planetary disk
        val rmsEnergy = sqrt(sumSq / count)
        return rmsEnergy
    }
}
