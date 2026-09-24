package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CosmosDark
import com.example.ui.theme.NebulaCyan
import com.example.ui.theme.StarGold
import kotlin.math.roundToInt

/**
 * Interactive Split-Screen Comparison Slider with Full Zoom Control.
 * Lets astrophotographers compare the single best raw frame against
 * the final stacked and wavelet-sharpened planetary imaging result.
 * Includes interactive zoom controls (1.0x to 5.0x), quick zoom presets (1x, 2x, 3x, 4x),
 * pinch-to-zoom, double-tap zoom, and panning across zoomed details.
 */
@Composable
fun BeforeAfterSlider(
    rawBitmap: Bitmap,
    stackedBitmap: Bitmap,
    rawLabel: String = "Raw Single Frame",
    stackedLabel: String = "Stacked & Sharpened",
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.50f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val rawImageBitmap = remember(rawBitmap) { rawBitmap.asImageBitmap() }
    val stackedImageBitmap = remember(stackedBitmap) { stackedBitmap.asImageBitmap() }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.0f)
                .clip(RoundedCornerShape(16.dp))
                .background(CosmosDark)
                .testTag("before_after_slider")
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val splitX = widthPx * splitFraction

            // Pan limits based on current zoom
            val zoomedW = widthPx * zoomScale
            val zoomedH = heightPx * zoomScale
            val maxPanX = (zoomedW - widthPx) / 2f
            val maxPanY = (zoomedH - heightPx) / 2f
            val curPanX = panOffset.x.coerceIn(-maxPanX, maxPanX)
            val curPanY = panOffset.y.coerceIn(-maxPanY, maxPanY)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Double-tap to toggle 1x / 2.5x zoom
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (zoomScale > 1.2f) {
                                    zoomScale = 1.0f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.5f
                                    // Focus zoom on double-tap location
                                    val targetPanX = (widthPx / 2f - tapOffset.x) * 1.5f
                                    val targetPanY = (heightPx / 2f - tapOffset.y) * 1.5f
                                    panOffset = Offset(targetPanX, targetPanY)
                                }
                            }
                        )
                    }
                    // Pinch to zoom and pan gestures on the image
                    .pointerInput(zoomScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newZoom = (zoomScale * zoom).coerceIn(1.0f, 5.0f)
                            zoomScale = newZoom
                            if (newZoom > 1.0f) {
                                val nMaxPanX = (widthPx * newZoom - widthPx) / 2f
                                val nMaxPanY = (heightPx * newZoom - heightPx) / 2f
                                panOffset = Offset(
                                    x = (panOffset.x + pan.x).coerceIn(-nMaxPanX, nMaxPanX),
                                    y = (panOffset.y + pan.y).coerceIn(-nMaxPanY, nMaxPanY)
                                )
                            } else {
                                panOffset = Offset.Zero
                            }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val zWidth = canvasWidth * zoomScale
                val zHeight = canvasHeight * zoomScale
                val startX = (canvasWidth - zWidth) / 2f + curPanX
                val startY = (canvasHeight - zHeight) / 2f + curPanY

                // 1. Draw right side: Stacked & Sharpened (transformed with zoom & pan)
                drawImage(
                    image = stackedImageBitmap,
                    dstOffset = IntOffset(startX.roundToInt(), startY.roundToInt()),
                    dstSize = IntSize(zWidth.roundToInt(), zHeight.roundToInt())
                )

                // 2. Draw left side: Raw Single Frame (clipped to splitX, transformed identically)
                val clipRect = Rect(0f, 0f, splitX, canvasHeight)
                drawContext.canvas.save()
                drawContext.canvas.clipRect(clipRect)
                drawImage(
                    image = rawImageBitmap,
                    dstOffset = IntOffset(startX.roundToInt(), startY.roundToInt()),
                    dstSize = IntSize(zWidth.roundToInt(), zHeight.roundToInt())
                )
                drawContext.canvas.restore()

                // 3. Draw divider line
                drawLine(
                    color = Color.White.copy(alpha = 0.90f),
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, canvasHeight),
                    strokeWidth = 2.5.dp.toPx()
                )
            }

            // Draggable Handle on the divider
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (splitX - 22.dp.toPx()).roundToInt(),
                            (heightPx / 2f - 22.dp.toPx()).roundToInt()
                        )
                    }
                    .size(44.dp)
                    .pointerInput(widthPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            splitFraction = (splitFraction + dragAmount.x / widthPx).coerceIn(0.05f, 0.95f)
                        }
                    }
                    .background(StarGold, CircleShape)
                    .testTag("slider_divider_handle"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Drag comparison divider",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(90f)
                )
            }

            // Left Label (Raw Frame)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.70f)
            ) {
                Text(
                    text = rawLabel,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Right Label (Stacked & Sharpened)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(6.dp),
                color = NebulaCyan.copy(alpha = 0.85f)
            ) {
                Text(
                    text = stackedLabel,
                    color = Color(0xFF001F33),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Instruction Footer inside viewport
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    text = if (zoomScale > 1.05f) "◄ Drag handle to compare • Drag image to pan ►" else "◄ Drag handle to compare • Double-tap or zoom below ►",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Dedicated Zoom Control Bar beneath the slider
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0B1426),
            border = BorderStroke(1.dp, Color(0xFF1E2E4A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Zoom Out Button
                IconButton(
                    onClick = {
                        val newZoom = (zoomScale - 0.5f).coerceAtLeast(1.0f)
                        zoomScale = newZoom
                        if (newZoom <= 1.0f) panOffset = Offset.Zero
                    },
                    enabled = zoomScale > 1.0f,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("slider_zoom_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom out comparison",
                        tint = if (zoomScale > 1.0f) Color.White else Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Quick Zoom Preset Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZoomPresetChip(
                        label = "1x",
                        isSelected = kotlin.math.abs(zoomScale - 1.0f) < 0.1f,
                        testTag = "slider_zoom_chip_1x",
                        onClick = {
                            zoomScale = 1.0f
                            panOffset = Offset.Zero
                        }
                    )
                    ZoomPresetChip(
                        label = "2x",
                        isSelected = kotlin.math.abs(zoomScale - 2.0f) < 0.1f,
                        testTag = "slider_zoom_chip_2x",
                        onClick = {
                            zoomScale = 2.0f
                        }
                    )
                    ZoomPresetChip(
                        label = "3x",
                        isSelected = kotlin.math.abs(zoomScale - 3.0f) < 0.1f,
                        testTag = "slider_zoom_chip_3x",
                        onClick = {
                            zoomScale = 3.0f
                        }
                    )
                    ZoomPresetChip(
                        label = "4x",
                        isSelected = kotlin.math.abs(zoomScale - 4.0f) < 0.1f,
                        testTag = "slider_zoom_chip_4x",
                        onClick = {
                            zoomScale = 4.0f
                        }
                    )
                }

                // Zoom In Button
                IconButton(
                    onClick = {
                        val newZoom = (zoomScale + 0.5f).coerceAtMost(5.0f)
                        zoomScale = newZoom
                    },
                    enabled = zoomScale < 5.0f,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("slider_zoom_in_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom in comparison",
                        tint = if (zoomScale < 5.0f) Color.White else Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom Level Badge & Reset
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%.1f", zoomScale)}x",
                        color = NebulaCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("slider_zoom_level_text")
                    )

                    AnimatedVisibility(
                        visible = zoomScale > 1.05f,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(
                            onClick = {
                                zoomScale = 1.0f
                                panOffset = Offset.Zero
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("slider_zoom_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset zoom",
                                tint = StarGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomPresetChip(
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = if (isSelected) NebulaCyan else Color(0xFF132238),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (isSelected) NebulaCyan else Color(0xFF223854))
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFF001F33) else Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

