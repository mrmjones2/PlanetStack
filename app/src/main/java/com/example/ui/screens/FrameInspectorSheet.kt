package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlanetaryFrame
import com.example.engine.AviParser
import com.example.engine.PlanetaryFeature
import com.example.ui.theme.NebulaCyan
import com.example.ui.theme.ObservatoryCardBorder
import com.example.ui.theme.ObservatorySurfaceVariant
import com.example.ui.theme.SeeingAmber
import com.example.ui.theme.SeeingEmerald
import com.example.ui.theme.SeeingRed
import com.example.ui.theme.StarGold
import kotlin.math.roundToInt

/**
 * Frame Inspector Carousel.
 * Allows scrolling through individual planetary frames, showing their rank,
 * seeing score, sub-pixel registration drift, and planetary feature alignment points.
 */
@Composable
fun FrameInspector(
    frames: List<PlanetaryFrame>,
    selectedCutoffIndex: Int,
    videoStreamInfo: AviParser.VideoStreamInfo? = null,
    detectedFeatures: List<PlanetaryFeature> = emptyList(),
    modifier: Modifier = Modifier
) {
    var inspectingFrame by remember { mutableStateOf<PlanetaryFrame?>(frames.firstOrNull()) }
    var showFeaturePoints by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("frame_inspector_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Frame Inspector (Ranked Sharpest to Softest)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            if (detectedFeatures.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NebulaCyan.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NebulaCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable { showFeaturePoints = !showFeaturePoints }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showFeaturePoints) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle APs",
                            tint = NebulaCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${detectedFeatures.size} APs",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NebulaCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "${frames.size} frames",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal scrolling frame list
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(frames) { frame ->
                val isSelectedForStack = frame.rank <= selectedCutoffIndex
                val isInspected = inspectingFrame?.index == frame.index

                val borderColor = when {
                    isInspected -> StarGold
                    isSelectedForStack -> SeeingEmerald.copy(alpha = 0.8f)
                    else -> ObservatoryCardBorder
                }

                Surface(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(if (isInspected) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable { inspectingFrame = frame }
                        .testTag("frame_thumb_${frame.index}"),
                    color = ObservatorySurfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(68.dp)) {
                            Image(
                                bitmap = frame.bitmap.asImageBitmap(),
                                contentDescription = "Frame #${frame.rank}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )

                            // Rank badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "#${frame.rank}",
                                    color = if (frame.rank <= 3) StarGold else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val scorePct = (frame.qualityScore * 100).roundToInt()
                        val scoreColor = when {
                            frame.qualityScore >= 0.70f -> SeeingEmerald
                            frame.qualityScore >= 0.45f -> SeeingAmber
                            else -> SeeingRed
                        }

                        Text(
                            text = "$scorePct%",
                            color = scoreColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = String.format(java.util.Locale.US, "%+.1f,%+.1f", frame.subShiftX, frame.subShiftY),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Active Inspecting Frame Detail Card
        inspectingFrame?.let { frame ->
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0C1220),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObservatoryCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Frame Thumbnail with optional Planetary Feature Reticles
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, NebulaCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                bitmap = frame.bitmap.asImageBitmap(),
                                contentDescription = "Selected Frame",
                                modifier = Modifier.fillMaxWidth().height(76.dp)
                            )

                            if (showFeaturePoints && detectedFeatures.isNotEmpty()) {
                                val bmpW = frame.bitmap.width.toFloat().coerceAtLeast(1f)
                                val bmpH = frame.bitmap.height.toFloat().coerceAtLeast(1f)

                                Canvas(modifier = Modifier.size(76.dp)) {
                                    val scaleX = size.width / bmpW
                                    val scaleY = size.height / bmpH

                                    detectedFeatures.forEachIndexed { fIdx, feat ->
                                        // Target pixel on this frame accounts for frame's sub-pixel shift
                                        val fx = (feat.x - frame.subShiftX) * scaleX
                                        val fy = (feat.y - frame.subShiftY) * scaleY

                                        if (fx in 0f..size.width && fy in 0f..size.height) {
                                            val apColor = if (fIdx == 0) StarGold else NebulaCyan
                                            drawCircle(
                                                color = apColor,
                                                radius = 4.5f,
                                                center = Offset(fx, fy),
                                                style = Stroke(width = 1.5f)
                                            )
                                            drawCircle(
                                                color = apColor,
                                                radius = 1.2f,
                                                center = Offset(fx, fy)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Frame #${frame.index + 1} (Rank #${frame.rank})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val isStacked = frame.rank <= selectedCutoffIndex
                                Text(
                                    text = if (isStacked) "✓ In Stack" else "✗ Excluded",
                                    color = if (isStacked) SeeingEmerald else SeeingRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Overall Quality Score: ${(frame.qualityScore * 100).roundToInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StarGold
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Sharpness pill
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SHARPNESS", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${(frame.sharpnessScore * 100).roundToInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SeeingEmerald,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                // Contrast pill
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CONTRAST", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${(frame.contrastScore * 100).roundToInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NebulaCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                // Jitter / Stability pill
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text("JITTER STAB", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${(frame.jitterScore * 100).roundToInt()}% (${String.format(java.util.Locale.US, "%.1f", frame.jitterOffsetPx)}px)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (frame.jitterOffsetPx < 3.0f) SeeingEmerald else SeeingAmber,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Sub-Pixel Registration & Format Info Bar
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF070B14),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObservatoryCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Adjust,
                                    contentDescription = null,
                                    tint = NebulaCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sub-Pixel: ΔX=${String.format(java.util.Locale.US, "%+.2f", frame.subShiftX)}px, ΔY=${String.format(java.util.Locale.US, "%+.2f", frame.subShiftY)}px",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Reg: ${(frame.registrationScore * 100).roundToInt()}% (${frame.featurePointsCount} APs)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = SeeingEmerald
                            )
                        }
                    }

                    videoStreamInfo?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Stream: ${it.formatDescription} (${it.width}×${it.height})",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = NebulaCyan
                        )
                    }
                }
            }
        }
    }
}

