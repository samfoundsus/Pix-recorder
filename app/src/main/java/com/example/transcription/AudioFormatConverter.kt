package com.example.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result of local audio conversion to ML Kit required format (16kHz, 16-bit, Mono PCM).
 */
data class AudioConversionResult(
    val pcmFile: File,
    val durationMs: Long,
    val sampleRate: Int = AudioFormatConverter.TARGET_SAMPLE_RATE,
    val channelCount: Int = 1,
    val totalPcmBytes: Long
)

class AudioProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Preprocesses and converts any audio file format (AAC, M4A, WAV, MP3, OGG, FLAC)
 * into raw, headerless 16-bit Mono PCM at 16,000 Hz as required by ML Kit GenAI Speech Recognition.
 *
 * Runs completely locally on the device off the main UI thread with zero external dependencies.
 */
object AudioFormatConverter {

    private const val TAG = "AudioFormatConverter"
    const val TARGET_SAMPLE_RATE = 16000
    const val TARGET_CHANNEL_COUNT = 1
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM = 2 bytes
    const val BYTES_PER_SECOND = TARGET_SAMPLE_RATE * BYTES_PER_SAMPLE // 32,000 bytes/sec

    /**
     * Converts the specified audio file into a raw 16kHz 16-bit mono PCM file.
     *
     * @param inputFile Source audio file (e.g. .m4a, .aac, .wav, .mp3).
     * @param outputFile Destination raw PCM file.
     * @param onProgress Optional callback for conversion progress (0.0f - 1.0f).
     * @return AudioConversionResult containing output file information and duration.
     */
    suspend fun convertToPcm16kMono(
        inputFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): AudioConversionResult = withContext(Dispatchers.IO) {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            throw AudioProcessingException("Input audio file does not exist or is empty: ${inputFile.absolutePath}")
        }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        try {
            decodeAudioFileToPcm(inputFile, outputFile, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec decoding failed, trying WAV fallback: ${e.message}", e)
            try {
                decodeWavFallback(inputFile, outputFile)
            } catch (fallbackEx: Exception) {
                throw AudioProcessingException(
                    "Failed to decode audio file '${inputFile.name}': ${e.message}",
                    e
                )
            }
        }

        val pcmLength = outputFile.length()
        if (pcmLength == 0L) {
            throw AudioProcessingException("Converted PCM file is empty. Audio format may be unsupported or silent.")
        }

        val durationMs = (pcmLength * 1000L) / BYTES_PER_SECOND
        AudioConversionResult(
            pcmFile = outputFile,
            durationMs = durationMs,
            sampleRate = TARGET_SAMPLE_RATE,
            channelCount = TARGET_CHANNEL_COUNT,
            totalPcmBytes = pcmLength
        )
    }

    /**
     * Decodes any supported audio format into 16kHz mono 16-bit PCM using Android's native MediaCodec.
     */
    private fun decodeAudioFileToPcm(
        inputFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)?
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var outputStream: FileOutputStream? = null

