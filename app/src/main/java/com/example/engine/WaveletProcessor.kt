package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.SharpeningMode
import com.example.data.model.StackingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Astrophotography Wavelet, Sharpening & Post-Processing Suite.
 * Provides multi-scale frequency detail extraction (RegiStax-style),
 * dedicated Unsharp Mask (USM) sharpening with noise suppression thresholding,
 * Laplacian 3x3 high-pass micro-edge filtering,
 * Atmospheric Dispersion Correction (ADC RGB channel shift),
 * and contrast/gamma curve adjustments.
 */
object WaveletProcessor {

    suspend fun process(
        baseBitmap: Bitmap,
        config: StackingConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = baseBitmap.width
        val height = baseBitmap.height

        // 1. First apply Atmospheric Dispersion Corrector (ADC RGB shift) if requested
        val adcBitmap = if (config.redShiftX != 0 || config.redShiftY != 0 ||
            config.blueShiftX != 0 || config.blueShiftY != 0
        ) {
            applyAdcShift(baseBitmap, config.redShiftX, config.redShiftY, config.blueShiftX, config.blueShiftY)
        } else {
            baseBitmap
        }

        // 2. Apply Sharpening Tool (Unsharp Mask and/or Laplacian Micro-Edge)
        val sharpenedBitmap = applySharpening(adcBitmap, config)

        // 3. Multi-scale wavelets (unsharp masks)
        val w1 = config.waveletFine      // Fine scale (radius 1)
        val w2 = config.waveletMedium    // Medium scale (radius 2)
        val w3 = config.waveletCoarse    // Coarse scale (radius 4)

        val srcPixels = IntArray(width * height)
        sharpenedBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // Generate blurred layers for wavelet bandpass decomposition
        // Layer 1 blur (small 3x3 box blur / gaussian approximation)
        val blur1 = boxBlur(srcPixels, width, height, radius = 1)
        // Layer 2 blur (radius 3)
        val blur2 = boxBlur(blur1, width, height, radius = 2)
        // Layer 3 blur (radius 5)
        val blur3 = boxBlur(blur2, width, height, radius = 3)

        val outPixels = IntArray(width * height)

        val contrast = config.contrast
        val brightness = config.brightness
        val saturation = config.saturation
        val gamma = config.gamma.coerceIn(0.5f, 2.0f)
        val invGamma = 1.0f / gamma

        for (i in srcPixels.indices) {
            val c0 = srcPixels[i]
            val c1 = blur1[i]
            val c2 = blur2[i]
            val c3 = blur3[i]

            val r0 = (c0 shr 16) and 0xFF
            val g0 = (c0 shr 8) and 0xFF
            val b0 = c0 and 0xFF

            val r1 = (c1 shr 16) and 0xFF
            val g1 = (c1 shr 8) and 0xFF
            val b1 = c1 and 0xFF

            val r2 = (c2 shr 16) and 0xFF
            val g2 = (c2 shr 8) and 0xFF
            val b2 = c2 and 0xFF

            val r3 = (c3 shr 16) and 0xFF
            val g3 = (c3 shr 8) and 0xFF
            val b3 = c3 and 0xFF

            // Wavelet detail layers (difference of Gaussians)
            val d1R = r0 - r1
            val d1G = g0 - g1
            val d1B = b0 - b1

            val d2R = r1 - r2
            val d2G = g1 - g2
            val d2B = b1 - b2

            val d3R = r2 - r3
            val d3G = g2 - g3
            val d3B = b2 - b3

            // Combine sharpened details
            var r = r0 + w1 * d1R + w2 * d2R + w3 * d3R
            var g = g0 + w1 * d1G + w2 * d2G + w3 * d3G
            var b = b0 + w1 * d1B + w2 * d2B + w3 * d3B

            // Tone adjustments: Brightness & Contrast
            r = ((r - 128f) * contrast + 128f) * brightness
            g = ((g - 128f) * contrast + 128f) * brightness
            b = ((b - 128f) * contrast + 128f) * brightness

            // Saturation adjustment
            if (saturation != 1.0f) {
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                r = lum + (r - lum) * saturation
                g = lum + (g - lum) * saturation
                b = lum + (b - lum) * saturation
            }

            // Gamma curve
            if (gamma != 1.0f) {
                r = (255f * (r.coerceIn(0f, 255f) / 255f).pow(invGamma))
                g = (255f * (g.coerceIn(0f, 255f) / 255f).pow(invGamma))
                b = (255f * (b.coerceIn(0f, 255f) / 255f).pow(invGamma))
            }

            val finalR = r.roundToInt().coerceIn(0, 255)
            val finalG = g.roundToInt().coerceIn(0, 255)
            val finalB = b.roundToInt().coerceIn(0, 255)

            outPixels[i] = Color.rgb(finalR, finalG, finalB)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        result
    }

    /**
     * Atmospheric Dispersion Corrector (ADC):
     * Offsets the Red and Blue channels independently relative to Green to eliminate color fringing
     * caused by Earth's atmospheric refraction.
     */
    fun applyAdcShift(
        bitmap: Bitmap,
        rx: Int,
        ry: Int,
        bx: Int,
        by: Int
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val src = IntArray(width * height)
        val dst = IntArray(width * height)
        bitmap.getPixels(src, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val rSrcY = (y - ry).coerceIn(0, height - 1)
            val bSrcY = (y - by).coerceIn(0, height - 1)
            val dstRow = y * width
            val gRow = y * width
            val rRow = rSrcY * width
            val bRow = bSrcY * width

            for (x in 0 until width) {
                val rSrcX = (x - rx).coerceIn(0, width - 1)
                val bSrcX = (x - bx).coerceIn(0, width - 1)

                val rColor = src[rRow + rSrcX]
                val gColor = src[gRow + x]
                val bColor = src[bRow + bSrcX]

                val r = (rColor shr 16) and 0xFF
                val g = (gColor shr 8) and 0xFF
                val b = bColor and 0xFF

                dst[dstRow + x] = Color.rgb(r, g, b)
            }
        }

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(dst, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Sharpening Tool:
     * Dispatches Unsharp Mask (USM), Laplacian Micro-Edge, or Hybrid Combination based on StackingConfig.
     */
    fun applySharpening(
        bitmap: Bitmap,
        config: StackingConfig
    ): Bitmap {
        val usmAmount = config.unsharpMaskAmount
        val laplacianAmount = config.laplacianSharpening
        if (usmAmount <= 0.01f && laplacianAmount <= 0.01f) {
            return bitmap
        }

        return when (config.sharpeningMode) {
            SharpeningMode.UNSHARP_MASK -> {
                if (usmAmount > 0.01f) {
                    applyUnsharpMask(bitmap, usmAmount, config.unsharpMaskRadius, config.unsharpMaskThreshold)
                } else bitmap
            }
            SharpeningMode.LAPLACIAN -> {
                if (laplacianAmount > 0.01f) {
                    applyLaplacianSharpening(bitmap, laplacianAmount)
                } else bitmap
            }
            SharpeningMode.COMBINED -> {
                var current = bitmap
                if (usmAmount > 0.01f) {
                    current = applyUnsharpMask(current, usmAmount, config.unsharpMaskRadius, config.unsharpMaskThreshold)
                }
                if (laplacianAmount > 0.01f) {
                    current = applyLaplacianSharpening(current, laplacianAmount)
                }
                current
            }
        }
    }

    /**
     * Unsharp Mask (USM) sharpening tool:
     * Creates a high-pass frequency difference signal between original and blurred copy,
     * suppressing noise amplification via tonal delta thresholding.
     */
    fun applyUnsharpMask(
        bitmap: Bitmap,
        amount: Float,
        radius: Float,
        threshold: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val srcPixels = IntArray(width * height)
        bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val intRadius = radius.roundToInt().coerceIn(1, 10)
        val blurredPixels = boxBlur(srcPixels, width, height, intRadius)
        val outPixels = IntArray(width * height)

        val thresh255 = threshold * 255f

        for (i in srcPixels.indices) {
            val orig = srcPixels[i]
            val blur = blurredPixels[i]

            val r0 = (orig shr 16) and 0xFF
            val g0 = (orig shr 8) and 0xFF
            val b0 = orig and 0xFF

            val rB = (blur shr 16) and 0xFF
            val gB = (blur shr 8) and 0xFF
            val bB = blur and 0xFF

            val diffR = r0 - rB
            val diffG = g0 - gB
            val diffB = b0 - bB

            // Suppress noise if difference is below threshold
            val boostR = if (abs(diffR) >= thresh255) diffR * amount else 0f
            val boostG = if (abs(diffG) >= thresh255) diffG * amount else 0f
            val boostB = if (abs(diffB) >= thresh255) diffB * amount else 0f

            val finalR = (r0 + boostR).roundToInt().coerceIn(0, 255)
            val finalG = (g0 + boostG).roundToInt().coerceIn(0, 255)
            val finalB = (b0 + boostB).roundToInt().coerceIn(0, 255)

            outPixels[i] = Color.rgb(finalR, finalG, finalB)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Laplacian 3x3 Micro-Edge Sharpening:
     * Discrete 3x3 Laplacian second-derivative high-pass filter:
     * [  0, -1,  0 ]
     * [ -1,  4, -1 ]
     * [  0, -1,  0 ]
     * Accentuates sub-pixel surface features such as Jupiter's belts, Saturn's rings, and lunar crater walls.
     */
    fun applyLaplacianSharpening(
        bitmap: Bitmap,
        strength: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val src = IntArray(width * height)
        val dst = IntArray(width * height)
        bitmap.getPixels(src, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val ym1 = (y - 1).coerceIn(0, height - 1) * width
            val y0 = y * width
            val yp1 = (y + 1).coerceIn(0, height - 1) * width

            for (x in 0 until width) {
                val xm1 = (x - 1).coerceIn(0, width - 1)
                val xp1 = (x + 1).coerceIn(0, width - 1)

                val cCenter = src[y0 + x]
                val cTop = src[ym1 + x]
                val cBottom = src[yp1 + x]
                val cLeft = src[y0 + xm1]
                val cRight = src[y0 + xp1]

                val rCenter = (cCenter shr 16) and 0xFF
                val gCenter = (cCenter shr 8) and 0xFF
                val bCenter = cCenter and 0xFF

                val rTop = (cTop shr 16) and 0xFF
                val gTop = (cTop shr 8) and 0xFF
                val bTop = cTop and 0xFF

                val rBottom = (cBottom shr 16) and 0xFF
                val gBottom = (cBottom shr 8) and 0xFF
                val bBottom = cBottom and 0xFF

                val rLeft = (cLeft shr 16) and 0xFF
                val gLeft = (cLeft shr 8) and 0xFF
                val bLeft = cLeft and 0xFF

                val rRight = (cRight shr 16) and 0xFF
                val gRight = (cRight shr 8) and 0xFF
                val bRight = cRight and 0xFF

                // 3x3 Laplacian edge response
                val lapR = 4 * rCenter - (rTop + rBottom + rLeft + rRight)
                val lapG = 4 * gCenter - (gTop + gBottom + gLeft + gRight)
                val lapB = 4 * bCenter - (bTop + bBottom + bLeft + bRight)

                val newR = (rCenter + strength * lapR).roundToInt().coerceIn(0, 255)
                val newG = (gCenter + strength * lapG).roundToInt().coerceIn(0, 255)
                val newB = (bCenter + strength * lapB).roundToInt().coerceIn(0, 255)

                dst[y0 + x] = Color.rgb(newR, newG, newB)
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(dst, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Fast separable box blur for wavelet approximation.
     */
    private fun boxBlur(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0) return src.clone()
        val temp = IntArray(width * height)
        val dst = IntArray(width * height)
        val div = 2 * radius + 1

        // Horizontal pass
        for (y in 0 until height) {
            val row = y * width
            var rSum = 0
            var gSum = 0
            var bSum = 0

            // Initialize window
            for (i in -radius..radius) {
                val x = i.coerceIn(0, width - 1)
                val c = src[row + x]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }

            for (x in 0 until width) {
                temp[row + x] = Color.rgb(rSum / div, gSum / div, bSum / div)

                val xRemove = (x - radius).coerceIn(0, width - 1)
                val xAdd = (x + radius + 1).coerceIn(0, width - 1)
                val cRem = src[row + xRemove]
                val cAdd = src[row + xAdd]

                rSum += ((cAdd shr 16) and 0xFF) - ((cRem shr 16) and 0xFF)
                gSum += ((cAdd shr 8) and 0xFF) - ((cRem shr 8) and 0xFF)
                bSum += (cAdd and 0xFF) - (cRem and 0xFF)
            }
        }

        // Vertical pass
        for (x in 0 until width) {
            var rSum = 0
            var gSum = 0
            var bSum = 0

            for (i in -radius..radius) {
                val y = i.coerceIn(0, height - 1)
                val c = temp[y * width + x]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }

            for (y in 0 until height) {
                dst[y * width + x] = Color.rgb(rSum / div, gSum / div, bSum / div)

                val yRemove = (y - radius).coerceIn(0, height - 1)
                val yAdd = (y + radius + 1).coerceIn(0, height - 1)
                val cRem = temp[yRemove * width + x]
                val cAdd = temp[yAdd * width + x]

                rSum += ((cAdd shr 16) and 0xFF) - ((cRem shr 16) and 0xFF)
                gSum += ((cAdd shr 8) and 0xFF) - ((cRem shr 8) and 0xFF)
                bSum += (cAdd and 0xFF) - (cRem and 0xFF)
            }
        }

        return dst
    }
}
