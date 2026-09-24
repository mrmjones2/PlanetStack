package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Planetary Centroid & Center of Gravity Alignment Engine.
 * Stabilizes drifting planetary discs across video frames prior to planetary stacking.
 */
object CentroidAligner {

    data class Centroid(val x: Float, val y: Float, val totalMass: Float)

    /**
     * Finds the center of brightness (planetary centroid) for a frame.
     */
    fun computeCentroid(bitmap: Bitmap): Centroid {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var maxLuma = 0f
        val lumas = FloatArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            lumas[i] = l
            if (l > maxLuma) maxLuma = l
        }

        // Noise floor: ignore background sky
        val threshold = max(12f, maxLuma * 0.15f)

        var sumWeight = 0.0
        var sumX = 0.0
        var sumY = 0.0

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val weight = lumas[row + x]
                if (weight > threshold) {
                    val w = (weight - threshold).toDouble()
                    sumWeight += w
                    sumX += x * w
                    sumY += y * w
                }
            }
        }

        if (sumWeight <= 0.0) {
            return Centroid(width / 2f, height / 2f, 0f)
        }

        return Centroid(
            x = (sumX / sumWeight).toFloat(),
            y = (sumY / sumWeight).toFloat(),
            totalMass = sumWeight.toFloat()
        )
    }

    /**
     * Translates a frame by (shiftX, shiftY) so that its centroid aligns with the reference frame.
     */
    fun alignFrame(
        frame: Bitmap,
        shiftX: Int,
        shiftY: Int
    ): Bitmap {
        if (shiftX == 0 && shiftY == 0) return frame

        val width = frame.width
        val height = frame.height
        val srcPixels = IntArray(width * height)
        val dstPixels = IntArray(width * height)
        frame.getPixels(srcPixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val srcY = y - shiftY
            if (srcY in 0 until height) {
                val dstRow = y * width
                val srcRow = srcY * width
                for (x in 0 until width) {
                    val srcX = x - shiftX
                    if (srcX in 0 until width) {
                        dstPixels[dstRow + x] = srcPixels[srcRow + srcX]
                    } else {
                        dstPixels[dstRow + x] = Color.BLACK
                    }
                }
            } else {
                val dstRow = y * width
                for (x in 0 until width) {
                    dstPixels[dstRow + x] = Color.BLACK
                }
            }
        }

        val alignedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        alignedBitmap.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return alignedBitmap
    }
}