        try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || trackFormat == null) {
                throw AudioProcessingException("No audio track found in file: ${inputFile.name}")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw AudioProcessingException("Missing audio MIME type in track format")

            val totalDurationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            var sourceSampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else TARGET_SAMPLE_RATE

            var sourceChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else TARGET_CHANNEL_COUNT

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            outputStream = FileOutputStream(outputFile)
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            val kTimeoutUs = 5000L

            val byteBuffer16k = ByteArray(8192)

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(kTimeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, kTimeoutUs)
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }

                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            // Read 16-bit PCM shorts from decoder output
                            val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val shortCount = shortBuffer.remaining()
                            val shorts = ShortArray(shortCount)
                            shortBuffer.get(shorts)

                            // Downmix to mono and resample to 16kHz
                            val pcm16k = processPcm(
                                inputSamples = shorts,
                                inChannels = sourceChannels,
                                inSampleRate = sourceSampleRate,
                                targetSampleRate = TARGET_SAMPLE_RATE
                            )

                            // Write raw little-endian bytes to file
                            writeShortsAsBytes(pcm16k, outputStream)

                            if (totalDurationUs > 0) {
                                val progress = (bufferInfo.presentationTimeUs.toFloat() / totalDurationUs)
                                    .coerceIn(0f, 1f)
                                onProgress?.invoke(progress)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        sourceChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    Log.d(TAG, "Decoder format changed: rate=$sourceSampleRate, channels=$sourceChannels")
                }
            }

            outputStream.flush()
            onProgress?.invoke(1.0f)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing codec: ${e.message}")
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing extractor: ${e.message}")
            }
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing output stream: ${e.message}")
            }
        }
    }

    /**
     * Fallback parser for standard RIFF/WAV files to extract PCM bytes directly.
     */
    private fun decodeWavFallback(inputFile: File, outputFile: File) {
        FileInputStream(inputFile).use { inputStream ->
            val header = ByteArray(44)
            val read = inputStream.read(header)
            if (read < 44) throw AudioProcessingException("Invalid WAV header")

            // Check "RIFF" and "WAVE"
            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") {
                throw AudioProcessingException("Not a valid RIFF/WAVE audio file")
            }

            val channels = ((header[22].toInt() and 0xFF) or ((header[23].toInt() and 0xFF) shl 8))
            val sampleRate = (
                (header[24].toInt() and 0xFF) or
                    ((header[25].toInt() and 0xFF) shl 8) or
                    ((header[26].toInt() and 0xFF) shl 16) or
                    ((header[27].toInt() and 0xFF) shl 24)
                )
            val bitsPerSample = ((header[34].toInt() and 0xFF) or ((header[35].toInt() and 0xFF) shl 8))

            if (bitsPerSample != 16) {
                throw AudioProcessingException("Only 16-bit PCM WAV supported in direct parser, found $bitsPerSample-bit")
            }

            FileOutputStream(outputFile).use { outStream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val shortCount = bytesRead / 2
                    val shorts = ShortArray(shortCount)
                    val bb = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
                    bb.asShortBuffer().get(shorts)

                    val pcm16k = processPcm(
                        inputSamples = shorts,
                        inChannels = channels,
                        inSampleRate = sampleRate,
                        targetSampleRate = TARGET_SAMPLE_RATE
                    )
                    writeShortsAsBytes(pcm16k, outStream)
                }
                outStream.flush()
            }
        }
    }

    /**
     * Converts multi-channel samples to mono and resamples to 16,000 Hz using linear interpolation.
     */
    private fun processPcm(
        inputSamples: ShortArray,
        inChannels: Int,
        inSampleRate: Int,
        targetSampleRate: Int
    ): ShortArray {
        // 1. Downmix to Mono
        val monoSamples: ShortArray
        if (inChannels <= 1) {
            monoSamples = inputSamples
        } else {
            val frameCount = inputSamples.size / inChannels
            monoSamples = ShortArray(frameCount)
            for (i in 0 until frameCount) {
                var sum = 0
                val offset = i * inChannels
                for (c in 0 until inChannels) {
                    sum += inputSamples[offset + c]
                }
                monoSamples[i] = (sum / inChannels).coerceIn(-32768, 32767).toShort()
            }
        }

        // 2. Resample if needed
        if (inSampleRate == targetSampleRate) {
            return monoSamples
        }

        val ratio = inSampleRate.toDouble() / targetSampleRate.toDouble()
        val outLength = (monoSamples.size / ratio).toInt()
        val resampled = ShortArray(outLength)

        for (i in 0 until outLength) {
            val inputPos = i * ratio
            val index0 = inputPos.toInt()
            val index1 = (index0 + 1).coerceAtMost(monoSamples.size - 1)
            val frac = (inputPos - index0).toFloat()

            val s0 = monoSamples[index0].toFloat()
            val s1 = monoSamples[index1].toFloat()
            val interpolated = ((1f - frac) * s0 + frac * s1).toInt().coerceIn(-32768, 32767)
            resampled[i] = interpolated.toShort()
        }

        return resampled
    }

    /**
     * Writes 16-bit short samples into little-endian byte stream.
     */
    private fun writeShortsAsBytes(shorts: ShortArray, outputStream: FileOutputStream) {
        val bytes = ByteArray(shorts.size * 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.asShortBuffer().put(shorts)
        outputStream.write(bytes)
    }

    /**
     * Creates a real-time paced pipe for ML Kit SpeechRecognizer.
     * ML Kit requires ~32,000 bytes/sec continuous real-time delivery via ParcelFileDescriptor.
     *
     * @param pcmFile The raw 16kHz 16-bit mono PCM file.
     * @param scope CoroutineScope to run the background pipe writer.
     * @param onProgress Callback receiving progress (0.0f - 1.0f).
     * @return The read-end ParcelFileDescriptor to pass to AudioSource.fromPfd(readPfd).
     */
    fun createPacedPipe(
        pcmFile: File,
        scope: CoroutineScope,
        onProgress: ((Float) -> Unit)? = null
    ): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readPfd = pipe[0]
        val writePfd = pipe[1]

        scope.launch(Dispatchers.IO) {
            val totalBytes = pcmFile.length()
            var streamedBytes = 0L

            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writePfd).use { outStream ->
                    FileInputStream(pcmFile).use { inStream ->
                        // 100ms chunk of 16kHz 16-bit mono PCM = 1600 samples * 2 bytes = 3200 bytes
                        val chunkBytes = 3200
                        val buffer = ByteArray(chunkBytes)
                        var read = 0

                        while (isActive && inStream.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                            outStream.flush()
                            streamedBytes += read

                            if (totalBytes > 0) {
                                val progress = (streamedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                onProgress?.invoke(progress)
                            }

                            // 100ms chunk pace: slight 90ms delay to keep the buffer smoothly populated
                            delay(90L)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Pipe writer finished or closed by consumer: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Error writing to audio pipe: ${e.message}")
            }
        }

        return readPfd
    }
}
