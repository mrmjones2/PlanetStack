package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles decoding and creating AVI video files with full support for:
 * 1. RGB 8-bit formats:
 *    - 8-bit per channel RGB (24bpp BGR/RGB uncompressed DIB)
 *    - 8-bit Indexed RGB (palettized 256-color DIB with RGBQUAD color table & RLE8)
 *    - 8-bit Bayer CFA (RAW8: RGGB, BGGR, GBRG, GRBG demosaiced to RGB)
 *    - 8-bit Packed truecolor (RGB332) and 32-bit (8-bit per channel RGBA/BGRx)
 * 2. Motion JPEG (MJPEG)
 * 3. Standard Android container codecs via MediaMetadataRetriever (8-bit ARGB_8888)
 * 4. AVI generator supporting 24bpp RGB, 8-bit Indexed RGB, and 8-bit Bayer CFA
 */
object AviParser {
    private const val TAG = "AviParser"

    enum class AviColorFormat(val displayName: String, val bitDepth: Int) {
        RGB_8BIT_24BPP("RGB 8-bit (24bpp BGR)", 24),
        RGB_8BIT_INDEXED("RGB 8-bit (Indexed / 256 Colors)", 8),
        RGB_8BIT_RAW8_BAYER("RGB 8-bit (RAW8 Bayer CFA)", 8)
    }

    data class VideoStreamInfo(
        val formatDescription: String,
        val bitDepth: Int,
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val codec: String,
        val fps: Double = 30.0
    )

    /**
     * In-depth Video Stream Format & Container Analysis Report.
     * Analyzes selected input video files (RIFF AVI, MP4, etc.) to report
     * format details, codec, container structure, resolution, bit depth,
     * estimated total frame count, framerate, duration, and astrophotography suitability.
     */
    data class VideoAnalysisReport(
        val fileName: String,
        val containerType: String,
        val codec: String,
        val fourCC: String,
        val formatDescription: String,
        val bitDepth: Int,
        val width: Int,
        val height: Int,
        val totalFramesEstimated: Int,
        val fps: Double,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val isAstroCameraNative: Boolean,
        val colorFormat: String,
        val astrophotographySuitability: String
    )

    data class ExtractionResult(
        val frames: List<Bitmap>,
        val videoInfo: VideoStreamInfo
    )

