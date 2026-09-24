package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StackingMethod
import com.example.data.sample.PlanetarySimulations
import com.example.engine.PlanetaryFeature
import com.example.ui.components.BeforeAfterSlider
import com.example.ui.components.QualityHistogramChart
import com.example.ui.components.QualityGraphView
import com.example.ui.components.WaveletControlPanel
import com.example.ui.theme.CelestialBlue
import com.example.ui.theme.CosmosDark
import com.example.ui.theme.HydrogenAlphaRose
import com.example.ui.theme.NebulaCyan
import com.example.ui.theme.ObservatoryCardBorder
import com.example.ui.theme.ObservatorySurface
import com.example.ui.theme.ObservatorySurfaceVariant
import com.example.ui.theme.SeeingAmber
import com.example.ui.theme.SeeingEmerald
import com.example.ui.theme.StarGold
import com.example.ui.viewmodel.AppStage
import com.example.ui.viewmodel.PlanetStackViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PlanetStackViewModel) {
    val context = LocalContext.current
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val targetName by viewModel.targetName.collectAsStateWithLifecycle()
    val allFrames by viewModel.allFrames.collectAsStateWithLifecycle()
    val sortedScores by viewModel.sortedScores.collectAsStateWithLifecycle()
    val bestRawFrame by viewModel.bestRawFrame.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val finalProcessedBitmap by viewModel.finalProcessedBitmap.collectAsStateWithLifecycle()
    val stackingResult by viewModel.stackingResult.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val videoStreamInfo by viewModel.videoStreamInfo.collectAsStateWithLifecycle()
    val videoAnalysisReport by viewModel.videoAnalysisReport.collectAsStateWithLifecycle()
    val detectedFeatures by viewModel.detectedPlanetaryFeatures.collectAsStateWithLifecycle()

    var selectedNavTab by remember { mutableIntStateOf(0) } // 0 = Stacker, 1 = Gallery, 2 = Guide
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // Video File Picker Launcher for AVI videos
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video.avi"
            viewModel.loadUserAvi(uri, fileName)
            selectedNavTab = 0
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(CosmosDark)) {
        val isTablet = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Adaptive Tablet NavigationRail
            if (isTablet) {
                NavigationRail(
                    containerColor = ObservatorySurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(NebulaCyan.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Science,
                                    contentDescription = null,
                                    tint = NebulaCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "PlanetStack",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NebulaCyan
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .border(
                            BorderStroke(1.dp, ObservatoryCardBorder)
                        )
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationRailItem(
                        selected = selectedNavTab == 0,
                        onClick = { selectedNavTab = 0 },
                        icon = { Icon(Icons.Default.Layers, contentDescription = "Stacker") },
                        label = { Text("Stacker") },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF001B2B),
                            selectedTextColor = NebulaCyan,
                            indicatorColor = NebulaCyan,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_stacker")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    NavigationRailItem(
                        selected = selectedNavTab == 1,
                        onClick = { selectedNavTab = 1 },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Saved Stacks") },
                        label = { Text("Gallery (${savedProjects.size})") },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF241800),
                            selectedTextColor = StarGold,
                            indicatorColor = StarGold,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_gallery")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    NavigationRailItem(
                        selected = selectedNavTab == 2,
                        onClick = { selectedNavTab = 2 },
                        icon = { Icon(Icons.Default.HelpOutline, contentDescription = "Observatory Guide") },
                        label = { Text("Guide") },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CelestialBlue,
                            indicatorColor = CelestialBlue,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_guide")
                    )
                }
            }

            // Main Scaffold
            Scaffold(
                containerColor = CosmosDark,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isTablet) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(NebulaCyan.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Science,
                                            contentDescription = null,
                                            tint = NebulaCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = if (isTablet) "PlanetStack Observatory" else "PlanetStack",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = ObservatorySurface
                        ),
                        navigationIcon = {
                            if (selectedNavTab == 0 && stage == AppStage.RESULT) {
                                IconButton(
                                    onClick = { viewModel.backToReview() },
                                    modifier = Modifier.testTag("back_to_review_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back to frame selection",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else if (selectedNavTab != 0) {
                                IconButton(onClick = { selectedNavTab = 0 }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back to Stacker",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        actions = {
                            if (selectedNavTab == 0 && (stage == AppStage.REVIEW || stage == AppStage.RESULT)) {
                                IconButton(
                                    onClick = { viewModel.startNewStack() },
                                    modifier = Modifier.testTag("new_video_action")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Load new video",
                                        tint = StarGold
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    // Only show bottom navigation bar on phones
                    if (!isTablet) {
                        NavigationBar(
                            containerColor = ObservatorySurface,
                            tonalElevation = 4.dp
                        ) {
                            NavigationBarItem(
                                selected = selectedNavTab == 0,
                                onClick = { selectedNavTab = 0 },
                                icon = { Icon(Icons.Default.Layers, contentDescription = "Stacker") },
                                label = { Text("Stacker") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF001B2B),
                                    selectedTextColor = NebulaCyan,
                                    indicatorColor = NebulaCyan,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_stacker")
                            )

                            NavigationBarItem(
                                selected = selectedNavTab == 1,
                                onClick = { selectedNavTab = 1 },
                                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Saved Stacks") },
                                label = { Text("Gallery (${savedProjects.size})") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF241800),
                                    selectedTextColor = StarGold,
                                    indicatorColor = StarGold,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_gallery")
                            )

                            NavigationBarItem(
                                selected = selectedNavTab == 2,
                                onClick = { selectedNavTab = 2 },
                                icon = { Icon(Icons.Default.HelpOutline, contentDescription = "Observatory Guide") },
                                label = { Text("Guide") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = CelestialBlue,
                                    indicatorColor = CelestialBlue,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_guide")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedNavTab) {
                        1 -> {
                            GalleryScreen(
                                projects = savedProjects,
                                onOpenProject = { proj ->
                                    viewModel.openSavedProject(proj)
                                    selectedNavTab = 0
                                },
                                onDeleteProject = { proj ->
                                    viewModel.deleteProject(proj)
                                }
                            )
                        }

                        2 -> {
                            ObservatoryGuideScreen()
                        }

                        0 -> {
                            // Stacker flow
                            when (stage) {
                                AppStage.IDLE -> {
                                    IdleCapturePicker(
                                        isTablet = isTablet,
                                        onPickUserFile = {
                                            filePickerLauncher.launch(arrayOf("video/*", "video/x-msvideo", "video/avi", "*/*"))
                                        },
                                        onSelectPreset = { target ->
                                            viewModel.loadSampleTarget(target)
                                        }
                                    )
                                }

                                AppStage.ANALYZING, AppStage.STACKING -> {
                                    ProcessingScreen(
                                        stageTitle = if (stage == AppStage.ANALYZING) "Analyzing Planetary AVI Video" else "Planetary Frame Stacking",
                                        status = statusMessage,
                                        progress = progress
                                    )
                                }

                                AppStage.REVIEW -> {
                                    ReviewFramesScreen(
                                        isTablet = isTablet,
                                        targetName = targetName,
                                        frames = allFrames,
                                        sortedScores = sortedScores,
                                        videoStreamInfo = videoStreamInfo,
                                        videoAnalysisReport = videoAnalysisReport,
                                        detectedFeatures = detectedFeatures,
                                        config = config,
                                        onPercentageChange = { viewModel.setStackPercentage(it) },
                                        onMethodChange = { viewModel.setStackingMethod(it) },
                                        onAlignToggle = { viewModel.updateConfig(config.copy(alignPlanet = it)) },
                                        onSubPixelAlignToggle = { viewModel.setSubPixelAlignment(it) },
                                        onStartStack = { viewModel.performStacking() }
                                    )
                                }

                                AppStage.RESULT -> {
                                    finalProcessedBitmap?.let { stackedBmp ->
                                        bestRawFrame?.let { rawBmp ->
                                            ResultScreen(
                                                isTablet = isTablet,
                                                targetName = targetName,
                                                rawBitmap = rawBmp,
                                                stackedBitmap = stackedBmp,
                                                result = stackingResult,
                                                config = config,
                                                onConfigChange = { viewModel.updateConfig(it) },
                                                onSaveToGallery = { viewModel.exportToGallery() },
                                                onSaveProject = { viewModel.saveProjectToDb() }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Initial Idle Screen: lets user open an AVI from their device or test with pre-recorded planetary capture sessions.
 * Adapts to phone (single column) and tablet (2-column side-by-side layout).
 */
@Composable
private fun IdleCapturePicker(
    isTablet: Boolean,
    onPickUserFile: () -> Unit,
    onSelectPreset: (PlanetarySimulations.CelestialTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isTablet) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp)
                .testTag("idle_screen"),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Column: Hero Header & File Import
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = ObservatorySurfaceVariant,
                        border = BorderStroke(1.dp, ObservatoryCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(NebulaCyan.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Science,
                                        contentDescription = null,
                                        tint = NebulaCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Planetary Imaging Stacker",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Astronomical video stacker for phones & tablets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NebulaCyan
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Import telescope or astro camera videos (uncompressed RGB 24bpp, 8-bit Indexed, RAW8 Bayer CFA, AVI/MP4). PlanetStack automatically analyzes video container and codec details, extracts up to 5,000 frames, grades seeing sharpness, and stacks the finest frames into razor-sharp planetary astrophotographs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 21.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Primary Button: Open Device Video
                            Button(
                                onClick = onPickUserFile,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("select_avi_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NebulaCyan,
                                    contentColor = Color(0xFF001B2B)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Analyze & Stack Video (Up to 5,000 Frames)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                item {
                    // Supported Formats Spec Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF09101E),
                        border = BorderStroke(1.dp, NebulaCyan.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Supported Astrophotography Capture Formats",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = StarGold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Uncompressed RGB 24bpp (DIB / BGR24) from FireCapture / SharpCap\n• 8-bit Indexed Palette (256-color BMP/AVI)\n• RAW8 Bayer CFA Planetary Stream\n• High-speed AVI & MP4 Containers (up to 5,000 frames)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Right Column: Simulated Telescope Sessions
            LazyColumn(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Simulated Telescope AVI Sessions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                items(PlanetarySimulations.CelestialTarget.entries.size) { index ->
                    val target = PlanetarySimulations.CelestialTarget.entries[index]
                    PresetTargetCard(
                        target = target,
                        onClick = { onSelectPreset(target) }
                    )
                }
            }
        }
    } else {
        // Phone Layout: Single column vertical scroll
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("idle_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ObservatorySurfaceVariant,
                    border = BorderStroke(1.dp, ObservatoryCardBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Planetary Imaging Stacker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import telescope or astro camera videos (uncompressed RGB 24bpp, 8-bit Indexed, RAW8 Bayer CFA, AVI/MP4). PlanetStack automatically analyzes video container and codec details, extracts up to 5,000 frames, grades seeing sharpness, and stacks the finest frames into razor-sharp planetary astrophotographs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Primary Button: Open Device Video
                        Button(
                            onClick = onPickUserFile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("select_avi_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NebulaCyan,
                                contentColor = Color(0xFF001B2B)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Analyze & Stack Video (Up to 5,000 Frames)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Or Test with Simulated Telescope AVI Sessions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            items(PlanetarySimulations.CelestialTarget.entries.size) { index ->
                val target = PlanetarySimulations.CelestialTarget.entries[index]
                PresetTargetCard(
                    target = target,
                    onClick = { onSelectPreset(target) }
                )
            }
        }
    }
}

@Composable
private fun PresetTargetCard(
    target: PlanetarySimulations.CelestialTarget,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("preset_target_${target.name}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObservatorySurfaceVariant),
        border = BorderStroke(1.dp, ObservatoryCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        when (target) {
                            PlanetarySimulations.CelestialTarget.JUPITER -> Color(0xFFEBDAC3).copy(alpha = 0.25f)
                            PlanetarySimulations.CelestialTarget.SATURN -> StarGold.copy(alpha = 0.25f)
                            PlanetarySimulations.CelestialTarget.MARS -> HydrogenAlphaRose.copy(alpha = 0.25f)
                            PlanetarySimulations.CelestialTarget.MOON -> Color(0xFFE0E0E0).copy(alpha = 0.25f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = when (target) {
                        PlanetarySimulations.CelestialTarget.JUPITER -> StarGold
                        PlanetarySimulations.CelestialTarget.SATURN -> StarGold
                        PlanetarySimulations.CelestialTarget.MARS -> Color(0xFFFF6B6B)
                        PlanetarySimulations.CelestialTarget.MOON -> Color(0xFFE0E0E0)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = target.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${target.frameCount} frames • Seeing turbulence & drift simulation",
                    style = MaterialTheme.typography.labelSmall,
                    color = NebulaCyan
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Load capture",
                tint = NebulaCyan
            )
        }
    }
}

/**
 * Processing state (Extracting / Grading / Stacking).
 */
@Composable
private fun ProcessingScreen(
    stageTitle: String,
    status: String,
    progress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("processing_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(90.dp),
                    color = NebulaCyan,
                    strokeWidth = 6.dp,
                    trackColor = ObservatoryCardBorder
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = StarGold,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stageTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = StarGold,
                trackColor = ObservatoryCardBorder
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${(progress * 100).roundToInt()}% Complete",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = NebulaCyan
            )
        }
    }
}

/**
 * Frame review & stacking parameter selection screen.
 * Supports phone and tablet responsive layouts using pure native Jetpack Compose.
 */
@Composable
private fun ReviewFramesScreen(
    isTablet: Boolean,
    targetName: String,
    frames: List<com.example.data.model.PlanetaryFrame>,
    sortedScores: List<Float>,
    videoStreamInfo: com.example.engine.AviParser.VideoStreamInfo?,
    videoAnalysisReport: com.example.engine.AviParser.VideoAnalysisReport? = null,
    detectedFeatures: List<PlanetaryFeature> = emptyList(),
    config: com.example.data.model.StackingConfig,
    onPercentageChange: (Int) -> Unit,
    onMethodChange: (StackingMethod) -> Unit,
    onAlignToggle: (Boolean) -> Unit,
    onSubPixelAlignToggle: (Boolean) -> Unit = {},
    onStartStack: () -> Unit
) {
    val totalFrames = frames.size
    val selectedCount = ((config.stackPercentage / 100f) * totalFrames).roundToInt().coerceIn(1, totalFrames)

    if (isTablet) {
        // Tablet Canonical Two-Pane Split Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("review_screen"),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Video Information, Frame Inspector & Detected APs
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = targetName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$totalFrames frames decoded from video (supports up to 5,000 frames)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Video format card
                item {
                    VideoFormatReportCard(videoAnalysisReport, videoStreamInfo, totalFrames)
                }

                // Detected APs Banner
                if (detectedFeatures.isNotEmpty()) {
                    item {
                        DetectedFeaturesBanner(detectedFeatures)
                    }
                }

                // Frame Inspector Carousel
                item {
                    FrameInspector(
                        frames = frames.sortedByDescending { it.qualityScore },
                        selectedCutoffIndex = selectedCount,
                        videoStreamInfo = videoStreamInfo,
                        detectedFeatures = detectedFeatures
                    )
                }
            }

            // Right Pane: Native Quality Histogram, Slider, Algorithms, and Action Button
            LazyColumn(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Native Compose Quality Histogram & Cutoff Selector
                item {
                    QualityHistogramChart(
                        sortedScores = sortedScores,
                        selectedPercentage = config.stackPercentage,
                        onPercentageChange = onPercentageChange
                    )
                }

                // Percentage Slider & Quick Presets
                item {
                    PercentageSliderCard(
                        selectedCount = selectedCount,
                        totalFrames = totalFrames,
                        config = config,
                        onPercentageChange = onPercentageChange
                    )
                }

                // Stacking Algorithm & Alignment Controls
                item {
                    AlgorithmAndAlignmentCard(
                        config = config,
                        onMethodChange = onMethodChange,
                        onAlignToggle = onAlignToggle,
                        onSubPixelAlignToggle = onSubPixelAlignToggle
                    )
                }

                // Primary Action Button
                item {
                    Button(
                        onClick = onStartStack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_stack_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NebulaCyan,
                            contentColor = Color(0xFF001B2B)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Layers, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Stack Best $selectedCount Frames (${config.stackPercentage}%)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    } else {
        // Phone Layout: Single column vertical list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("review_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = targetName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$totalFrames frames decoded from video (supports up to 5,000 frames)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                VideoFormatReportCard(videoAnalysisReport, videoStreamInfo, totalFrames)
            }

            // Native Quality Histogram & Cutoff Selector
            item {
                QualityHistogramChart(
                    sortedScores = sortedScores,
                    selectedPercentage = config.stackPercentage,
                    onPercentageChange = onPercentageChange
                )
            }

            item {
                PercentageSliderCard(
                    selectedCount = selectedCount,
                    totalFrames = totalFrames,
                    config = config,
                    onPercentageChange = onPercentageChange
                )
            }

            item {
                AlgorithmAndAlignmentCard(
                    config = config,
                    onMethodChange = onMethodChange,
                    onAlignToggle = onAlignToggle,
                    onSubPixelAlignToggle = onSubPixelAlignToggle
                )
            }

            if (detectedFeatures.isNotEmpty()) {
                item {
                    DetectedFeaturesBanner(detectedFeatures)
                }
            }

            item {
                FrameInspector(
                    frames = frames.sortedByDescending { it.qualityScore },
                    selectedCutoffIndex = selectedCount,
                    videoStreamInfo = videoStreamInfo,
                    detectedFeatures = detectedFeatures
                )
            }

            item {
                Button(
                    onClick = onStartStack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_stack_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NebulaCyan,
                        contentColor = Color(0xFF001B2B)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Layers, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Stack Best $selectedCount Frames (${config.stackPercentage}%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun VideoFormatReportCard(
    videoAnalysisReport: com.example.engine.AviParser.VideoAnalysisReport?,
    videoStreamInfo: com.example.engine.AviParser.VideoStreamInfo?,
    totalFrames: Int
) {
    videoAnalysisReport?.let { report ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ObservatorySurfaceVariant,
            border = BorderStroke(1.dp, NebulaCyan.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(NebulaCyan.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = NebulaCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Video Format Analysis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = report.containerType,
                            style = MaterialTheme.typography.labelSmall,
                            color = NebulaCyan
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SeeingEmerald.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, SeeingEmerald.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Ready to Stack",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SeeingEmerald
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Codec / FourCC",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${report.codec} (${report.fourCC})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Format & Precision",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = report.formatDescription,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = NebulaCyan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Resolution & Depth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${report.width} × ${report.height} px • ${report.bitDepth}-bit",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Frame Rate & Extracted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", report.fps)} FPS • $totalFrames frames",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = StarGold
                        )
                    }

                    if (report.fileSizeBytes > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stream Size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val sizeStr = if (report.fileSizeBytes >= 1_000_000L) {
                                String.format(java.util.Locale.US, "%.2f MB", report.fileSizeBytes / 1_000_000.0)
                            } else {
                                String.format(java.util.Locale.US, "%.1f KB", report.fileSizeBytes / 1000.0)
                            }
                            Text(
                                text = sizeStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CosmosDark.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, ObservatoryCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NebulaCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = report.astrophotographySuitability,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    } ?: videoStreamInfo?.let { info ->
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = NebulaCyan.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, NebulaCyan.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = NebulaCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${info.formatDescription} • ${info.width}×${info.height} px • ${info.frameCount} frames",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NebulaCyan
                )
            }
        }
    }
}

@Composable
private fun PercentageSliderCard(
    selectedCount: Int,
    totalFrames: Int,
    config: com.example.data.model.StackingConfig,
    onPercentageChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ObservatorySurfaceVariant,
        border = BorderStroke(1.dp, ObservatoryCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Frames to Stack: $selectedCount of $totalFrames",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = StarGold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${config.stackPercentage}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NebulaCyan
                )
            }

            Slider(
                value = config.stackPercentage.toFloat(),
                onValueChange = { onPercentageChange(it.roundToInt()) },
                valueRange = 5f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = StarGold,
                    activeTrackColor = StarGold,
                    inactiveTrackColor = StarGold.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stack_percentage_slider")
            )

            // Quick percentage chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 25, 35, 50, 75).forEach { pct ->
                    FilterChip(
                        selected = config.stackPercentage == pct,
                        onClick = { onPercentageChange(pct) },
                        label = { Text("$pct%") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StarGold,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlgorithmAndAlignmentCard(
    config: com.example.data.model.StackingConfig,
    onMethodChange: (StackingMethod) -> Unit,
    onAlignToggle: (Boolean) -> Unit,
    onSubPixelAlignToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ObservatorySurfaceVariant,
        border = BorderStroke(1.dp, ObservatoryCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Integration Algorithm",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            StackingMethod.entries.forEach { method ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onMethodChange(method) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                if (config.method == method) NebulaCyan else Color.Transparent,
                                CircleShape
                            )
                            .border(2.dp, if (config.method == method) NebulaCyan else ObservatoryCardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (config.method == method) {
                            Box(modifier = Modifier.size(8.dp).background(Color.Black, CircleShape))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = method.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (config.method == method) NebulaCyan else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = method.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alignment Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAlignToggle(!config.alignPlanet) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = config.alignPlanet,
                    onCheckedChange = onAlignToggle,
                    colors = CheckboxDefaults.colors(checkedColor = NebulaCyan),
                    modifier = Modifier.testTag("align_planet_checkbox")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Centroid Planetary Alignment",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Locks onto planet center-of-gravity to eliminate mount tracking drift",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (config.alignPlanet) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSubPixelAlignToggle(!config.subPixelAlignment) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = config.subPixelAlignment,
                        onCheckedChange = onSubPixelAlignToggle,
                        colors = CheckboxDefaults.colors(checkedColor = StarGold),
                        modifier = Modifier.testTag("subpixel_alignment_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sub-Pixel Feature Registration",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = StarGold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = StarGold.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "0.05px NCC",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StarGold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Bilinear interpolation tracking surface bands & albedo features",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectedFeaturesBanner(detectedFeatures: List<PlanetaryFeature>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF09101E),
        border = BorderStroke(1.dp, StarGold.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Adjust,
                    contentDescription = null,
                    tint = StarGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Planetary Feature Alignment Points (${detectedFeatures.size} APs Locked)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = StarGold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Detected across planetary disk using local gradient variance. Cross-correlated for continuous sub-pixel shifts.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(detectedFeatures) { feat ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NebulaCyan.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, NebulaCyan.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = feat.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NebulaCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${feat.x.roundToInt()}, ${feat.y.roundToInt()})",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Result Screen: Before/After interactive slider, Wavelets, ADC, Telemetry, and Save Actions.
 * Adapts to phone (single column) and tablet (2-column canonical layout).
 */
@Composable
private fun ResultScreen(
    isTablet: Boolean,
    targetName: String,
    rawBitmap: android.graphics.Bitmap,
    stackedBitmap: android.graphics.Bitmap,
    result: com.example.engine.PlanetaryStacker.StackingResult?,
    config: com.example.data.model.StackingConfig,
    onConfigChange: (com.example.data.model.StackingConfig) -> Unit,
    onSaveToGallery: () -> Unit,
    onSaveProject: () -> Unit
) {
    if (isTablet) {
        // Tablet 2-Pane Split Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("result_screen"),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Before/After Comparison & Telemetry & Actions
            LazyColumn(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "$targetName Stacked",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    result?.let {
                        Text(
                            text = "${it.framesStacked} of ${it.totalFrames} frames integrated • +${String.format(java.util.Locale.US, "%.1f", it.snrBoostDb)} dB SNR Boost",
                            style = MaterialTheme.typography.bodySmall,
                            color = StarGold
                        )
                    }
                }

                // Interactive Before / After Split Slider
                item {
                    BeforeAfterSlider(
                        rawBitmap = rawBitmap,
                        stackedBitmap = stackedBitmap,
                        rawLabel = "Single Raw Frame",
                        stackedLabel = "Stacked (${result?.framesStacked ?: 0} Frames)"
                    )
                }

                // Telemetry Badges
                item {
                    ResultTelemetryRow(result = result)
                }

                // Export & Save Actions
                item {
                    ExportActionButtons(
                        onSaveToGallery = onSaveToGallery,
                        onSaveProject = onSaveProject
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Right Pane: Wavelet Sharpening & Post-Processing Suite
            LazyColumn(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    WaveletControlPanel(
                        config = config,
                        onConfigChange = onConfigChange
                    )
                }
            }
        }
    } else {
        // Phone Layout: Single column vertical list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("result_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$targetName Stacked",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        result?.let {
                            Text(
                                text = "${it.framesStacked} of ${it.totalFrames} frames integrated • +${String.format(java.util.Locale.US, "%.1f", it.snrBoostDb)} dB SNR",
                                style = MaterialTheme.typography.bodySmall,
                                color = StarGold
                            )
                        }
                    }
                }
            }

            // Interactive Before / After Split Slider
            item {
                BeforeAfterSlider(
                    rawBitmap = rawBitmap,
                    stackedBitmap = stackedBitmap,
                    rawLabel = "Single Raw Frame",
                    stackedLabel = "Stacked (${result?.framesStacked ?: 0} Frames)"
                )
            }

            // Telemetry Badges
            item {
                ResultTelemetryRow(result = result)
            }

            // Wavelet & ADC Post-Processing Suite
            item {
                WaveletControlPanel(
                    config = config,
                    onConfigChange = onConfigChange
                )
            }

            // Export & Save Actions
            item {
                ExportActionButtons(
                    onSaveToGallery = onSaveToGallery,
                    onSaveProject = onSaveProject
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ResultTelemetryRow(result: com.example.engine.PlanetaryStacker.StackingResult?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = ObservatorySurfaceVariant,
            border = BorderStroke(1.dp, ObservatoryCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "SNR BOOST", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "+${String.format(java.util.Locale.US, "%.1f", result?.snrBoostDb ?: 0f)} dB",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SeeingEmerald,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = ObservatorySurfaceVariant,
            border = BorderStroke(1.dp, ObservatoryCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "STACK SIZE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${result?.framesStacked ?: 0} / ${result?.totalFrames ?: 0}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = StarGold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = ObservatorySurfaceVariant,
            border = BorderStroke(1.dp, ObservatoryCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "AVG QUALITY", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${((result?.avgQuality ?: 0.5f) * 100).roundToInt()}%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NebulaCyan,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ExportActionButtons(
    onSaveToGallery: () -> Unit,
    onSaveProject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onSaveToGallery,
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("save_gallery_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = NebulaCyan,
                contentColor = Color(0xFF001B2B)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Image", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onSaveProject,
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .testTag("save_project_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = StarGold,
                contentColor = Color(0xFF241800)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Project", fontWeight = FontWeight.Bold)
        }
    }
}
