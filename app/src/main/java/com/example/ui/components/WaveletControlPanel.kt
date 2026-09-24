package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Details
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SharpeningMode
import com.example.data.model.StackingConfig
import com.example.ui.theme.CelestialBlue
import com.example.ui.theme.NebulaCyan
import com.example.ui.theme.ObservatoryCardBorder
import com.example.ui.theme.ObservatorySurfaceVariant
import com.example.ui.theme.SeeingEmerald
import com.example.ui.theme.StarGold
import kotlin.math.roundToInt

/**
 * Astrophotography Post-Processing Suite:
 * Wavelets, Unsharp Mask & Laplacian Sharpening Tools, Tone adjustments, and ADC Atmospheric Dispersion Corrector.
 */
@Composable
fun WaveletControlPanel(
    config: StackingConfig,
    onConfigChange: (StackingConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Sharpening Tool, 1 = Wavelets, 2 = Tone/Contrast, 3 = ADC Prism

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wavelet_control_panel"),
        shape = RoundedCornerShape(16.dp),
        color = ObservatorySurfaceVariant,
        border = BorderStroke(1.dp, ObservatoryCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = StarGold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sharpening & Wavelet Suite",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sharpen planetary seeing blur, craters, bands & correct ADC",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        onConfigChange(
                            config.copy(
                                sharpeningMode = SharpeningMode.UNSHARP_MASK,
                                unsharpMaskAmount = 1.2f,
                                unsharpMaskRadius = 1.5f,
                                unsharpMaskThreshold = 0.02f,
                                laplacianSharpening = 0.5f,
                                waveletFine = 1.0f,
                                waveletMedium = 0.5f,
                                waveletCoarse = 0.2f,
                                contrast = 1.1f,
                                brightness = 1.0f,
                                saturation = 1.0f,
                                redShiftX = 0,
                                redShiftY = 0,
                                blueShiftX = 0,
                                blueShiftY = 0
                            )
                        )
                    },
                    modifier = Modifier.testTag("reset_wavelets_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset adjustments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Sharpening", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Details, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StarGold,
                        selectedLabelColor = Color(0xFF241800)
                    )
                )

                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Wavelets", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NebulaCyan,
                        selectedLabelColor = Color(0xFF001B2B)
                    )
                )

                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Tone", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SeeingEmerald,
                        selectedLabelColor = Color(0xFF002315)
                    )
                )

                FilterChip(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    label = { Text("ADC", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CelestialBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> {
                    // Dedicated Sharpening Tool
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Sharpening Algorithm",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StarGold
                        )

                        // Mode Selector Radios
                        SharpeningMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onConfigChange(config.copy(sharpeningMode = mode)) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            if (config.sharpeningMode == mode) StarGold else Color.Transparent,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (config.sharpeningMode == mode) {
                                        Box(modifier = Modifier.size(6.dp).background(Color.Black, CircleShape))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (config.sharpeningMode == mode) StarGold else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (config.sharpeningMode == SharpeningMode.UNSHARP_MASK || config.sharpeningMode == SharpeningMode.COMBINED) {
                            WaveletSliderRow(
                                title = "USM Sharpening Strength",
                                subtitle = "High-pass edge amplifier (typical 0.8–2.0)",
                                value = config.unsharpMaskAmount,
                                valueRange = 0f..3.0f,
                                onValueChange = { onConfigChange(config.copy(unsharpMaskAmount = it)) },
                                accentColor = StarGold
                            )

                            WaveletSliderRow(
                                title = "USM Blur Radius",
                                subtitle = "Pixel scale of planetary detail (1.0–3.0 px)",
                                value = config.unsharpMaskRadius,
                                valueRange = 0.5f..5.0f,
                                onValueChange = { onConfigChange(config.copy(unsharpMaskRadius = it)) },
                                accentColor = NebulaCyan
                            )

                            WaveletSliderRow(
                                title = "Noise Suppression Threshold",
                                subtitle = "Prevents grain & background noise amplification",
                                value = config.unsharpMaskThreshold,
                                valueRange = 0.0f..0.15f,
                                onValueChange = { onConfigChange(config.copy(unsharpMaskThreshold = it)) },
                                accentColor = SeeingEmerald
                            )
                        }

                        if (config.sharpeningMode == SharpeningMode.LAPLACIAN || config.sharpeningMode == SharpeningMode.COMBINED) {
                            WaveletSliderRow(
                                title = "Laplacian Micro-Contrast",
                                subtitle = "3x3 second-derivative fine edge filter",
                                value = config.laplacianSharpening,
                                valueRange = 0.0f..2.0f,
                                onValueChange = { onConfigChange(config.copy(laplacianSharpening = it)) },
                                accentColor = Color(0xFFFF9F1C)
                            )
                        }
                    }
                }

                1 -> {
                    // Wavelets tab
                    WaveletSliderRow(
                        title = "Layer 1: Fine Detail",
                        subtitle = "Cassini division, small craters & storms",
                        value = config.waveletFine,
                        valueRange = 0f..2.5f,
                        onValueChange = { onConfigChange(config.copy(waveletFine = it)) },
                        accentColor = NebulaCyan
                    )

                    WaveletSliderRow(
                        title = "Layer 2: Medium Detail",
                        subtitle = "Equatorial cloud bands & crater rings",
                        value = config.waveletMedium,
                        valueRange = 0f..2.5f,
                        onValueChange = { onConfigChange(config.copy(waveletMedium = it)) },
                        accentColor = StarGold
                    )

                    WaveletSliderRow(
                        title = "Layer 3: Coarse Detail",
                        subtitle = "Planetary limb & global albedo",
                        value = config.waveletCoarse,
                        valueRange = 0f..2.0f,
                        onValueChange = { onConfigChange(config.copy(waveletCoarse = it)) },
                        accentColor = SeeingEmerald
                    )
                }

                2 -> {
                    // Tone tab
                    WaveletSliderRow(
                        title = "Contrast",
                        subtitle = "Planetary disc dynamic separation",
                        value = config.contrast,
                        valueRange = 0.6f..2.0f,
                        onValueChange = { onConfigChange(config.copy(contrast = it)) },
                        accentColor = StarGold
                    )

                    WaveletSliderRow(
                        title = "Brightness",
                        subtitle = "Exposure compensation",
                        value = config.brightness,
                        valueRange = 0.6f..1.6f,
                        onValueChange = { onConfigChange(config.copy(brightness = it)) },
                        accentColor = NebulaCyan
                    )

                    WaveletSliderRow(
                        title = "Color Saturation",
                        subtitle = "Atmospheric gas and albedo colors",
                        value = config.saturation,
                        valueRange = 0.0f..2.0f,
                        onValueChange = { onConfigChange(config.copy(saturation = it)) },
                        accentColor = Color(0xFFF72585)
                    )
                }

                3 -> {
                    // Atmospheric Dispersion Corrector (ADC)
                    Text(
                        text = "Atmospheric Dispersion Corrector (ADC)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CelestialBlue
                    )
                    Text(
                        text = "Earth's atmosphere bends blue light more than red light. Adjust channel shifts to cancel color fringing:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Red Shift X/Y: (${config.redShiftX}, ${config.redShiftY})",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { onConfigChange(config.copy(redShiftY = config.redShiftY - 1)) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = ButtonDefaults.ContentPadding
                                ) { Text("▲ Y", fontSize = 11.sp) }
                                OutlinedButton(
                                    onClick = { onConfigChange(config.copy(redShiftY = config.redShiftY + 1)) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = ButtonDefaults.ContentPadding
                                ) { Text("▼ Y", fontSize = 11.sp) }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Blue Shift X/Y: (${config.blueShiftX}, ${config.blueShiftY})",
                                color = Color(0xFF4CC9F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { onConfigChange(config.copy(blueShiftY = config.blueShiftY - 1)) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = ButtonDefaults.ContentPadding
                                ) { Text("▲ Y", fontSize = 11.sp) }
                                OutlinedButton(
                                    onClick = { onConfigChange(config.copy(blueShiftY = config.blueShiftY + 1)) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = ButtonDefaults.ContentPadding
                                ) { Text("▼ Y", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveletSliderRow(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
