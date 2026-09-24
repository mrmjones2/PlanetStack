package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NebulaCyan
import com.example.ui.theme.ObservatoryCardBorder
import com.example.ui.theme.ObservatorySurfaceVariant
import com.example.ui.theme.SeeingAmber
import com.example.ui.theme.SeeingEmerald
import com.example.ui.theme.SeeingRed
import com.example.ui.theme.StarGold
import kotlin.math.roundToInt

/**
 * AutoStakkert-style Planetary Seeing Quality Graph.
 * Visualizes frame atmospheric sharpness distribution (sorted highest to lowest)
 * with an interactive cutoff indicator for frame selection.
 */
@Composable
fun QualityGraphView(
    sortedScores: List<Float>,
    selectedPercentage: Int,
    onPercentageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalFrames = sortedScores.size
    val selectedFramesCount = ((selectedPercentage / 100f) * totalFrames).roundToInt().coerceIn(1, totalFrames)

    // Current cutoff quality
    val cutoffScore = if (sortedScores.isNotEmpty()) {
        val idx = (selectedFramesCount - 1).coerceIn(0, sortedScores.size - 1)
        sortedScores[idx]
    } else 0f

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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quality_graph_card"),
        shape = RoundedCornerShape(16.dp),
        color = ObservatorySurfaceVariant,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObservatoryCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = NebulaCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Atmospheric Seeing Quality Curve",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "AutoStakkert quality ranking (sharpest to softest)",
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

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas Graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF070B16), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(totalFrames) {
                            detectTapGestures { offset ->
                                val pct = ((offset.x / size.width) * 100f).roundToInt().coerceIn(5, 100)
                                onPercentageChange(pct)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    if (sortedScores.isEmpty()) return@Canvas

                    // Grid lines (horizontal quality levels 25%, 50%, 75%)
                    val gridColor = Color(0x22FFFFFF)
                    for (level in listOf(0.25f, 0.50f, 0.75f)) {
                        val y = h * (1f - level)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Build path for the green AutoStakkert quality curve
                    val points = sortedScores.mapIndexed { idx, score ->
                        val x = if (totalFrames > 1) (idx.toFloat() / (totalFrames - 1)) * w else 0f
                        val y = h * (1f - score.coerceIn(0.05f, 0.98f))
                        Offset(x, y)
                    }

                    // Cutoff vertical divider
                    val cutoffX = (selectedPercentage / 100f) * w

                    // Fill under selected region
                    val fillPath = Path().apply {
                        moveTo(0f, h)
                        points.forEach { pt ->
                            if (pt.x <= cutoffX) {
                                lineTo(pt.x, pt.y)
                            }
                        }
                        // Interpolate cutoff point
                        val cutoffY = if (sortedScores.isNotEmpty()) {
                            val ratio = (selectedPercentage / 100f).coerceIn(0f, 1f)
                            val idxF = ratio * (sortedScores.size - 1)
                            val i0 = idxF.toInt()
                            val i1 = (i0 + 1).coerceAtMost(sortedScores.size - 1)
                            val t = idxF - i0
                            val interpScore = sortedScores[i0] * (1 - t) + sortedScores[i1] * t
                            h * (1f - interpScore.coerceIn(0.05f, 0.98f))
                        } else h / 2f

                        lineTo(cutoffX, cutoffY)
                        lineTo(cutoffX, h)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SeeingEmerald.copy(alpha = 0.45f),
                                SeeingEmerald.copy(alpha = 0.05f)
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw the entire quality curve line
                    val linePath = Path().apply {
                        points.forEachIndexed { i, pt ->
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }

                    drawPath(
                        path = linePath,
                        color = SeeingEmerald,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw cutoff vertical threshold line
                    drawLine(
                        color = StarGold,
                        start = Offset(cutoffX, 0f),
                        end = Offset(cutoffX, h),
                        strokeWidth = 2.dp.toPx()
                    )

                    // Cutoff handle circle at top of graph
                    drawCircle(
                        color = StarGold,
                        radius = 4.5.dp.toPx(),
                        center = Offset(cutoffX, 6.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Graph Metrics Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stacking: $selectedFramesCount of $totalFrames frames ($selectedPercentage%)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StarGold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Cutoff: ${(cutoffScore * 100).roundToInt()}% Sharpness",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
