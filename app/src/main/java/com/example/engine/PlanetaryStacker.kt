package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.PlanetaryFrame
import com.example.data.model.StackingMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Astrophotography Planetary Stacking Engine.
 * Stacks aligned planetary frames to maximize Signal-to-Noise Ratio (SNR)
 * and reject atmospheric seeing artifacts.
 */
object PlanetaryStacker {

    data class StackingResult(
        val stackedBitmap: Bitmap,
        val framesStacked: Int,
        val totalFrames: Int,
        val snrBoostDb: Float,
        val avgQuality: Float
    )

    suspend fun stackFrames(
        selectedFrames: List<PlanetaryFrame>,
        totalFramesCount: Int,
        method: StackingMethod,
        align: Boolean,
        subPixelAlign: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): StackingResult = withContext(Dispatchers.Default) {
        val count = selectedFrames.size
        if (count == 0) throw IllegalArgumentException("No frames provided for stacking")

        val width = selectedFrames[0].bitmap.width
        val height = selectedFrames[0].bitmap.height

        // Prepare aligned frame bitmaps using either sub-pixel feature registration or integer centroid alignment
        val alignedBitmaps = if (align) {
            selectedFrames.map { frame ->
                if (subPixelAlign) {
                    SubPixelAligner.warpSubPixel(frame.bitmap, frame.subShiftX, frame.subShiftY)
                } else {
                    CentroidAligner.alignFrame(frame.bitmap, frame.shiftX, frame.shiftY)
                }
            }
        } else {
            selectedFrames.map { it.bitmap }
        }

        // Streaming accumulator branch for large frame counts (> 120 frames up to 5000)
        // Eliminates OOM risks by processing frames sequentially in O(1) buffer memory
        if (count > 120) {
            val weights = FloatArray(count)
            var sumWeights = 0f
            for (i in 0 until count) {
                val q = selectedFrames[i].qualityScore.coerceIn(0.01f, 1.0f)
                weights[i] = q * q
                sumWeights += weights[i]
            }

            val outPixels = IntArray(width * height)
            val rAcc = FloatArray(width * height)
            val gAcc = FloatArray(width * height)
            val bAcc = FloatArray(width * height)
            val frameBuf = IntArray(width * height)

            for (i in 0 until count) {
                alignedBitmaps[i].getPixels(frameBuf, 0, width, 0, 0, width, height)
                val w = if (method == StackingMethod.QUALITY_WEIGHTED) weights[i] else 1.0f
                for (p in 0 until width * height) {
                    val c = frameBuf[p]
                    rAcc[p] += ((c shr 16) and 0xFF) * w
                    gAcc[p] += ((c shr 8) and 0xFF) * w
                    bAcc[p] += (c and 0xFF) * w
                }
                if (i % 25 == 0 || i == count - 1) {
                    onProgress(0.2f + (i.toFloat() / count) * 0.7f)
                }
            }

            val divisor = if (method == StackingMethod.QUALITY_WEIGHTED && sumWeights > 0f) sumWeights else count.toFloat()
            for (p in 0 until width * height) {
                val r = (rAcc[p] / divisor).roundToInt().coerceIn(0, 255)
                val g = (gAcc[p] / divisor).roundToInt().coerceIn(0, 255)
                val b = (bAcc[p] / divisor).roundToInt().coerceIn(0, 255)
                outPixels[p] = Color.rgb(r, g, b)
            }

            val stackedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            stackedBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
            val snrBoostDb = (10.0 * log10(max(1.0, count.toDouble()))).toFloat()
            val avgQuality = selectedFrames.map { it.qualityScore }.average().toFloat()

            return@withContext StackingResult(
                stackedBitmap = stackedBitmap,
                framesStacked = count,
                totalFrames = totalFramesCount,
                snrBoostDb = snrBoostDb,
                avgQuality = avgQuality
            )
        }

        // Get pixel buffers for all frames
        val allFramePixels = Array(count) { i ->
            val buf = IntArray(width * height)
            alignedBitmaps[i].getPixels(buf, 0, width, 0, 0, width, height)
            buf
        }

        val weights = FloatArray(count)
        var sumWeights = 0f
        for (i in 0 until count) {
            val q = selectedFrames[i].qualityScore.coerceIn(0.01f, 1.0f)
            weights[i] = q * q // quadratic emphasis on sharper frames
            sumWeights += weights[i]
        }

        val outPixels = IntArray(width * height)

        // Process rows in parallel coroutines
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val rowsPerChunk = (height + numCores - 1) / numCores

        val deferreds = (0 until numCores).map { coreIdx ->
            async {
                val startY = coreIdx * rowsPerChunk
                val endY = min(height, startY + rowsPerChunk)
                val tempR = FloatArray(count)
                val tempG = FloatArray(count)
                val tempB = FloatArray(count)

                for (y in startY until endY) {
                    val rowOffset = y * width
                    for (x in 0 until width) {
                        val pixelIdx = rowOffset + x

                        when (method) {
                            StackingMethod.AVERAGE -> {
                                var rSum = 0f
                                var gSum = 0f
                                var bSum = 0f
                                for (k in 0 until count) {
                                    val c = allFramePixels[k][pixelIdx]
                                    rSum += (c shr 16) and 0xFF
                                    gSum += (c shr 8) and 0xFF
                                    bSum += c and 0xFF
                                }
                                val r = (rSum / count).roundToInt().coerceIn(0, 255)
                                val g = (gSum / count).roundToInt().coerceIn(0, 255)
                                val b = (bSum / count).roundToInt().coerceIn(0, 255)
                                outPixels[pixelIdx] = Color.rgb(r, g, b)
                            }

                            StackingMethod.QUALITY_WEIGHTED -> {
                                var rSum = 0f
                                var gSum = 0f
                                var bSum = 0f
                                for (k in 0 until count) {
                                    val c = allFramePixels[k][pixelIdx]
                                    val w = weights[k]
                                    rSum += ((c shr 16) and 0xFF) * w
                                    gSum += ((c shr 8) and 0xFF) * w
                                    bSum += (c and 0xFF) * w
                                }
                                val r = (rSum / sumWeights).roundToInt().coerceIn(0, 255)
                                val g = (gSum / sumWeights).roundToInt().coerceIn(0, 255)
                                val b = (bSum / sumWeights).roundToInt().coerceIn(0, 255)
                                outPixels[pixelIdx] = Color.rgb(r, g, b)
                            }

                            StackingMethod.MEDIAN -> {
                                for (k in 0 until count) {
                                    val c = allFramePixels[k][pixelIdx]
                                    tempR[k] = ((c shr 16) and 0xFF).toFloat()
                                    tempG[k] = ((c shr 8) and 0xFF).toFloat()
                                    tempB[k] = (c and 0xFF).toFloat()
                                }
                                tempR.sort()
                                tempG.sort()
                                tempB.sort()
                                val mid = count / 2
                                val r = tempR[mid].roundToInt().coerceIn(0, 255)
                                val g = tempG[mid].roundToInt().coerceIn(0, 255)
                                val b = tempB[mid].roundToInt().coerceIn(0, 255)
                                outPixels[pixelIdx] = Color.rgb(r, g, b)
                            }

                            StackingMethod.SIGMA_CLIPPED -> {
                                // Calculate mean and standard deviation, reject outliers > 1.8 sigma
                                var rSum = 0.0
                                var gSum = 0.0
                                var bSum = 0.0
                                for (k in 0 until count) {
                                    val c = allFramePixels[k][pixelIdx]
                                    val r = ((c shr 16) and 0xFF).toFloat()
                                    val g = ((c shr 8) and 0xFF).toFloat()
                                    val b = (c and 0xFF).toFloat()
                                    tempR[k] = r
                                    tempG[k] = g
                                    tempB[k] = b
                                    rSum += r
                                    gSum += g
                                    bSum += b
                                }
                                val meanR = rSum / count
                                val meanG = gSum / count
                                val meanB = bSum / count

                                var varR = 0.0
                                var varG = 0.0
                                var varB = 0.0
                                for (k in 0 until count) {
                                    val dr = tempR[k] - meanR
                                    val dg = tempG[k] - meanG
                                    val db = tempB[k] - meanB
                                    varR += dr * dr
                                    varG += dg * dg
                                    varB += db * db
                                }
                                val sigmaR = sqrt(varR / count) * 1.8
                                val sigmaG = sqrt(varG / count) * 1.8
                                val sigmaB = sqrt(varB / count) * 1.8

                                var finalRSum = 0.0
                                var finalRCount = 0
                                var finalGSum = 0.0
                                var finalGCount = 0
                                var finalBSum = 0.0
                                var finalBCount = 0

                                for (k in 0 until count) {
                                    val r = tempR[k]
                                    if (kotlin.math.abs(r - meanR) <= sigmaR || sigmaR < 2.0) {
                                        finalRSum += r
                                        finalRCount++
                                    }
                                    val g = tempG[k]
                                    if (kotlin.math.abs(g - meanG) <= sigmaG || sigmaG < 2.0) {
                                        finalGSum += g
                                        finalGCount++
                                    }
                                    val b = tempB[k]
                                    if (kotlin.math.abs(b - meanB) <= sigmaB || sigmaB < 2.0) {
                                        finalBSum += b
                                        finalBCount++
                                    }
                                }

                                val r = (if (finalRCount > 0) finalRSum / finalRCount else meanR).roundToInt().coerceIn(0, 255)
                                val g = (if (finalGCount > 0) finalGSum / finalGCount else meanG).roundToInt().coerceIn(0, 255)
                                val b = (if (finalBCount > 0) finalBSum / finalBCount else meanB).roundToInt().coerceIn(0, 255)
                                outPixels[pixelIdx] = Color.rgb(r, g, b)
                            }
                        }
                    }
                }
            }
        }
        deferreds.awaitAll()

        val stackedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        stackedBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)

        // Calculate theoretical SNR improvement: 10 * log10(N) dB
        val snrBoostDb = (10.0 * log10(max(1.0, count.toDouble()))).toFloat()
        val avgQuality = selectedFrames.map { it.qualityScore }.average().toFloat()

        StackingResult(
            stackedBitmap = stackedBitmap,
            framesStacked = count,
            totalFrames = totalFramesCount,
            snrBoostDb = snrBoostDb,
            avgQuality = avgQuality
        )
    }
}