    data class AviMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val frameRate: Double,
        val codec: String
    )

    /**
     * Analyzes the selected video and returns a comprehensive format & stream report.
     * Supports astronomical camera uncompressed AVI streams (DIB, RAW8 Bayer, MJPEG)
     * as well as standard containers (MP4, MKV, etc.).
     */
    suspend fun analyzeVideo(
        context: Context,
        uri: Uri,
        displayName: String = "Selected Video"
    ): VideoAnalysisReport = withContext(Dispatchers.IO) {
        var fileSizeBytes = 0L
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                fileSizeBytes = pfd.statSize
            }
        } catch (_: Exception) {}

        // Attempt 1: Inspect native RIFF AVI headers
        var riffReport: VideoAnalysisReport? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { rawStream ->
                val bis = BufferedInputStream(rawStream)
                val header = ByteArray(12)
                if (readFully(bis, header, 12)) {
                    val riffTag = String(header, 0, 4, Charsets.US_ASCII)
                    val aviTag = String(header, 8, 4, Charsets.US_ASCII)
                    if (riffTag == "RIFF" && aviTag == "AVI ") {
                        var videoWidth = 0
                        var videoHeight = 0
                        var bitCount = 24
                        var biCompression = 0
                        var compressionFourCC = ""
                        var biClrUsed = 0
                        var totalFrames = 0
                        var microSecPerFrame = 33333

                        val chunkHeader = ByteArray(8)
                        var chunksInspected = 0
                        while (chunksInspected < 64) {
                            if (!readFully(bis, chunkHeader, 8)) break
                            val fcc = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                            val cSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                            if (cSize <= 0 || cSize > 50_000_000) break

                            when (fcc) {
                                "LIST" -> {
                                    val listType = ByteArray(4)
                                    readFully(bis, listType, 4)
                                }
                                "avih" -> {
                                    if (cSize >= 56) {
                                        val buf = ByteArray(cSize.toInt())
                                        readFully(bis, buf, cSize.toInt())
                                        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                                        microSecPerFrame = bb.int
                                        bb.int // maxBytesPerSec
                                        bb.int // padding
                                        bb.int // flags
                                        totalFrames = bb.int
                                        bb.int // initialFrames
                                        bb.int // streams
                                        bb.int // bufSize
                                        videoWidth = bb.int
                                        videoHeight = bb.int
                                    } else {
                                        skipFully(bis, cSize)
                                    }
                                    if (cSize % 2L != 0L) skipFully(bis, 1)
                                }
                                "strf" -> {
                                    if (cSize >= 40) {
                                        val bih = ByteArray(cSize.toInt())
                                        readFully(bis, bih, cSize.toInt())
                                        val bb = ByteBuffer.wrap(bih).order(ByteOrder.LITTLE_ENDIAN)
                                        bb.int // biSize
                                        videoWidth = bb.int
                                        videoHeight = kotlin.math.abs(bb.int)
                                        bb.short // biPlanes
                                        bitCount = bb.short.toInt()
                                        biCompression = bb.int
                                        bb.int // biSizeImage
                                        bb.int // xPels
                                        bb.int // yPels
                                        biClrUsed = bb.int

                                        compressionFourCC = String(byteArrayOf(
                                            (biCompression and 0xFF).toByte(),
                                            ((biCompression shr 8) and 0xFF).toByte(),
                                            ((biCompression shr 16) and 0xFF).toByte(),
                                            ((biCompression shr 24) and 0xFF).toByte()
                                        ), Charsets.US_ASCII).trim()
                                    } else {
                                        skipFully(bis, cSize)
                                    }
                                    if (cSize % 2L != 0L) skipFully(bis, 1)
                                    // Got strf, stop header scan
                                    break
                                }
                                else -> {
                                    skipFully(bis, cSize + (cSize % 2))
                                }
                            }
                            chunksInspected++
                        }

                        val fps = if (microSecPerFrame > 0) 1_000_000.0 / microSecPerFrame else 30.0
                        val estFrames = if (totalFrames > 0) totalFrames else 120
                        val durationMs = if (fps > 0) ((estFrames * 1000.0) / fps).toLong() else 0L

                        val isBayer = bitCount == 8 && isBayerFourCC(compressionFourCC)
                        val formatDesc = when {
                            isBayer -> "RGB 8-bit (RAW8 Bayer CFA -> Demosaic)"
                            bitCount == 8 && biClrUsed > 0 -> "RGB 8-bit (Indexed / 256 Colors)"
                            bitCount == 8 -> "RGB 8-bit (Monochrome 8bpp DIB)"
                            bitCount == 24 -> "RGB 8-bit (24bpp BGR Uncompressed DIB)"
                            bitCount == 32 -> "RGB 8-bit (32bpp BGRA/RGBA DIB)"
                            else -> "RGB 8-bit (AVI ${bitCount}bpp)"
                        }

                        val colorFmt = when {
                            isBayer -> "8-bit Bayer CFA (${compressionFourCC.ifEmpty { "RGGB" }} mosaic)"
                            bitCount == 8 && biClrUsed > 0 -> "8-bit Indexed Color Table (256 RGB entries)"
                            bitCount == 8 -> "8-bit Monochrome Linear Luminance"
                            bitCount == 24 -> "8-bit per channel RGB (24-bit TrueColor BGR)"
                            bitCount == 32 -> "8-bit per channel RGBA (32-bit TrueColor)"
                            else -> "${bitCount}-bit Video Stream"
                        }

                        val suitability = when {
                            isBayer -> "Optimal: Astronomical RAW8 Bayer camera stream with full sensor color fidelity."
                            bitCount == 24 -> "Optimal: Uncompressed 24bpp planetary stream with zero compression loss."
                            bitCount == 8 -> "Excellent: High-speed 8-bit capture stream, great for planetary stacking."
                            else -> "Supported: Astrophotography capture container ready for planetary stacking."
                        }

                        riffReport = VideoAnalysisReport(
                            fileName = displayName,
                            containerType = "RIFF AVI Container",
                            codec = if (compressionFourCC.isNotEmpty()) compressionFourCC else "Uncompressed DIB",
                            fourCC = if (compressionFourCC.isNotEmpty()) compressionFourCC else "DIB",
                            formatDescription = formatDesc,
                            bitDepth = if (bitCount > 0) bitCount else 24,
                            width = videoWidth,
                            height = videoHeight,
                            totalFramesEstimated = estFrames,
                            fps = fps,
                            durationMs = durationMs,
                            fileSizeBytes = fileSizeBytes,
                            isAstroCameraNative = true,
                            colorFormat = colorFmt,
                            astrophotographySuitability = suitability
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "RIFF header analysis skipped: ${e.message}")
        }

        val resolvedRiffReport = riffReport
        if (resolvedRiffReport != null) {
            return@withContext resolvedRiffReport
        }

        // Attempt 2: Fallback to Android MediaMetadataRetriever
        var videoWidth = 0
        var videoHeight = 0
        var durationMs = 0L
        var totalFramesEst = 0
        var fps = 30.0
        var mimeType = "video/mp4"

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (mimeStr != null) mimeType = mimeStr

            videoWidth = wStr?.toIntOrNull() ?: 0
            videoHeight = hStr?.toIntOrNull() ?: 0

            val captureFpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val capFps = captureFpsStr?.toDoubleOrNull()

            totalFramesEst = frameCountStr?.toIntOrNull() ?: if (durationMs > 0) {
                ((durationMs / 33.3).toInt()).coerceAtLeast(1)
            } else 120

            fps = capFps ?: if (durationMs > 0 && totalFramesEst > 0) {
                (totalFramesEst * 1000.0) / durationMs
            } else 30.0

            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever analysis failed: ${e.message}")
        }

        VideoAnalysisReport(
            fileName = displayName,
            containerType = if (displayName.endsWith(".avi", ignoreCase = true)) "AVI Video Container" else "Standard Video ($mimeType)",
            codec = mimeType,
            fourCC = "H264/AVC",
            formatDescription = "RGB 8-bit (Decoded ARGB_8888)",
            bitDepth = 24,
            width = videoWidth,
            height = videoHeight,
            totalFramesEstimated = totalFramesEst,
            fps = fps,
            durationMs = durationMs,
            fileSizeBytes = fileSizeBytes,
            isAstroCameraNative = false,
            colorFormat = "8-bit per channel RGB (24-bit TrueColor)",
            astrophotographySuitability = "Standard video feed decoded to 8-bit precision frames for planetary frame stacking."
        )
    }

    /**
     * Extracts frames from an AVI file (either content Uri or File) with video stream format information.
     * Supports loading up to 5,000 frames for comprehensive planetary stacking.
     */
    suspend fun extractFramesWithMetadata(
        context: Context,
        uri: Uri,
        maxFrames: Int = 5000,
        onProgress: (Int, Int) -> Unit
    ): ExtractionResult = withContext(Dispatchers.IO) {
        // First attempt: try pure RIFF AVI parser for astronomical camera uncompressed RGB 8-bit, RAW8, or MJPEG
        val riffResult = tryParseRiffAviWithMetadata(context, uri, maxFrames, onProgress)
        if (riffResult != null && riffResult.frames.isNotEmpty()) {
            Log.d(TAG, "Successfully extracted ${riffResult.frames.size} frames via native RIFF AVI parser (${riffResult.videoInfo.formatDescription})")
            return@withContext riffResult
        }

        // Second attempt: MediaMetadataRetriever for standard AVI/video codecs
        val frames = mutableListOf<Bitmap>()
        var videoWidth = 0
        var videoHeight = 0
        var fps = 30.0
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 3000L
            val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            val totalFramesEst = frameCountStr?.toIntOrNull() ?: ((durationMs / 33.3).toInt().coerceIn(10, maxFrames))

            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            videoWidth = wStr?.toIntOrNull() ?: 0
            videoHeight = hStr?.toIntOrNull() ?: 0

            val numToExtract = totalFramesEst.coerceAtMost(maxFrames)
            val stepUs = if (numToExtract > 1 && durationMs > 0) (durationMs * 1000L) / numToExtract else 33333L

            for (i in 0 until numToExtract) {
                val timeUs = i * stepUs
                try {
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(timeUs)
                    if (frame != null) {
                        frames.add(frame)
                        if (videoWidth == 0) {
                            videoWidth = frame.width
                            videoHeight = frame.height
                        }
                        onProgress(frames.size, numToExtract)
                    }
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "Memory threshold reached at ${frames.size} frames in MediaMetadataRetriever.")
                    break
                }
            }
            if (durationMs > 0) {
                fps = (frames.size.toDouble() * 1000.0) / durationMs
            }
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever extraction failed: ${e.message}")
        }

        val info = VideoStreamInfo(
            formatDescription = "RGB 8-bit (MediaCodec ARGB_8888)",
            bitDepth = 24,
            width = videoWidth,
            height = videoHeight,
            frameCount = frames.size,
            codec = "Android MediaCodec",
            fps = fps
        )
        ExtractionResult(frames, info)
    }

    /**
     * Extracts frames from an AVI file up to maxFrames (default: 5000 frames).
     */
    suspend fun extractFrames(
        context: Context,
        uri: Uri,
        maxFrames: Int = 5000,
        onProgress: (Int, Int) -> Unit
    ): List<Bitmap> {
        return extractFramesWithMetadata(context, uri, maxFrames, onProgress).frames
    }

    /**
     * Native RIFF AVI Parser with rich RGB 8-bit and astronomical format support.
     */
    private fun tryParseRiffAviWithMetadata(
        context: Context,
        uri: Uri,
        maxFrames: Int,
        onProgress: (Int, Int) -> Unit
    ): ExtractionResult? {
        val frames = mutableListOf<Bitmap>()
        var inputStream: InputStream? = null
        try {
            inputStream = BufferedInputStream(context.contentResolver.openInputStream(uri))
            val header = ByteArray(12)
            if (!readFully(inputStream, header, 12)) return null

            val riffTag = String(header, 0, 4, Charsets.US_ASCII)
            val aviTag = String(header, 8, 4, Charsets.US_ASCII)
            if (riffTag != "RIFF" || aviTag != "AVI ") {
                return null
            }

            var videoWidth = 0
            var videoHeight = 0
            var bitCount = 24
            var biCompression = 0
            var compressionFourCC = ""
            var palette: IntArray? = null
            var fps = 30.0
            var aviTotalFrames = 0

            val chunkHeader = ByteArray(8)
            while (frames.size < maxFrames) {
                if (!readFully(inputStream, chunkHeader, 8)) break
                val fourCC = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

                if (chunkSize <= 0 || chunkSize > 50_000_000) {
                    break
                }

                when (fourCC) {
                    "LIST" -> {
                        // Read list type
                        val listType = ByteArray(4)
                        readFully(inputStream, listType, 4)
                        // continue into list
                    }
                    "avih" -> {
                        if (chunkSize >= 56) {
                            val avihBuf = ByteArray(chunkSize.toInt())
                            readFully(inputStream, avihBuf, chunkSize.toInt())
                            val bb = ByteBuffer.wrap(avihBuf).order(ByteOrder.LITTLE_ENDIAN)
                            val microSecPerFrame = bb.int
                            bb.int // maxBytesPerSec
                            bb.int // paddingGranularity
                            bb.int // flags
                            val totalFrames = bb.int
                            if (totalFrames > 0) aviTotalFrames = totalFrames
                            if (microSecPerFrame > 0) {
                                fps = 1_000_000.0 / microSecPerFrame
                            }
                        } else {
                            skipFully(inputStream, chunkSize)
                        }
                        if (chunkSize % 2L != 0L) skipFully(inputStream, 1)
                    }
                    "strf" -> {
                        // Stream format (BITMAPINFOHEADER + optional palette)
                        if (chunkSize >= 40) {
                            val bih = ByteArray(chunkSize.toInt())
                            readFully(inputStream, bih, chunkSize.toInt())
                            val bb = ByteBuffer.wrap(bih).order(ByteOrder.LITTLE_ENDIAN)
                            bb.int // biSize
                            videoWidth = bb.int
                            videoHeight = bb.int
                            bb.short // biPlanes
                            bitCount = bb.short.toInt()
                            biCompression = bb.int
                            bb.int // biSizeImage
                            bb.int // biXPelsPerMeter
                            bb.int // biYPelsPerMeter
                            val biClrUsed = bb.int
                            bb.int // biClrImportant

                            compressionFourCC = String(byteArrayOf(
                                (biCompression and 0xFF).toByte(),
                                ((biCompression shr 8) and 0xFF).toByte(),
                                ((biCompression shr 16) and 0xFF).toByte(),
                                ((biCompression shr 24) and 0xFF).toByte()
                            ), Charsets.US_ASCII).trim()

                            // If 8-bit indexed, read color table palette
                            if (bitCount == 8) {
                                val numColors = if (biClrUsed in 1..256) {
                                    biClrUsed
                                } else if (bih.size >= 40 + 4) {
                                    ((bih.size - 40) / 4).coerceAtMost(256)
                                } else {
                                    0
                                }

                                if (numColors > 0 && bih.size >= 40 + numColors * 4) {
                                    val pal = IntArray(256)
                                    var offset = 40
                                    for (c in 0 until numColors) {
                                        val b = bih[offset].toInt() and 0xFF
                                        val g = bih[offset + 1].toInt() and 0xFF
                                        val r = bih[offset + 2].toInt() and 0xFF
                                        pal[c] = Color.rgb(r, g, b)
                                        offset += 4
                                    }
                                    // Fill remaining with grayscale if partial palette
                                    for (c in numColors until 256) {
                                        pal[c] = Color.rgb(c, c, c)
                                    }
                                    palette = pal
                                } else {
                                    // Default 8-bit linear grayscale palette
                                    palette = IntArray(256) { c -> Color.rgb(c, c, c) }
                                }
                            }
                        } else {
                            skipFully(inputStream, chunkSize)
                        }
                        if (chunkSize % 2L != 0L) skipFully(inputStream, 1)
                    }
                    "00dc", "00db", "01dc", "01db", "02dc", "02db" -> {
                        // Video frame chunk
                        val chunkData = ByteArray(chunkSize.toInt())
                        val readOk = readFully(inputStream, chunkData, chunkSize.toInt())

                        if (chunkSize % 2L != 0L) {
                            skipFully(inputStream, 1)
                        }

                        if (readOk) {
                            try {
                                val bitmap = decodeChunkToBitmap(
                                    data = chunkData,
                                    width = videoWidth,
                                    height = videoHeight,
                                    bitCount = bitCount,
                                    biCompression = biCompression,
                                    compressionFourCC = compressionFourCC,
                                    palette = palette
                                )
                                if (bitmap != null) {
                                    frames.add(bitmap)
                                    val targetTotal = if (aviTotalFrames in 1..maxFrames) aviTotalFrames else maxFrames
                                    onProgress(frames.size, targetTotal)
                                }
                            } catch (oom: OutOfMemoryError) {
                                Log.w(TAG, "Heap memory threshold reached at ${frames.size} frames in RIFF AVI parser.")
                                break
                            }
                        }
                    }
                    else -> {
                        // Skip unhandled chunks
                        skipFully(inputStream, chunkSize + (chunkSize % 2))
                    }
                }
            }

            if (frames.isNotEmpty()) {
                val formatDesc = when {
                    bitCount == 8 && isBayerFourCC(compressionFourCC) -> "RGB 8-bit (RAW8 Bayer CFA -> RGB)"
                    bitCount == 8 && palette != null -> "RGB 8-bit (Indexed / 256 Colors)"
                    bitCount == 8 -> "RGB 8-bit (8bpp Grayscale/Intensity)"
                    bitCount == 24 -> "RGB 8-bit (24bpp BGR/RGB)"
                    bitCount == 32 -> "RGB 8-bit (32bpp BGRA/RGBA)"
                    else -> "RGB 8-bit (AVI ${bitCount}bpp)"
                }
                val info = VideoStreamInfo(
                    formatDescription = formatDesc,
                    bitDepth = bitCount,
                    width = videoWidth,
                    height = kotlin.math.abs(videoHeight),
                    frameCount = frames.size,
                    codec = if (compressionFourCC.isNotEmpty()) compressionFourCC else "DIB",
                    fps = fps
                )
                return ExtractionResult(frames, info)
            }
        } catch (e: Exception) {
            Log.d(TAG, "RIFF parsing ended: ${e.message}")
        } finally {
            try {
                inputStream?.close()
            } catch (_: Exception) {}
        }
        return null
    }

    private fun isBayerFourCC(fourCC: String): Boolean {
        val upper = fourCC.uppercase()
        return upper in listOf("RGGB", "BGGR", "GBRG", "GRBG", "BY8", "RAW8", "RAW")
    }

    /**
     * Decodes uncompressed DIB chunks (24bpp, 8bpp Indexed, RAW8 Bayer, 32bpp) or MJPEG chunks into a Bitmap.
     */
    fun decodeChunkToBitmap(
        data: ByteArray,
        width: Int,
        height: Int,
        bitCount: Int,
        biCompression: Int = 0,
        compressionFourCC: String = "",
        palette: IntArray? = null
    ): Bitmap? {
        if (width <= 0 || height == 0) return null
        val absHeight = kotlin.math.abs(height)
        val isBottomUp = height > 0

        // 1. Try JPEG decode first (if MJPEG)
        if (data.size > 4 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) {
            return BitmapFactory.decodeByteArray(data, 0, data.size)
        }

        // 2. 8-bit Bayer CFA (RAW8: RGGB, BGGR, GBRG, GRBG, BY8, RAW8)
        if (bitCount == 8 && isBayerFourCC(compressionFourCC)) {
            return demosaicBayerToRgb(
                data = data,
                width = width,
                height = absHeight,
                isBottomUp = isBottomUp,
                pattern = compressionFourCC.uppercase()
            )
        }

        // 3. 8-bit Indexed RGB with Palette or Grayscale (BI_RGB)
        if (bitCount == 8 && biCompression == 0) {
            val rowPadding = (4 - (width % 4)) % 4
            val rowBytes = width + rowPadding
            if (data.size < rowBytes * absHeight) return null

            val bitmap = Bitmap.createBitmap(width, absHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * absHeight)
            val pal = palette ?: IntArray(256) { c -> Color.rgb(c, c, c) }

            for (y in 0 until absHeight) {
                val srcRow = if (isBottomUp) absHeight - 1 - y else y
                val rowStart = srcRow * rowBytes
                for (x in 0 until width) {
                    val index = data[rowStart + x].toInt() and 0xFF
                    pixels[y * width + x] = pal[index]
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, absHeight)
            return bitmap
        }

        // 4. 8-bit RLE (BI_RLE8)
        if (bitCount == 8 && biCompression == 1) {
            return decodeRle8ToBitmap(
                data = data,
                width = width,
                height = absHeight,
                isBottomUp = isBottomUp,
                palette = palette ?: IntArray(256) { c -> Color.rgb(c, c, c) }
            )
        }

        // 5. 8-bit Packed RGB332
        if (bitCount == 8 && compressionFourCC.uppercase() in listOf("R332", "RGB8", "332")) {
            val rowPadding = (4 - (width % 4)) % 4
            val rowBytes = width + rowPadding
            if (data.size < rowBytes * absHeight) return null

            val bitmap = Bitmap.createBitmap(width, absHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * absHeight)

            for (y in 0 until absHeight) {
                val srcRow = if (isBottomUp) absHeight - 1 - y else y
                val rowStart = srcRow * rowBytes
                for (x in 0 until width) {
                    val b = data[rowStart + x].toInt() and 0xFF
                    val r = ((b shr 5) and 0x07) * 255 / 7
                    val g = ((b shr 2) and 0x07) * 255 / 7
                    val bl = (b and 0x03) * 255 / 3
                    pixels[y * width + x] = Color.rgb(r, g, bl)
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, absHeight)
            return bitmap
        }

        // 6. 8-bit per channel RGB (24bpp BGR / RGB)
        if (bitCount == 24) {
            val rowPadding = (4 - (width * 3) % 4) % 4
            val rowBytes = width * 3 + rowPadding
            if (data.size < rowBytes * absHeight) return null

            val bitmap = Bitmap.createBitmap(width, absHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * absHeight)
            val isRgbOrder = compressionFourCC.uppercase() in listOf("RGB", "RAW", "RAW24")

            for (y in 0 until absHeight) {
                val srcRow = if (isBottomUp) absHeight - 1 - y else y
                val rowStart = srcRow * rowBytes
                for (x in 0 until width) {
                    val idx = rowStart + x * 3
                    val c0 = data[idx].toInt() and 0xFF
                    val c1 = data[idx + 1].toInt() and 0xFF
                    val c2 = data[idx + 2].toInt() and 0xFF
                    val r: Int
                    val g: Int
                    val b: Int
                    if (isRgbOrder) {
                        r = c0; g = c1; b = c2
                    } else {
                        b = c0; g = c1; r = c2
                    }
                    pixels[y * width + x] = Color.rgb(r, g, b)
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, absHeight)
            return bitmap
        }

        // 7. 8-bit per channel 32-bit RGB (32bpp BGRA / RGBA)
        if (bitCount == 32) {
            val rowBytes = width * 4
            if (data.size < rowBytes * absHeight) return null

            val bitmap = Bitmap.createBitmap(width, absHeight, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * absHeight)
            val isRgba = compressionFourCC.uppercase() in listOf("RGBA", "RGB")

            for (y in 0 until absHeight) {
                val srcRow = if (isBottomUp) absHeight - 1 - y else y
                val rowStart = srcRow * rowBytes
                for (x in 0 until width) {
                    val idx = rowStart + x * 4
                    val c0 = data[idx].toInt() and 0xFF
                    val c1 = data[idx + 1].toInt() and 0xFF
                    val c2 = data[idx + 2].toInt() and 0xFF
                    val r = if (isRgba) c0 else c2
                    val g = c1
                    val b = if (isRgba) c2 else c0
                    pixels[y * width + x] = Color.rgb(r, g, b)
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, absHeight)
            return bitmap
        }

        return null
    }

    /**
     * High-quality bilinear demosaicing of 8-bit Bayer CFA (RAW8) into 24-bit RGB bitmap.
     * Supports RGGB, BGGR, GBRG, and GRBG.
     */
    fun demosaicBayerToRgb(
        data: ByteArray,
        width: Int,
        height: Int,
        isBottomUp: Boolean = false,
        pattern: String = "RGGB"
    ): Bitmap? {
        val rowPadding = (4 - (width % 4)) % 4
        val rowBytes = width + rowPadding
        if (data.size < rowBytes * height) return null

        // Extract 2D raw 8-bit luminance sensor grid
        val raw = Array(height) { y ->
            val srcRow = if (isBottomUp) height - 1 - y else y
            val rowStart = srcRow * rowBytes
            IntArray(width) { x -> data[rowStart + x].toInt() and 0xFF }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val isBggr = pattern.startsWith("BGGR")
        val isGbrg = pattern.startsWith("GBRG")
        val isGrbg = pattern.startsWith("GRBG")

        for (y in 0 until height) {
            val yPrev = kotlin.math.max(0, y - 1)
            val yNext = kotlin.math.min(height - 1, y + 1)
            val rowOffset = y * width

            for (x in 0 until width) {
                val xPrev = kotlin.math.max(0, x - 1)
                val xNext = kotlin.math.min(width - 1, x + 1)

                val r: Int
                val g: Int
                val b: Int

                // Determine pixel color filter based on pattern
                val isEvenRow = y % 2 == 0
                val isEvenCol = x % 2 == 0

                val isRedPixel: Boolean
                val isBluePixel: Boolean
                val isGreenPixel: Boolean

                if (isBggr) {
                    isBluePixel = isEvenRow && isEvenCol
                    isRedPixel = !isEvenRow && !isEvenCol
                    isGreenPixel = !isBluePixel && !isRedPixel
                } else if (isGbrg) {
                    isGreenPixel = (isEvenRow && isEvenCol) || (!isEvenRow && !isEvenCol)
                    isBluePixel = isEvenRow && !isEvenCol
                    isRedPixel = !isEvenRow && isEvenCol
                } else if (isGrbg) {
                    isGreenPixel = (isEvenRow && isEvenCol) || (!isEvenRow && !isEvenCol)
                    isRedPixel = isEvenRow && !isEvenCol
                    isBluePixel = !isEvenRow && isEvenCol
                } else {
                    // Default RGGB
                    isRedPixel = isEvenRow && isEvenCol
                    isBluePixel = !isEvenRow && !isEvenCol
                    isGreenPixel = !isRedPixel && !isBluePixel
                }

                if (isRedPixel) {
                    r = raw[y][x]
                    g = (raw[yPrev][x] + raw[yNext][x] + raw[y][xPrev] + raw[y][xNext]) / 4
                    b = (raw[yPrev][xPrev] + raw[yPrev][xNext] + raw[yNext][xPrev] + raw[yNext][xNext]) / 4
                } else if (isBluePixel) {
                    b = raw[y][x]
                    g = (raw[yPrev][x] + raw[yNext][x] + raw[y][xPrev] + raw[y][xNext]) / 4
                    r = (raw[yPrev][xPrev] + raw[yPrev][xNext] + raw[yNext][xPrev] + raw[yNext][xNext]) / 4
                } else {
                    // Green pixel
                    g = raw[y][x]
                    if (isEvenRow) {
                        r = (raw[y][xPrev] + raw[y][xNext]) / 2
                        b = (raw[yPrev][x] + raw[yNext][x]) / 2
                    } else {
                        b = (raw[y][xPrev] + raw[y][xNext]) / 2
                        r = (raw[yPrev][x] + raw[yNext][x]) / 2
                    }
                }

                pixels[rowOffset + x] = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Decodes BI_RLE8 compressed 8-bit stream.
     */
    private fun decodeRle8ToBitmap(
        data: ByteArray,
        width: Int,
        height: Int,
        isBottomUp: Boolean,
        palette: IntArray
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        var curX = 0
        var curY = if (isBottomUp) height - 1 else 0
        val yStep = if (isBottomUp) -1 else 1
        var pos = 0

        while (pos + 1 < data.size && ((isBottomUp && curY >= 0) || (!isBottomUp && curY < height))) {
            val count = data[pos++].toInt() and 0xFF
            val value = data[pos++].toInt() and 0xFF

            if (count > 0) {
                val color = palette[value]
                for (k in 0 until count) {
                    if (curX in 0 until width && curY in 0 until height) {
                        pixels[curY * width + curX] = color
                    }
                    curX++
                }
            } else {
                when (value) {
                    0 -> { // End of scanline
                        curX = 0
                        curY += yStep
                    }
                    1 -> { // End of bitmap
                        break
                    }
                    2 -> { // Delta position
                        if (pos + 1 < data.size) {
                            curX += data[pos++].toInt() and 0xFF
                            curY += (data[pos++].toInt() and 0xFF) * yStep
                        }
                    }
                    else -> { // Absolute mode
                        val absCount = value
                        for (k in 0 until absCount) {
                            if (pos < data.size) {
                                val idx = data[pos++].toInt() and 0xFF
                                if (curX in 0 until width && curY in 0 until height) {
                                    pixels[curY * width + curX] = palette[idx]
                                }
                                curX++
                            }
                        }
                        if (absCount % 2 != 0 && pos < data.size) {
                            pos++
                        }
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Builds a standard 256-color palette covering the 6x6x6 RGB color cube plus celestial grayscale ramp.
     */
    fun generateColorPalette256(): IntArray {
        val pal = IntArray(256)
        // 6x6x6 RGB cube = 216 colors
        var idx = 0
        for (r in 0..5) {
            val red = (r * 255) / 5
            for (g in 0..5) {
                val green = (g * 255) / 5
                for (b in 0..5) {
                    val blue = (b * 255) / 5
                    pal[idx++] = Color.rgb(red, green, blue)
                }
            }
        }
        // 40 grayscale ramp colors
        for (grayIdx in 0 until 40) {
            val gray = (grayIdx * 255) / 39
            pal[idx++] = Color.rgb(gray, gray, gray)
        }
        return pal
    }

    /**
     * Quantizes an RGB color to the closest index in the 256-color palette.
     */
    fun quantizeRgbToPalette(r: Int, g: Int, b: Int): Int {
        val isGray = kotlin.math.abs(r - g) <= 8 && kotlin.math.abs(g - b) <= 8
        if (isGray) {
            val gray = (r + g + b) / 3
            return 216 + ((gray * 39) / 255).coerceIn(0, 39)
        }
        val rIdx = ((r * 5) + 127) / 255
        val gIdx = ((g * 5) + 127) / 255
        val bIdx = ((b * 5) + 127) / 255
        return (rIdx.coerceIn(0, 5) * 36) + (gIdx.coerceIn(0, 5) * 6) + bIdx.coerceIn(0, 5)
    }

    private fun readFully(stream: InputStream, buffer: ByteArray, length: Int = buffer.size): Boolean {
        var total = 0
        while (total < length) {
            val r = stream.read(buffer, total, length - total)
            if (r <= 0) return false
            total += r
        }
        return true
    }

    private fun skipFully(stream: InputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(kotlin.math.min(4096L, remaining).toInt())
        while (remaining > 0) {
            val toRead = kotlin.math.min(buf.size.toLong(), remaining).toInt()
            val r = stream.read(buf, 0, toRead)
            if (r <= 0) break
            remaining -= r
        }
    }

    /**
     * Creates a genuine, valid RIFF AVI file in RGB 8-bit format:
     * - [AviColorFormat.RGB_8BIT_24BPP]: 8-bit per channel RGB (24bpp uncompressed DIB)
     * - [AviColorFormat.RGB_8BIT_INDEXED]: 8-bit Indexed RGB with 256-color palette (1 byte per pixel)
     * - [AviColorFormat.RGB_8BIT_RAW8_BAYER]: 8-bit Bayer CFA (RGGB 8-bit raw)
     */
    suspend fun createAviFile(
        outputFile: File,
        frames: List<Bitmap>,
        fps: Int = 30,
        format: AviColorFormat = AviColorFormat.RGB_8BIT_24BPP
    ): Boolean = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext false
        try {
            val width = frames[0].width
            val height = frames[0].height
            val totalFrames = frames.size

            val fos = FileOutputStream(outputFile)
            val bos = java.io.BufferedOutputStream(fos)

            fun write4CC(tag: String) {
                bos.write(tag.toByteArray(Charsets.US_ASCII))
            }

            fun writeIntLE(v: Int) {
                bos.write(v and 0xFF)
                bos.write((v shr 8) and 0xFF)
                bos.write((v shr 16) and 0xFF)
                bos.write((v shr 24) and 0xFF)
            }

            fun writeShortLE(v: Short) {
                bos.write(v.toInt() and 0xFF)
                bos.write((v.toInt() shr 8) and 0xFF)
            }

            when (format) {
                AviColorFormat.RGB_8BIT_INDEXED -> {
                    // 8-bit Indexed RGB with 256-color palette
                    val palette = generateColorPalette256()
                    val rowPadding = (4 - (width % 4)) % 4
                    val rowBytes = width + rowPadding
                    val frameDataSize = rowBytes * height

                    val moviSize = totalFrames * (8 + frameDataSize + (frameDataSize % 2))
                    val strfSize = 40 + (256 * 4) // BITMAPINFOHEADER + 256 RGBQUADs
                    val strlSize = 4 + 8 + strfSize
                    val hdrlSize = 4 + (8 + 56) + (12 + 8 + strfSize)
                    val riffSize = 4 + (12 + 64 + (8 + strfSize)) + (12 + moviSize)

                    // RIFF AVI header
                    write4CC("RIFF")
                    writeIntLE(riffSize)
                    write4CC("AVI ")

                    // LIST hdrl
                    write4CC("LIST")
                    writeIntLE(hdrlSize)
                    write4CC("hdrl")

                    // avih
                    write4CC("avih")
                    writeIntLE(56)
                    val usPerFrame = (1_000_000L / fps).toInt()
                    writeIntLE(usPerFrame)
                    writeIntLE(frameDataSize * fps)
                    writeIntLE(0)
                    writeIntLE(0x10)
                    writeIntLE(totalFrames)
                    writeIntLE(0)
                    writeIntLE(1)
                    writeIntLE(frameDataSize)
                    writeIntLE(width)
                    writeIntLE(height)
                    writeIntLE(0); writeIntLE(0); writeIntLE(0); writeIntLE(0)

                    // LIST strl
                    write4CC("LIST")
                    writeIntLE(strlSize)
                    write4CC("strl")

                    // strf (BITMAPINFO + 256-color palette)
                    write4CC("strf")
                    writeIntLE(strfSize)
                    writeIntLE(40) // biSize
                    writeIntLE(width) // biWidth
                    writeIntLE(height) // biHeight
                    writeShortLE(1.toShort()) // biPlanes
                    writeShortLE(8.toShort()) // biBitCount = 8
                    writeIntLE(0) // biCompression = BI_RGB
                    writeIntLE(frameDataSize) // biSizeImage
                    writeIntLE(0) // biXPelsPerMeter
                    writeIntLE(0) // biYPelsPerMeter
                    writeIntLE(256) // biClrUsed = 256
                    writeIntLE(256) // biClrImportant

                    // Write 256 RGBQUAD structures (B, G, R, 0)
                    for (color in palette) {
                        bos.write(Color.blue(color))
                        bos.write(Color.green(color))
                        bos.write(Color.red(color))
                        bos.write(0)
                    }

                    // LIST movi
                    write4CC("LIST")
                    writeIntLE(4 + moviSize)
                    write4CC("movi")

                    val pixelBuf = IntArray(width)
                    val rowBuf = ByteArray(rowBytes)

                    for (bmp in frames) {
                        write4CC("00db")
                        writeIntLE(frameDataSize)
                        for (y in height - 1 downTo 0) {
                            bmp.getPixels(pixelBuf, 0, width, 0, y, width, 1)
                            for (x in 0 until width) {
                                val c = pixelBuf[x]
                                val r = Color.red(c)
                                val g = Color.green(c)
                                val b = Color.blue(c)
                                rowBuf[x] = quantizeRgbToPalette(r, g, b).toByte()
                            }
                            for (pad in width until rowBytes) {
                                rowBuf[pad] = 0
                            }
                            bos.write(rowBuf)
                        }
                        if (frameDataSize % 2 != 0) bos.write(0)
                    }
                }

                AviColorFormat.RGB_8BIT_RAW8_BAYER -> {
                    // 8-bit RAW8 Bayer RGGB format
                    val rowPadding = (4 - (width % 4)) % 4
                    val rowBytes = width + rowPadding
                    val frameDataSize = rowBytes * height

                    val moviSize = totalFrames * (8 + frameDataSize + (frameDataSize % 2))
                    val strfSize = 40
                    val strlSize = 4 + 8 + strfSize
                    val hdrlSize = 4 + (8 + 56) + (12 + 8 + strfSize)
                    val riffSize = 4 + (12 + 64 + (8 + strfSize)) + (12 + moviSize)

                    write4CC("RIFF")
                    writeIntLE(riffSize)
                    write4CC("AVI ")

                    write4CC("LIST")
                    writeIntLE(hdrlSize)
                    write4CC("hdrl")

                    write4CC("avih")
                    writeIntLE(56)
                    writeIntLE((1_000_000L / fps).toInt())
                    writeIntLE(frameDataSize * fps)
                    writeIntLE(0)
                    writeIntLE(0x10)
                    writeIntLE(totalFrames)
                    writeIntLE(0)
                    writeIntLE(1)
                    writeIntLE(frameDataSize)
                    writeIntLE(width)
                    writeIntLE(height)
                    writeIntLE(0); writeIntLE(0); writeIntLE(0); writeIntLE(0)

                    write4CC("LIST")
                    writeIntLE(strlSize)
                    write4CC("strl")

                    // strf with RGGB FourCC compression
                    write4CC("strf")
                    writeIntLE(40)
                    writeIntLE(40)
                    writeIntLE(width)
                    writeIntLE(height)
                    writeShortLE(1.toShort())
                    writeShortLE(8.toShort())
                    write4CC("RGGB") // biCompression FourCC
                    writeIntLE(frameDataSize)
                    writeIntLE(0); writeIntLE(0); writeIntLE(0); writeIntLE(0)

                    write4CC("LIST")
                    writeIntLE(4 + moviSize)
                    write4CC("movi")

                    val pixelBuf = IntArray(width)
                    val rowBuf = ByteArray(rowBytes)

                    for (bmp in frames) {
                        write4CC("00dc")
                        writeIntLE(frameDataSize)
                        for (y in height - 1 downTo 0) {
                            bmp.getPixels(pixelBuf, 0, width, 0, y, width, 1)
                            for (x in 0 until width) {
                                val c = pixelBuf[x]
                                val sample = if (y % 2 == 0) {
                                    if (x % 2 == 0) Color.red(c) else Color.green(c)
                                } else {
                                    if (x % 2 == 0) Color.green(c) else Color.blue(c)
                                }
                                rowBuf[x] = sample.toByte()
                            }
                            for (pad in width until rowBytes) {
                                rowBuf[pad] = 0
                            }
                            bos.write(rowBuf)
                        }
                        if (frameDataSize % 2 != 0) bos.write(0)
                    }
                }

                AviColorFormat.RGB_8BIT_24BPP -> {
                    // Standard 8-bit per channel RGB (24bpp BGR DIB)
                    val rowPadding = (4 - (width * 3) % 4) % 4
                    val rowBytes = width * 3 + rowPadding
                    val frameDataSize = rowBytes * height

                    val moviSize = totalFrames * (8 + frameDataSize + (frameDataSize % 2))
                    val riffSize = 4 + (12 + 64 + 48) + (12 + moviSize)

                    write4CC("RIFF")
                    writeIntLE(riffSize)
                    write4CC("AVI ")

                    write4CC("LIST")
                    val hdrlSize = 4 + (8 + 56) + (12 + 8 + 40)
                    writeIntLE(hdrlSize)
                    write4CC("hdrl")

                    write4CC("avih")
                    writeIntLE(56)
                    val usPerFrame = (1_000_000L / fps).toInt()
                    writeIntLE(usPerFrame)
                    writeIntLE(frameDataSize * fps)
                    writeIntLE(0)
                    writeIntLE(0x10)
                    writeIntLE(totalFrames)
                    writeIntLE(0)
                    writeIntLE(1)
                    writeIntLE(frameDataSize)
                    writeIntLE(width)
                    writeIntLE(height)
                    writeIntLE(0); writeIntLE(0); writeIntLE(0); writeIntLE(0)

                    write4CC("LIST")
                    val strlSize = 4 + 8 + 40
                    writeIntLE(strlSize)
                    write4CC("strl")

                    write4CC("strf")
                    writeIntLE(40)
                    writeIntLE(40)
                    writeIntLE(width)
                    writeIntLE(height)
                    writeShortLE(1.toShort())
                    writeShortLE(24.toShort()) // 24-bit (8-bit per channel)
                    writeIntLE(0)
                    writeIntLE(frameDataSize)
                    writeIntLE(0); writeIntLE(0); writeIntLE(0); writeIntLE(0)

                    write4CC("LIST")
                    writeIntLE(4 + moviSize)
                    write4CC("movi")

                    val pixelBuf = IntArray(width)
                    val rowBuf = ByteArray(rowBytes)

                    for (bmp in frames) {
                        write4CC("00db")
                        writeIntLE(frameDataSize)
                        for (y in height - 1 downTo 0) {
                            bmp.getPixels(pixelBuf, 0, width, 0, y, width, 1)
                            var idx = 0
                            for (x in 0 until width) {
                                val c = pixelBuf[x]
                                rowBuf[idx++] = (c and 0xFF).toByte() // B
                                rowBuf[idx++] = ((c shr 8) and 0xFF).toByte() // G
                                rowBuf[idx++] = ((c shr 16) and 0xFF).toByte() // R
                            }
                            while (idx < rowBytes) {
                                rowBuf[idx++] = 0
                            }
                            bos.write(rowBuf)
                        }
                        if (frameDataSize % 2 != 0) {
                            bos.write(0)
                        }
                    }
                }
            }

            bos.flush()
            bos.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing AVI file: ${e.message}", e)
            false
        }
    }
}
