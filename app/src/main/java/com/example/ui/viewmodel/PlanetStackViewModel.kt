package com.example.ui.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PlanetStackDatabase
import com.example.data.db.StackedProjectRepository
import com.example.data.model.PlanetaryFrame
import com.example.data.model.StackedProject
import com.example.data.model.StackingConfig
import com.example.data.model.StackingMethod
import com.example.data.sample.PlanetarySimulations
import com.example.engine.AviParser
import com.example.engine.CentroidAligner
import com.example.engine.FrameQualityAnalyzer
import com.example.engine.PlanetaryStacker
import com.example.engine.SeeingScorer
import com.example.engine.SubPixelAligner
import com.example.engine.PlanetaryFeature
import com.example.engine.WaveletProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class AppStage {
    IDLE,
    ANALYZING,
    REVIEW,
    STACKING,
    RESULT
}

class PlanetStackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StackedProjectRepository
    init {
        val db = PlanetStackDatabase.getDatabase(application)
        repository = StackedProjectRepository(db.stackedProjectDao())
    }

    val savedProjects: StateFlow<List<StackedProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _stage = MutableStateFlow(AppStage.IDLE)
    val stage: StateFlow<AppStage> = _stage.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // Video & Frames data
    private val _targetName = MutableStateFlow("Planetary AVI Capture")
    val targetName: StateFlow<String> = _targetName.asStateFlow()

    private val _videoStreamInfo = MutableStateFlow<AviParser.VideoStreamInfo?>(null)
    val videoStreamInfo: StateFlow<AviParser.VideoStreamInfo?> = _videoStreamInfo.asStateFlow()

    private val _videoAnalysisReport = MutableStateFlow<AviParser.VideoAnalysisReport?>(null)
    val videoAnalysisReport: StateFlow<AviParser.VideoAnalysisReport?> = _videoAnalysisReport.asStateFlow()

    private val _detectedPlanetaryFeatures = MutableStateFlow<List<PlanetaryFeature>>(emptyList())
    val detectedPlanetaryFeatures: StateFlow<List<PlanetaryFeature>> = _detectedPlanetaryFeatures.asStateFlow()

    private val _allFrames = MutableStateFlow<List<PlanetaryFrame>>(emptyList())
    val allFrames: StateFlow<List<PlanetaryFrame>> = _allFrames.asStateFlow()

    private val _sortedScores = MutableStateFlow<List<Float>>(emptyList())
    val sortedScores: StateFlow<List<Float>> = _sortedScores.asStateFlow()

    private val _bestRawFrame = MutableStateFlow<Bitmap?>(null)
    val bestRawFrame: StateFlow<Bitmap?> = _bestRawFrame.asStateFlow()

    // Stacking Configuration
    private val _config = MutableStateFlow(StackingConfig())
    val config: StateFlow<StackingConfig> = _config.asStateFlow()

    // Results
    private val _rawStackedBitmap = MutableStateFlow<Bitmap?>(null)
    val rawStackedBitmap: StateFlow<Bitmap?> = _rawStackedBitmap.asStateFlow()

    private val _finalProcessedBitmap = MutableStateFlow<Bitmap?>(null)
    val finalProcessedBitmap: StateFlow<Bitmap?> = _finalProcessedBitmap.asStateFlow()

    private val _stackingResult = MutableStateFlow<PlanetaryStacker.StackingResult?>(null)
    val stackingResult: StateFlow<PlanetaryStacker.StackingResult?> = _stackingResult.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var waveletJob: Job? = null

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun updateConfig(newConfig: StackingConfig) {
        _config.value = newConfig
        if (_stage.value == AppStage.RESULT && _rawStackedBitmap.value != null) {
            triggerWaveletProcessing(newConfig)
        }
    }

    fun setStackPercentage(percent: Int) {
        _config.value = _config.value.copy(stackPercentage = percent.coerceIn(5, 100))
    }

    fun setStackingMethod(method: StackingMethod) {
        _config.value = _config.value.copy(method = method)
    }

    fun setSubPixelAlignment(enabled: Boolean) {
        _config.value = _config.value.copy(subPixelAlignment = enabled)
    }

    fun setAlignPlanet(enabled: Boolean) {
        _config.value = _config.value.copy(alignPlanet = enabled)
    }

    /**
     * Ingest a video file provided by the user via file picker.
     * Performs full video stream analysis, reports format details, and extracts up to 5,000 frames.
     */
    fun loadUserAvi(uri: Uri, displayName: String = "User Video") {
        viewModelScope.launch {
            val cleanName = displayName.substringBeforeLast(".")
            _targetName.value = cleanName
            _stage.value = AppStage.ANALYZING
            _statusMessage.value = "Analyzing video container, codec & stream format..."
            _progress.value = 0.05f

            val context = getApplication<Application>()
            try {
                // Step 1: Deep video container & format analysis
                val report = AviParser.analyzeVideo(context, uri, displayName)
                _videoAnalysisReport.value = report
                _videoStreamInfo.value = AviParser.VideoStreamInfo(
                    formatDescription = report.formatDescription,
                    bitDepth = report.bitDepth,
                    width = report.width,
                    height = report.height,
                    frameCount = report.totalFramesEstimated,
                    codec = report.codec,
                    fps = report.fps
                )

                _statusMessage.value = "Format: ${report.containerType} • ${report.codec} (${report.width}x${report.height}, ~${report.totalFramesEstimated} frames @ ${String.format("%.1f", report.fps)} FPS). Extracting up to 5,000 frames..."
                _progress.value = 0.15f

                // Step 2: Extract up to 5,000 frames from the analyzed video
                val extraction = AviParser.extractFramesWithMetadata(context, uri, maxFrames = 5000) { current, total ->
                    _progress.value = 0.15f + (current.toFloat() / total.coerceAtLeast(1)) * 0.45f
                    _statusMessage.value = "Extracted $current / $total frames from ${report.codec}..."
                }

                if (extraction.frames.isEmpty()) {
                    _userMessage.value = "Could not decode frames from the selected video (${report.codec}). Check container codec."
                    _stage.value = AppStage.IDLE
                    return@launch
                }

                _videoStreamInfo.value = extraction.videoInfo
                processExtractedBitmaps(extraction.frames)
            } catch (e: Exception) {
                Log.e("PlanetStackVM", "Error loading user video", e)
                _userMessage.value = "Error analyzing video: ${e.message}"
                _stage.value = AppStage.IDLE
            }
        }
    }

    /**
     * Load a pre-recorded planetary capture session (creates real AVI and processes it).
     */
    fun loadSampleTarget(target: PlanetarySimulations.CelestialTarget) {
        viewModelScope.launch {
            _targetName.value = target.displayName
            _stage.value = AppStage.ANALYZING
            _statusMessage.value = "Generating telescope AVI capture of ${target.displayName}..."
            _progress.value = 0.05f

            val context = getApplication<Application>()
            try {
                // Generate genuine RIFF AVI video on device storage
                val aviFile = PlanetarySimulations.generateSampleAvi(context, target) { curr, total ->
                    _progress.value = 0.05f + (curr.toFloat() / total) * 0.25f
                    _statusMessage.value = "Synthesizing planetary capture: $curr/$total frames..."
                }

                _statusMessage.value = "Analyzing video container & streams..."
                val aviUri = Uri.fromFile(aviFile)
                val report = AviParser.analyzeVideo(context, aviUri, "${target.displayName}.avi")
                _videoAnalysisReport.value = report

                _statusMessage.value = "Extracting frames from ${report.codec} (${report.width}x${report.height})..."
                val extraction = AviParser.extractFramesWithMetadata(context, aviUri, maxFrames = 5000) { curr, total ->
                    _progress.value = 0.30f + (curr.toFloat() / total) * 0.30f
                }

                _videoStreamInfo.value = extraction.videoInfo
                processExtractedBitmaps(extraction.frames)
            } catch (e: Exception) {
                Log.e("PlanetStackVM", "Error loading sample target", e)
                _userMessage.value = "Simulation error: ${e.message}"
                _stage.value = AppStage.IDLE
            }
        }
    }

    private suspend fun processExtractedBitmaps(bitmaps: List<Bitmap>) {
        _statusMessage.value = "Analyzing frames for sharpness, contrast & atmospheric jitter..."
        _progress.value = 0.65f

        // Analyze frames using the FrameQualityAnalyzer utility & service
        val analysisResults = FrameQualityAnalyzer.analyzeFrames(bitmaps) { curr, count ->
            _progress.value = 0.65f + (curr.toFloat() / count) * 0.25f
        }

        _statusMessage.value = "Detecting planetary features & performing sub-pixel registration..."
        _progress.value = 0.88f

        // Find reference frame (frame with highest composite quality score)
        val bestIdx = analysisResults.indices.maxByOrNull { analysisResults[it].compositeQualityScore } ?: 0
        val refBitmap = bitmaps[bestIdx]

        // Detect high-contrast planetary features / alignment points (APs)
        val features = SubPixelAligner.detectPlanetaryFeatures(refBitmap)
        _detectedPlanetaryFeatures.value = features

        // Perform sub-pixel cross-correlation & parabolic peak registration
        val registrationResults = SubPixelAligner.registerFrames(bitmaps, referenceIndex = bestIdx) { curr, count ->
            _progress.value = 0.88f + (curr.toFloat() / count) * 0.10f
            _statusMessage.value = "Sub-pixel registering frame $curr / $count..."
        }

        // Wrap into PlanetaryFrame objects with detailed quality metrics & sub-pixel alignment
        val framesList = bitmaps.mapIndexed { i, bmp ->
            val metrics = analysisResults[i]
            val reg = registrationResults.getOrNull(i)
            val subShiftX = reg?.subShiftX ?: 0f
            val subShiftY = reg?.subShiftY ?: 0f
            val shiftX = reg?.coarseShiftX ?: 0
            val shiftY = reg?.coarseShiftY ?: 0
            val regScore = reg?.correlationScore ?: 1.0f
            val featCount = reg?.trackedFeaturesCount ?: features.size

            PlanetaryFrame(
                index = i,
                timestampMs = (i * 33.3).toLong(),
                bitmap = bmp,
                qualityScore = metrics.compositeQualityScore,
                sharpnessScore = metrics.sharpnessScore,
                contrastScore = metrics.contrastScore,
                jitterScore = metrics.jitterScore,
                jitterOffsetPx = metrics.jitterOffsetPx,
                centroidX = metrics.centroid.x,
                centroidY = metrics.centroid.y,
                shiftX = shiftX,
                shiftY = shiftY,
                subShiftX = subShiftX,
                subShiftY = subShiftY,
                registrationScore = regScore,
                featurePointsCount = featCount
            )
        }

        // Rank frames from best to lowest
        val ranked = framesList.sortedByDescending { it.qualityScore }
        ranked.forEachIndexed { rank, frame ->
            frame.rank = rank + 1
        }

        val sortedScoresList = ranked.map { it.qualityScore }

        _allFrames.value = framesList
        _sortedScores.value = sortedScoresList
        _bestRawFrame.value = bitmaps[bestIdx]
        _stage.value = AppStage.REVIEW
        _progress.value = 1.0f
        _statusMessage.value = "Frames analyzed. Set stacking percentage."
    }

    /**
     * Stacks the selected best frames according to current configuration.
     */
    fun performStacking() {
        val frames = _allFrames.value
        if (frames.isEmpty()) return

        viewModelScope.launch {
            _stage.value = AppStage.STACKING
            _statusMessage.value = "Aligning planetary frames and stack integration..."
            _progress.value = 0.1f

            val total = frames.size
            val pct = _config.value.stackPercentage
            val countToStack = ((pct / 100f) * total).roundToInt().coerceIn(1, total)

            // Select the top N sharpest frames
            val selectedFrames = frames.sortedByDescending { it.qualityScore }.take(countToStack)

            val result = PlanetaryStacker.stackFrames(
                selectedFrames = selectedFrames,
                totalFramesCount = total,
                method = _config.value.method,
                align = _config.value.alignPlanet,
                subPixelAlign = _config.value.subPixelAlignment
            ) { prog ->
                _progress.value = 0.1f + prog * 0.7f
            }

            _stackingResult.value = result
            _rawStackedBitmap.value = result.stackedBitmap

            _statusMessage.value = "Applying wavelet detail extraction & ADC..."
            _progress.value = 0.9f

            // Apply wavelets and tone processing
            val finalBmp = WaveletProcessor.process(result.stackedBitmap, _config.value)
            _finalProcessedBitmap.value = finalBmp

            _stage.value = AppStage.RESULT
            _userMessage.value = "Stacking complete! ${result.framesStacked} frames integrated (+${String.format("%.1f", result.snrBoostDb)} dB SNR)"
        }
    }

    private fun triggerWaveletProcessing(config: StackingConfig) {
        waveletJob?.cancel()
        val raw = _rawStackedBitmap.value ?: return

        waveletJob = viewModelScope.launch {
            // Small debounce for rapid slider movement
            delay(40)
            val processed = WaveletProcessor.process(raw, config)
            _finalProcessedBitmap.value = processed
        }
    }

    fun backToReview() {
        _stage.value = AppStage.REVIEW
    }

    fun startNewStack() {
        _stage.value = AppStage.IDLE
        _allFrames.value = emptyList()
        _sortedScores.value = emptyList()
        _bestRawFrame.value = null
        _rawStackedBitmap.value = null
        _finalProcessedBitmap.value = null
        _stackingResult.value = null
    }

    /**
     * Saves stacked image into device's public MediaStore (Pictures/PlanetStack)
     */
    fun exportToGallery() {
        val bitmap = _finalProcessedBitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "PlanetStack_${_targetName.value.replace(" ", "_")}_$timestamp.png"

            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PlanetStack")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }

                    _userMessage.value = "Saved image to Pictures/PlanetStack!"
                } else {
                    _userMessage.value = "Failed creating MediaStore entry."
                }
            } catch (e: Exception) {
                Log.e("PlanetStackVM", "Failed saving to gallery", e)
                _userMessage.value = "Error saving to gallery: ${e.message}"
            }
        }
    }

    /**
     * Saves the full project to Room Database with saved files.
     */
    fun saveProjectToDb() {
        val result = _stackingResult.value ?: return
        val finalBmp = _finalProcessedBitmap.value ?: return
        val rawBmp = _bestRawFrame.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val projectDir = File(context.filesDir, "saved_stacks").apply { mkdirs() }
            val id = System.currentTimeMillis()

            val finalFile = File(projectDir, "stacked_$id.png")
            FileOutputStream(finalFile).use { out ->
                finalBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val rawFile = File(projectDir, "raw_$id.png")
            FileOutputStream(rawFile).use { out ->
                rawBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val project = StackedProject(
                title = _targetName.value,
                targetPlanet = _targetName.value,
                totalFrames = result.totalFrames,
                stackedFrames = result.framesStacked,
                stackingMethod = _config.value.method.displayName,
                qualityScoreAvg = result.avgQuality,
                imagePath = finalFile.absolutePath,
                rawBestImagePath = rawFile.absolutePath,
                snrBoostDb = result.snrBoostDb,
                waveletFine = _config.value.waveletFine,
                waveletMedium = _config.value.waveletMedium,
                waveletCoarse = _config.value.waveletCoarse,
                contrast = _config.value.contrast,
                saturation = _config.value.saturation,
                brightness = _config.value.brightness,
                redShiftX = _config.value.redShiftX,
                redShiftY = _config.value.redShiftY,
                blueShiftX = _config.value.blueShiftX,
                blueShiftY = _config.value.blueShiftY
            )

            repository.saveProject(project)
            _userMessage.value = "Project saved to Observatory Gallery!"
        }
    }

    fun deleteProject(project: StackedProject) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(project.imagePath).delete()
                File(project.rawBestImagePath).delete()
            } catch (_: Exception) {}
            repository.deleteProject(project)
        }
    }

    fun openSavedProject(project: StackedProject) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalBmp = BitmapFactory.decodeFile(project.imagePath)
            val rawBmp = BitmapFactory.decodeFile(project.rawBestImagePath)

            if (finalBmp != null && rawBmp != null) {
                _targetName.value = project.title
                _rawStackedBitmap.value = finalBmp
                _finalProcessedBitmap.value = finalBmp
                _bestRawFrame.value = rawBmp

                _config.value = StackingConfig(
                    stackPercentage = ((project.stackedFrames.toFloat() / project.totalFrames) * 100).roundToInt(),
                    waveletFine = project.waveletFine,
                    waveletMedium = project.waveletMedium,
                    waveletCoarse = project.waveletCoarse,
                    contrast = project.contrast,
                    saturation = project.saturation,
                    brightness = project.brightness,
                    redShiftX = project.redShiftX,
                    redShiftY = project.redShiftY,
                    blueShiftX = project.blueShiftX,
                    blueShiftY = project.blueShiftY
                )

                _stackingResult.value = PlanetaryStacker.StackingResult(
                    stackedBitmap = finalBmp,
                    framesStacked = project.stackedFrames,
                    totalFrames = project.totalFrames,
                    snrBoostDb = project.snrBoostDb,
                    avgQuality = project.qualityScoreAvg
                )

                _stage.value = AppStage.RESULT
            } else {
                _userMessage.value = "Could not load saved project files."
            }
        }
    }
}
