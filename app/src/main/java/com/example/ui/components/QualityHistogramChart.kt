package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CelestialBlue
import com.example.ui.theme.NebulaCyan
import com.example.ui.theme.ObservatoryCardBorder
import com.example.ui.theme.ObservatorySurfaceVariant
import com.example.ui.theme.SeeingAmber
import com.example.ui.theme.SeeingEmerald
import com.example.ui.theme.SeeingRed
import com.example.ui.theme.StarGold
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 100% Native Jetpack Compose Planetary Frame Quality Distribution Histogram & Cutoff Selector.
 * Replaces web-based D3.js visualization with hardware-accelerated Compose Canvas rendering.
 * Provides interactive threshold dragging, dynamic bin coloring, and dual visualization modes.
 */
@Composable
fun QualityHistogramChart(
    sortedScores: List<Float>,
    selectedPercentage: Int,
    onPercentageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalFrames = sortedScores.size
    val selectedFramesCount = ((selectedPercentage / 100f) * totalFrames).roundToInt().coerceIn(1, max(1, totalFrames))

    val cutoffScore = if (sortedScores.isNotEmpty()) {
        val idx = (selectedFramesCount - 1).coerceIn(0, sortedScores.size - 1)
        sortedScores[idx]
    } else 0.5f

    val seeingBadgeColor = when {
        cutoffScore >= 0.70f -> SeeingEmerald
        cutoffScore >= 0.45f -> SeeingAmber
        else -> SeeingRed
    }

    val seeingBadgeText = when {
        cutoffScore >= 0.70f -> "Sharp Seeing"
        cutoffScore >= 0.45f -> "Moderate Seeing"
        else -> "Turbulent Seeing"
    }

    // Chart mode toggle: 0 = Native Distribution Histogram, 1 = AutoStakkert Ranking Curve
    var viewMode by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quality_histogram_card"),
        shape = RoundedCornerShape(16.dp),
        color = ObservatorySurfaceVariant,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, ObservatoryCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(NebulaCyan.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = NebulaCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Frame Quality Distribution",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Seeing sharpness distribution & cutoff threshold",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .background(seeingBadgeColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = seeingBadgeText,
                        color = seeingBadgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    label = { Text("Quality Histogram", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NebulaCyan,
                        selectedLabelColor = Color(0xFF001B2B)
                    )
                )

                FilterChip(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    label = { Text("Ranking Curve", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StarGold,
                        selectedLabelColor = Color(0xFF241800)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (viewMode == 0) {
                // Native Compose Canvas Histogram
                NativeHistogramCanvas(
                    scores = sortedScores,
                    cutoffScore = cutoffScore,
                    onCutoffScoreChanged = { newScore ->
                        if (sortedScores.isNotEmpty()) {
                            val countAbove = sortedScores.count { it >= newScore }
                            val pct = ((countAbove.toFloat() / sortedScores.size) * 100f)
                                .roundToInt()
                                .coerceIn(5, 100)
                            onPercentageChange(pct)
                        }
                    }
                )
            } else {
                // Ranking Curve
                QualityGraphView(
                    sortedScores = sortedScores,
                    selectedPercentage = selectedPercentage,
                    onPercentageChange = onPercentageChange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direct Score Threshold Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quality Score Cutoff Threshold",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Stacking frames with seeing score ≥ ${(cutoffScore * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = StarGold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NebulaCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, NebulaCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", cutoffScore * 100)}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = NebulaCyan
                    )
                }
            }

            val minScore = (sortedScores.minOrNull() ?: 0.1f).coerceAtLeast(0.05f)
            val maxScore = (sortedScores.maxOrNull() ?: 0.95f).coerceAtMost(1.0f)
            val sliderRange = if (minScore < maxScore) minScore..maxScore else 0.1f..0.95f

            Slider(
                value = cutoffScore.coerceIn(sliderRange.start, sliderRange.endInclusive),
                onValueChange = { targetThreshold ->
                    if (sortedScores.isNotEmpty()) {
                        val countAbove = sortedScores.count { it >= targetThreshold }
                        val pct = ((countAbove.toFloat() / sortedScores.size) * 100f)
                            .roundToInt()
                            .coerceIn(5, 100)
                        onPercentageChange(pct)
                    }
                },
                valueRange = sliderRange,
                colors = SliderDefaults.colors(
                    thumbColor = StarGold,
                    activeTrackColor = StarGold,
                    inactiveTrackColor = StarGold.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("score_threshold_slider")
            )

            // Metrics Summary Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: $selectedFramesCount of $totalFrames ($selectedPercentage%)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = StarGold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Drag or tap histogram to adjust cutoff",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * High-performance hardware-accelerated Compose Canvas Histogram.
 * Divides score spectrum into 16 bins, highlights active frames, and renders interactive cutoff guide.
 */
@Composable
private fun NativeHistogramCanvas(
    scores: List<Float>,
    cutoffScore: Float,
    onCutoffScoreChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val binCount = 16
    val minScore = 0.0f
    val maxScore = 1.0f
    val binWidth = (maxScore - minScore) / binCount

    // Compute histogram bins
    val bins = remember(scores) {
        val counts = IntArray(binCount)
        for (score in scores) {
            val idx = ((score - minScore) / binWidth).toInt().coerceIn(0, binCount - 1)
            counts[idx]++
        }
        counts
    }

    val maxCount = remember(bins) {
        max(1, bins.maxOrNull() ?: 1)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFF070B16), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("quality_histogram_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(scores) {
                    detectTapGestures { offset ->
                        val tapRatio = (offset.x / size.width).coerceIn(0f, 1f)
                        val tappedScore = minScore + tapRatio * (maxScore - minScore)
                        onCutoffScoreChanged(tappedScore)
                    }
                }
                .pointerInput(scores) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val dragRatio = (change.position.x / size.width).coerceIn(0f, 1f)
                        val draggedScore = minScore + dragRatio * (maxScore - minScore)
                        onCutoffScoreChanged(draggedScore)
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // 1. Subtle horizontal grid lines (25%, 50%, 75% max count)
            val gridColor = Color(0x18FFFFFF)
            for (fraction in listOf(0.25f, 0.50f, 0.75f, 1.0f)) {
                val y = h * (1f - fraction)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2. Draw Histogram Bars
            val barSpacing = 3.dp.toPx()
            val totalSpacing = barSpacing * (binCount - 1)
            val individualBarWidth = (w - totalSpacing) / binCount

            for (i in 0 until binCount) {
                val binStartScore = minScore + i * binWidth
                val binEndScore = binStartScore + binWidth
                val count = bins[i]
                val barHeight = if (maxCount > 0) (count.toFloat() / maxCount) * (h - 14.dp.toPx()) else 0f
                val clampedBarHeight = max(barHeight, if (count > 0) 3.dp.toPx() else 0f)

                val x = i * (individualBarWidth + barSpacing)
                val y = h - clampedBarHeight

                // Check if this bin is selected (at or above cutoff threshold)
                val isSelected = binEndScore >= cutoffScore

                val (topColor, bottomColor) = when {
                    !isSelected -> Color(0x35607D8B) to Color(0x20263238)
                    binStartScore >= 0.70f -> SeeingEmerald to NebulaCyan
                    binStartScore >= 0.45f -> StarGold to SeeingAmber
                    else -> NebulaCyan to Color(0xFF007799)
                }

                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(topColor, bottomColor)),
                    topLeft = Offset(x, y),
                    size = Size(individualBarWidth, clampedBarHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Optional glowing stroke on selected bars
                if (isSelected && count > 0) {
                    drawRoundRect(
                        color = topColor.copy(alpha = 0.6f),
                        topLeft = Offset(x, y),
                        size = Size(individualBarWidth, clampedBarHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // 3. Draw Cutoff Indicator Line & Marker
            val cutoffRatio = ((cutoffScore - minScore) / (maxScore - minScore)).coerceIn(0f, 1f)
            val cutoffX = cutoffRatio * w

            // Vertical dashed line
            drawLine(
                color = StarGold,
                start = Offset(cutoffX, 0f),
                end = Offset(cutoffX, h),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )

            // Top indicator circle
            drawCircle(
                color = StarGold,
                radius = 5.dp.toPx(),
                center = Offset(cutoffX, 5.dp.toPx())
            )
            drawCircle(
                color = Color.Black,
                radius = 2.5.dp.toPx(),
                center = Offset(cutoffX, 5.dp.toPx())
            )

            // Bottom baseline
            drawLine(
                color = Color(0x44FFFFFF),
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}
