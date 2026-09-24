package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.LensBlur
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CelestialBlue
import com.example.ui.theme.NebulaCyan
import com.example.ui.theme.ObservatoryCardBorder
import com.example.ui.theme.ObservatorySurfaceVariant
import com.example.ui.theme.SeeingEmerald
import com.example.ui.theme.StarGold

@Composable
fun ObservatoryGuideScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("observatory_guide_grid"),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero card spanning full width
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GuideHeroCard()
                }

                item {
                    GuideStepCard(
                        step = "1",
                        icon = Icons.Default.Videocam,
                        title = "Capture Video in AVI Format",
                        description = "Record uncompressed or high-quality AVI video with planetary cameras (ZWO ASI, QHY, Celestron) or webcams using capture tools like FireCapture or SharpCap. Typical captures contain 1,000–5,000 frames at 30–120 FPS.",
                        badgeColor = NebulaCyan
                    )
                }

                item {
                    GuideStepCard(
                        step = "2",
                        icon = Icons.Default.LensBlur,
                        title = "Sharpness, Contrast & Jitter Analysis",
                        description = "Every video frame is evaluated across three seeing dimensions: Laplacian edge sharpness (detecting crisp detail moments), RMS planetary contrast (separating faint cloud belts from haze), and atmospheric jitter tracking (measuring wavefront wobble and limb distortion). Frames are assigned a composite quality score and ranked into an AutoStakkert-style curve.",
                        badgeColor = SeeingEmerald
                    )
                }

                item {
                    GuideStepCard(
                        step = "3",
                        icon = Icons.Default.CenterFocusStrong,
                        title = "Centroid Drift Alignment",
                        description = "Planets drift across the sensor due to telescope mount tracking errors and wind. PlanetStack calculates the intensity center of gravity for each frame, translating all frames to common coordinates.",
                        badgeColor = StarGold
                    )
                }

                item {
                    GuideStepCard(
                        step = "4",
                        icon = Icons.Default.Compress,
                        title = "Frame Stacking (Mean & Sigma-Clip)",
                        description = "Combining N frames reduces CMOS readout noise by √N (e.g. 64 frames yields an 8× noise reduction, +18 dB SNR boost!). Sigma-clipping automatically discards transient seeing artifacts.",
                        badgeColor = CelestialBlue
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    GuideStepCard(
                        step = "5",
                        icon = Icons.Default.AutoAwesome,
                        title = "Wavelets & ADC Color Alignment",
                        description = "Raw stacks look smooth but soft. RegiStax-style multi-scale wavelets extract fine detail layers (Cassini division, Great Red Spot swirls). Atmospheric Dispersion Correction (ADC) cancels atmospheric prism color fringing.",
                        badgeColor = Color(0xFFF72585)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("observatory_guide_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    GuideHeroCard()
                }

                item {
                    GuideStepCard(
                        step = "1",
                        icon = Icons.Default.Videocam,
                        title = "Capture Video in AVI Format",
                        description = "Record uncompressed or high-quality AVI video with planetary cameras (ZWO ASI, QHY, Celestron) or webcams using capture tools like FireCapture or SharpCap. Typical captures contain 1,000–5,000 frames at 30–120 FPS.",
                        badgeColor = NebulaCyan
                    )
                }

                item {
                    GuideStepCard(
                        step = "2",
                        icon = Icons.Default.LensBlur,
                        title = "Sharpness, Contrast & Jitter Analysis",
                        description = "Every video frame is evaluated across three seeing dimensions: Laplacian edge sharpness (detecting crisp detail moments), RMS planetary contrast (separating faint cloud belts from haze), and atmospheric jitter tracking (measuring wavefront wobble and limb distortion). Frames are assigned a composite quality score and ranked into an AutoStakkert-style curve.",
                        badgeColor = SeeingEmerald
                    )
                }

                item {
                    GuideStepCard(
                        step = "3",
                        icon = Icons.Default.CenterFocusStrong,
                        title = "Centroid Drift Alignment",
                        description = "Planets drift across the sensor due to telescope mount tracking errors and wind. PlanetStack calculates the intensity center of gravity for each frame, translating all frames to common coordinates.",
                        badgeColor = StarGold
                    )
                }

                item {
                    GuideStepCard(
                        step = "4",
                        icon = Icons.Default.Compress,
                        title = "Frame Stacking (Mean & Sigma-Clip)",
                        description = "Combining N frames reduces CMOS readout noise by √N (e.g. 64 frames yields an 8× noise reduction, +18 dB SNR boost!). Sigma-clipping automatically discards transient seeing artifacts.",
                        badgeColor = CelestialBlue
                    )
                }

                item {
                    GuideStepCard(
                        step = "5",
                        icon = Icons.Default.AutoAwesome,
                        title = "Wavelets & ADC Color Alignment",
                        description = "Raw stacks look smooth but soft. RegiStax-style multi-scale wavelets extract fine detail layers (Cassini division, Great Red Spot swirls). Atmospheric Dispersion Correction (ADC) cancels atmospheric prism color fringing.",
                        badgeColor = Color(0xFFF72585)
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideHeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ObservatorySurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObservatoryCardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(NebulaCyan.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NebulaCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Planetary Imaging Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "How to turn telescope AVI videos into razor-sharp images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Ground-based telescopes constantly battle Earth's turbulent atmosphere (\"seeing\"), which distorts and blurs incoming planetary light. High-speed video recording captures rare moments when the air is steady, then stacks the best frames into one crisp image.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun GuideStepCard(
    step: String,
    icon: ImageVector,
    title: String,
    description: String,
    badgeColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObservatorySurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObservatoryCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(badgeColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
