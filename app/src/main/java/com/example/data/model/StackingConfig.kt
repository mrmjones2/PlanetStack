package com.example.data.model

import android.graphics.Bitmap

data class PlanetaryFrame(
    val index: Int,
    val timestampMs: Long,
    val bitmap: Bitmap,
    var qualityScore: Float = 0f, // 0.0 to 1.0 (composite frame quality score)
    var sharpnessScore: Float = 0f, // 0.0 to 1.0 (Laplacian edge sharpness)
    var contrastScore: Float = 0f,  // 0.0 to 1.0 (disc surface contrast)
    var jitterScore: Float = 0f,    // 0.0 to 1.0 (atmospheric jitter stability)
    var jitterOffsetPx: Float = 0f, // Displacement in pixels from smoothed track
    var rank: Int = 0,
    var centroidX: Float = 0f,
    var centroidY: Float = 0f,
    var shiftX: Int = 0,
    var shiftY: Int = 0,
    var subShiftX: Float = 0f,      // Sub-pixel floating-point X shift
    var subShiftY: Float = 0f,      // Sub-pixel floating-point Y shift
    var registrationScore: Float = 1.0f, // Cross-correlation registration confidence
    var featurePointsCount: Int = 0  // Number of tracked planetary feature alignment points
)

enum class StackingMethod(val displayName: String, val description: String) {
    SIGMA_CLIPPED("Sigma-Clipped", "Rejects atmospheric seeing outliers (>1.8σ) & averages"),
    AVERAGE("Average (Mean)", "Maximizes SNR gain (noise reduction ∝ √N)"),
    MEDIAN("Median", "Eliminates cosmic rays, satellite trails & transient dust"),
    QUALITY_WEIGHTED("Quality Weighted", "Weights sharpest frames higher in luminance integration")
}

enum class SharpeningMode(val displayName: String, val description: String) {
    UNSHARP_MASK("Unsharp Mask (USM)", "Classic astrophotography high-pass filter with noise thresholding"),
    LAPLACIAN("Laplacian Micro-Edge", "Sharpens fine planetary features (Cassini division, crater rims)"),
    COMBINED("Hybrid Multi-Stage", "Cascades unsharp mask edge enhancement with Laplacian high-pass sharpening")
}

data class StackingConfig(
    val stackPercentage: Int = 30, // 5% to 100%
    val method: StackingMethod = StackingMethod.SIGMA_CLIPPED,
    val alignPlanet: Boolean = true,
    val subPixelAlignment: Boolean = true, // Sub-pixel bilinear registration based on planetary feature detection
    // Sharpening Tool parameters
    val sharpeningMode: SharpeningMode = SharpeningMode.UNSHARP_MASK,
    val unsharpMaskAmount: Float = 1.2f,       // 0.0 to 3.0 (sharpening strength)
    val unsharpMaskRadius: Float = 1.5f,       // 0.5 to 5.0 (gaussian blur radius in pixels)
    val unsharpMaskThreshold: Float = 0.02f,   // 0.0 to 0.20 (tonal edge threshold to suppress noise)
    val laplacianSharpening: Float = 0.5f,     // 0.0 to 2.0 (high-frequency micro-contrast enhancement)
    // Post-processing parameters
    val waveletFine: Float = 1.2f,     // 0.0 to 3.0
    val waveletMedium: Float = 0.8f,   // 0.0 to 3.0
    val waveletCoarse: Float = 0.3f,   // 0.0 to 2.0
    val denoise: Float = 0.15f,        // 0.0 to 1.0
    val contrast: Float = 1.15f,       // 0.5 to 2.0
    val brightness: Float = 1.05f,     // 0.5 to 2.0
    val saturation: Float = 1.10f,     // 0.0 to 2.0
    val gamma: Float = 1.0f,           // 0.5 to 2.0
    // Atmospheric Dispersion Corrector (ADC)
    val redShiftX: Int = 0,
    val redShiftY: Int = 0,
    val blueShiftX: Int = 0,
    val blueShiftY: Int = 0
)
