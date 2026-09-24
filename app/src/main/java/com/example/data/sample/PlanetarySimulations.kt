package com.example.data.sample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.engine.AviParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Random
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates scientifically modeled planetary capture sessions with realistic atmospheric seeing,
 * mount tracking drift, photon noise, and planetary surface details.
 * Also generates actual RIFF AVI video files on device storage for testing.
 */
object PlanetarySimulations {

    enum class CelestialTarget(
        val displayName: String,
        val subtitle: String,
        val frameCount: Int,
        val defaultFormat: AviParser.AviColorFormat = AviParser.AviColorFormat.RGB_8BIT_24BPP
    ) {
        JUPITER("Jupiter & Great Red Spot", "Equatorial bands & GRS • RGB 8-bit (24bpp DIB)", 60, AviParser.AviColorFormat.RGB_8BIT_24BPP),
        SATURN("Saturn & Cassini Division", "Rings & shadow • RGB 8-bit (24bpp DIB)", 60, AviParser.AviColorFormat.RGB_8BIT_24BPP),
        MARS("Mars & Polar Ice Cap", "Polar ice cap & maria • RGB 8-bit (RAW8 Bayer)", 50, AviParser.AviColorFormat.RGB_8BIT_RAW8_BAYER),
        MOON("Moon (Copernicus Crater)", "Crater walls & ejecta • RGB 8-bit (Indexed)", 50, AviParser.AviColorFormat.RGB_8BIT_INDEXED)
    }

    suspend fun generateSampleAvi(
        context: Context,
        target: CelestialTarget,
        format: AviParser.AviColorFormat = target.defaultFormat,
        onProgress: (Int, Int) -> Unit
    ): File = withContext(Dispatchers.Default) {
        val frames = generatePlanetaryFrames(target, onProgress)
        val dir = File(context.cacheDir, "planetary_captures").apply { mkdirs() }
        val filename = "${target.name.lowercase()}_${format.name.lowercase()}.avi"
        val aviFile = File(dir, filename)

        // Write real RIFF AVI file
        AviParser.createAviFile(aviFile, frames, fps = 30, format = format)
        aviFile
    }

    fun generatePlanetaryFrames(
        target: CelestialTarget,
        onProgress: (Int, Int) -> Unit
    ): List<Bitmap> {
        val width = 280
        val height = 280
        val total = target.frameCount
        val frames = ArrayList<Bitmap>(total)

        // Base ground truth high-res master rendering of the planet
        val master = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderMasterPlanet(master, target)

        val masterPixels = IntArray(width * height)
        master.getPixels(masterPixels, 0, width, 0, 0, width, height)

        val rng = Random(42)

        for (frameIdx in 0 until total) {
            // Atmospheric seeing modeling:
            // "Seeing" consists of:
            // 1. Defocus / wavefront turbulence (blur kernel size varies between 1.0 and 4.5 px)
            // 2. High-frequency seeing moments (sharp frames vs blurred frames)
            val seeingQuality = 0.5f + 0.45f * sin(frameIdx * 0.42f).toFloat() + 0.05f * rng.nextFloat()
            val blurRadius = (1.2f + (1f - seeingQuality) * 4.0f)

            // Mount drift: slow periodic tracking drift
            val driftX = (sin(frameIdx * 0.10f) * 6.5f + frameIdx * 0.08f).toInt()
            val driftY = (cos(frameIdx * 0.08f) * 5.0f - frameIdx * 0.05f).toInt()

            val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val framePixels = IntArray(width * height)

            val blurInt = blurRadius.toInt().coerceIn(1, 4)
            val kernelWeight = 1.0 / ((2 * blurInt + 1) * (2 * blurInt + 1))

            for (y in 0 until height) {
                val srcY = (y - driftY).coerceIn(0, height - 1)
                val row = y * width

                for (x in 0 until width) {
                    val srcX = (x - driftX).coerceIn(0, width - 1)

                    // Box blur sampling for seeing simulation
                    var rAcc = 0.0
                    var gAcc = 0.0
                    var bAcc = 0.0

                    for (ky in -blurInt..blurInt) {
                        val sy = (srcY + ky).coerceIn(0, height - 1)
                        val sRow = sy * width
                        for (kx in -blurInt..blurInt) {
                            val sx = (srcX + kx).coerceIn(0, width - 1)
                            val c = masterPixels[sRow + sx]
                            rAcc += ((c shr 16) and 0xFF)
                            gAcc += ((c shr 8) and 0xFF)
                            bAcc += (c and 0xFF)
                        }
                    }

                    var r = (rAcc * kernelWeight).toInt()
                    var g = (gAcc * kernelWeight).toInt()
                    var b = (bAcc * kernelWeight).toInt()

                    // Atmospheric dispersion: slight vertical color shift (red upwards, blue downwards)
                    if (y > 2 && y < height - 3) {
                        val redShiftY = (srcY - 1).coerceIn(0, height - 1)
                        val blueShiftY = (srcY + 1).coerceIn(0, height - 1)
                        val rC = masterPixels[redShiftY * width + srcX]
                        val bC = masterPixels[blueShiftY * width + srcX]
                        r = (r * 0.7 + ((rC shr 16) and 0xFF) * 0.3).toInt()
                        b = (b * 0.7 + (bC and 0xFF) * 0.3).toInt()
                    }

                    // Sensor CMOS read noise & shot noise
                    val noise = (rng.nextGaussian() * 6.5).toInt()
                    r = (r + noise).coerceIn(0, 255)
                    g = (g + noise).coerceIn(0, 255)
                    b = (b + noise).coerceIn(0, 255)

                    framePixels[row + x] = Color.rgb(r, g, b)
                }
            }

            frameBitmap.setPixels(framePixels, 0, width, 0, 0, width, height)
            frames.add(frameBitmap)
            onProgress(frameIdx + 1, total)
        }

        return frames
    }

    private fun renderMasterPlanet(bitmap: Bitmap, target: CelestialTarget) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(4, 6, 12)) // Deep space background

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f

        when (target) {
            CelestialTarget.JUPITER -> {
                val radius = 80f
                // Base gas giant warm cream / ochre
                paint.color = Color.rgb(235, 218, 195)
                canvas.drawCircle(cx, cy, radius, paint)

                // Atmospheric bands (NEB, SEB, polar hoods)
                val bands = listOf(
                    // Y offset relative to center, height, Color
                    Triple(-55f, 16f, Color.rgb(180, 165, 145)), // North Polar Region
                    Triple(-32f, 10f, Color.rgb(195, 175, 150)), // North Temperate Belt
                    Triple(-16f, 15f, Color.rgb(155, 95, 65)),   // North Equatorial Belt (NEB - dark reddish brown)
                    Triple(2f, 12f, Color.rgb(240, 228, 205)),   // Equatorial Zone (bright cream)
                    Triple(18f, 16f, Color.rgb(160, 100, 70)),   // South Equatorial Belt (SEB)
                    Triple(38f, 9f, Color.rgb(190, 170, 145)),   // South Temperate Belt
                    Triple(52f, 18f, Color.rgb(175, 160, 140))   // South Polar Region
                )

                for ((yOff, bHeight, color) in bands) {
                    paint.color = color
                    val bandY = cy + yOff
                    // Draw clamped within planet disc
                    val dy = yOff
                    if (kotlin.math.abs(dy) < radius) {
                        val bandW = sqrt(radius * radius - dy * dy)
                        val rect = RectF(cx - bandW, bandY - bHeight / 2f, cx + bandW, bandY + bHeight / 2f)
                        canvas.drawOval(rect, paint)
                    }
                }

                // Great Red Spot in the South Equatorial Belt (SEB)
                val grsX = cx + 18f
                val grsY = cy + 22f
                paint.color = Color.rgb(195, 75, 45) // Vivid brick red
                canvas.drawOval(RectF(grsX - 16f, grsY - 9f, grsX + 16f, grsY + 9f), paint)
                // Inner core of GRS
                paint.color = Color.rgb(225, 90, 50)
                canvas.drawOval(RectF(grsX - 10f, grsY - 5f, grsX + 10f, grsY + 5f), paint)

                // Atmospheric limb darkening (outer edge soft shading)
                renderLimbDarkening(canvas, cx, cy, radius)
            }

            CelestialTarget.SATURN -> {
                val globeRadius = 52f

                // Saturn's Rings (drawn behind top part of globe, in front of bottom part)
                // Ring dimensions (tilted ellipse)
                val ringOuterA = 126f
                val ringInnerA = 112f
                val ringCassini = 110f // Cassini division
                val ringOuterB = 108f
                val ringInnerB = 78f
                val ringC = 64f
                val tilt = 0.38f // Aspect ratio of ellipse

                // Back of rings
                drawRings(canvas, cx, cy, tilt, ringOuterA, ringInnerA, ringCassini, ringOuterB, ringInnerB, ringC, isFront = false)

                // Globe of Saturn
                paint.color = Color.rgb(230, 212, 175)
                canvas.drawCircle(cx, cy, globeRadius, paint)

                // Equatorial cloud bands
                paint.color = Color.rgb(205, 185, 145)
                canvas.drawOval(RectF(cx - globeRadius, cy - 8f, cx + globeRadius, cy + 8f), paint)
                paint.color = Color.rgb(175, 155, 120)
                canvas.drawOval(RectF(cx - globeRadius * 0.85f, cy - 28f, cx + globeRadius * 0.85f, cy - 16f), paint)

                // Shadow of globe cast onto rings
                paint.color = Color.rgb(15, 18, 25)
                canvas.drawOval(RectF(cx + 18f, cy - 36f * tilt, cx + 55f, cy + 8f * tilt), paint)

                // Front of rings
                drawRings(canvas, cx, cy, tilt, ringOuterA, ringInnerA, ringCassini, ringOuterB, ringInnerB, ringC, isFront = true)

                // Shadow of rings across the globe
                paint.color = Color.argb(140, 20, 20, 28)
                canvas.drawRect(cx - globeRadius * 0.95f, cy + 2f, cx + globeRadius * 0.95f, cy + 9f, paint)

                renderLimbDarkening(canvas, cx, cy, globeRadius)
            }

            CelestialTarget.MARS -> {
                val radius = 72f
                // Mars rust/ochre base
                paint.color = Color.rgb(215, 115, 70)
                canvas.drawCircle(cx, cy, radius, paint)

                // Dark surface markings (Syrtis Major & Sinus Meridiani)
                paint.color = Color.rgb(130, 75, 55)
                val syrtis = RectF(cx - 24f, cy - 15f, cx + 28f, cy + 30f)
                canvas.drawOval(syrtis, paint)
                paint.color = Color.rgb(105, 60, 45)
                canvas.drawOval(RectF(cx - 15f, cy - 5f, cx + 18f, cy + 22f), paint)

                // Brilliant South Polar Ice Cap
                paint.color = Color.rgb(250, 250, 255)
                canvas.drawOval(RectF(cx - 20f, cy + radius - 16f, cx + 20f, cy + radius - 2f), paint)

                // North polar hood haze
                paint.color = Color.argb(100, 220, 230, 245)
                canvas.drawOval(RectF(cx - 28f, cy - radius + 2f, cx + 28f, cy - radius + 15f), paint)

                renderLimbDarkening(canvas, cx, cy, radius)
            }

            CelestialTarget.MOON -> {
                val radius = 95f
                // Lunar highland surface
                paint.color = Color.rgb(175, 175, 175)
                canvas.drawCircle(cx, cy, radius, paint)

                // Copernicus crater
                val craterX = cx - 5f
                val craterY = cy + 5f
                val craterR = 32f

                // Outer ejecta rays
                paint.color = Color.rgb(205, 205, 205)
                for (angle in 0 until 360 step 30) {
                    val rad = Math.toRadians(angle.toDouble())
                    val x2 = craterX + cos(rad) * 65f
                    val y2 = craterY + sin(rad) * 65f
                    canvas.drawLine(craterX.toFloat(), craterY.toFloat(), x2.toFloat(), y2.toFloat(), paint)
                }

                // Crater rim (terrace highlight and shadow)
                paint.color = Color.rgb(90, 90, 90) // Shadowed wall
                canvas.drawCircle(craterX + 4f, craterY, craterR, paint)
                paint.color = Color.rgb(230, 230, 230) // Sunlit wall
                canvas.drawCircle(craterX - 3f, craterY, craterR - 2f, paint)

                // Crater floor
                paint.color = Color.rgb(145, 145, 145)
                canvas.drawCircle(craterX, craterY, craterR - 6f, paint)

                // Central peaks
                paint.color = Color.rgb(245, 245, 245)
                canvas.drawCircle(craterX - 2f, craterY, 4f, paint)
                paint.color = Color.rgb(75, 75, 75)
                canvas.drawCircle(craterX + 2f, craterY, 3f, paint)
            }
        }
    }

    private fun drawRings(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        tilt: Float,
        outerA: Float,
        innerA: Float,
        cassini: Float,
        outerB: Float,
        innerB: Float,
        innerC: Float,
        isFront: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Ring A (soft grey-beige)
        paint.color = Color.rgb(195, 182, 155)
        drawTiltedRing(canvas, cx, cy, tilt, outerA, innerA, paint, isFront)

        // Cassini Division (dark gap)
        paint.color = Color.rgb(12, 14, 20)
        drawTiltedRing(canvas, cx, cy, tilt, innerA, outerB, paint, isFront)

        // Ring B (brightest, denser)
        paint.color = Color.rgb(235, 222, 185)
        drawTiltedRing(canvas, cx, cy, tilt, outerB, innerB, paint, isFront)

        // Ring C (Crepe ring - faint)
        paint.color = Color.rgb(115, 108, 95)
        drawTiltedRing(canvas, cx, cy, tilt, innerB, innerC, paint, isFront)
    }

    private fun drawTiltedRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        tilt: Float,
        rOut: Float,
        rIn: Float,
        paint: Paint,
        isFront: Boolean
    ) {
        // Draw using clip bounds or semi-ovals
        canvas.save()
        if (isFront) {
            canvas.clipRect(cx - rOut * 1.2f, cy, cx + rOut * 1.2f, cy + rOut * tilt * 1.2f)
        } else {
            canvas.clipRect(cx - rOut * 1.2f, cy - rOut * tilt * 1.2f, cx + rOut * 1.2f, cy)
        }
        val outRect = RectF(cx - rOut, cy - rOut * tilt, cx + rOut, cy + rOut * tilt)
        paint.style = Paint.Style.STROKE
        val strokeW = (rOut - rIn).coerceAtLeast(1f)
        paint.strokeWidth = strokeW
        val midR = (rOut + rIn) / 2f
        val midRect = RectF(cx - midR, cy - midR * tilt, cx + midR, cy + midR * tilt)
        canvas.drawOval(midRect, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun renderLimbDarkening(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        for (i in 0..12) {
            val r = radius - (i * 1.1f)
            if (r > 0) {
                val alpha = (120 * exp(-i / 3.0)).toInt().coerceIn(0, 150)
                paint.color = Color.argb(alpha, 8, 10, 18)
                paint.strokeWidth = 1.2f
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }
}
