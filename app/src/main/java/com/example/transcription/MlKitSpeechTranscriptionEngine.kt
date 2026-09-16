package com.example.transcription

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.settings.TranscriptionLanguage
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Core on-device speech transcription engine using Android ML Kit GenAI Speech Recognition.
 *
 * Supported OS: Android 12 (API 31), 13 (API 33), 14 (API 34), 15 (API 35), and 16 (API 36+).
 * Android 11 and lower are explicitly not supported.
 *
 * Runs 100% on-device with zero network requests, no Gemini API, no cloud APIs, and no API keys.
 */
class MlKitSpeechTranscriptionEngine(
    private val context: Context
) {

    companion object {
        private const val TAG = "MlKitSpeechEngine"

        /**
         * Minimum supported Android SDK version for ML Kit on-device transcription (Android 12).
         */
        const val MIN_SUPPORTED_SDK_INT = Build.VERSION_CODES.S // API 31

        /**
         * Maximum supported Android SDK version: supports up to Android 16+.
         */
        const val MAX_SUPPORTED_SDK_INT = 36
    }

    /**
     * Checks if the current device OS meets the minimum platform requirement (Android 12+ / API 31+).
     */
    fun isSupportedPlatform(): Boolean {
        return Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK_INT
    }

    /**
     * Maps app TranscriptionLanguage setting to a java.util.Locale.
     * Highlights official support for English and Hindi on-device models.
     */
    fun mapLanguageToLocale(language: TranscriptionLanguage): Locale {
        return when (language) {
            TranscriptionLanguage.HI_IN -> Locale("hi", "IN")
            TranscriptionLanguage.EN_US -> Locale.US
            TranscriptionLanguage.EN_GB -> Locale.UK
            TranscriptionLanguage.ES_ES -> Locale("es", "ES")
            TranscriptionLanguage.FR_FR -> Locale("fr", "FR")
            TranscriptionLanguage.DE_DE -> Locale("de", "DE")
            TranscriptionLanguage.JA_JP -> Locale.JAPANESE
            TranscriptionLanguage.ZH_CN -> Locale.SIMPLIFIED_CHINESE
            TranscriptionLanguage.SYSTEM_DEFAULT -> Locale.getDefault()
        }
    }

    /**
     * Checks the availability of the on-device ML Kit speech model for the requested language.
     */
    suspend fun checkModelAvailability(
        language: TranscriptionLanguage,
        preferAdvancedMode: Boolean = false
    ): ModelAvailabilityStatus = withContext(Dispatchers.IO) {
        if (!isSupportedPlatform()) {
            return@withContext ModelAvailabilityStatus.UNSUPPORTED_OS
        }

        val targetLocale = mapLanguageToLocale(language)
        var recognizer: SpeechRecognizer? = null
        try {
            val options = buildRecognizerOptions(targetLocale, preferAdvancedMode)
            recognizer = SpeechRecognition.getClient(options)
            val status = recognizer.checkStatus()
            Log.d(TAG, "checkModelAvailability for $targetLocale returned status code: $status")

            when (status) {
                FeatureStatus.AVAILABLE -> ModelAvailabilityStatus.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> ModelAvailabilityStatus.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> ModelAvailabilityStatus.DOWNLOADING
                else -> ModelAvailabilityStatus.UNAVAILABLE
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkModelAvailability exception for $targetLocale: ${e.message}", e)
            ModelAvailabilityStatus.UNAVAILABLE
        } finally {
            try {
                recognizer?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Downloads the on-device speech recognition model for the target language.
     * Emits progress updates until completed.
     */
    fun downloadModel(
        language: TranscriptionLanguage,
        preferAdvancedMode: Boolean = false
    ): Flow<ModelDownloadProgress> = flow {
        if (!isSupportedPlatform()) {
            throw IllegalStateException("On-device speech recognition requires Android 12 (API 31) or higher.")
        }

        val targetLocale = mapLanguageToLocale(language)
        val options = buildRecognizerOptions(targetLocale, preferAdvancedMode)
        val recognizer = SpeechRecognition.getClient(options)

        try {
            recognizer.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        emit(ModelDownloadProgress(0L, status.bytesToDownload, isCompleted = false))
                    }
                    is DownloadStatus.DownloadProgress -> {
                        emit(ModelDownloadProgress(status.totalBytesDownloaded, -1L, isCompleted = false))
                    }
                    is DownloadStatus.DownloadCompleted -> {
                        emit(ModelDownloadProgress(100L, 100L, isCompleted = true))
                    }
                    is DownloadStatus.DownloadFailed -> {
                        throw status.e
                    }
                }
            }
        } finally {
            try {
                recognizer.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Transcribes a real recorded audio file using ML Kit on-device Speech Recognition.
     *
     * Preprocesses the audio file locally into 16kHz 16-bit mono PCM and feeds it via ParcelFileDescriptor pipe.
     * Emits real-time states: Transcribing, Success, or Error.
     *
     * @param audioFile The real audio file to transcribe.
     * @param language Target language (supports English, Hindi, and others).
     * @param preferAdvancedMode Whether to prefer Gemini Nano advanced mode if available on device (falls back to Basic).
     */
    fun transcribeAudio(
        audioFile: File,
        language: TranscriptionLanguage = TranscriptionLanguage.SYSTEM_DEFAULT,
        preferAdvancedMode: Boolean = false
    ): Flow<TranscriptionState> = flow {
        // 1. Verify OS requirements: Strictly API 31+ (Android 12 to 16)
        if (!isSupportedPlatform()) {
            emit(
                TranscriptionState.Error(
                    errorType = TranscriptionErrorType.UNSUPPORTED_OS_VERSION,
                    message = "On-device transcription requires Android 12 (API 31) or higher. " +
                        "Current device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}). " +
                        "Android 11 and lower are not supported."
                )
            )
            return@flow
        }

        // 2. Verify input audio file
        if (!audioFile.exists() || audioFile.length() == 0L) {
            emit(
                TranscriptionState.Error(
                    errorType = TranscriptionErrorType.INVALID_AUDIO,
                    message = "Audio file '${audioFile.name}' does not exist or has 0 bytes."
                )
            )
            return@flow
        }

        val targetLocale = mapLanguageToLocale(language)
        emit(
            TranscriptionState.Transcribing(
                partialText = "",
                progress = 0.05f,
                stage = TranscriptionStage.CHECKING_MODEL
            )
        )

        val options = buildRecognizerOptions(targetLocale, preferAdvancedMode)
        val recognizer = SpeechRecognition.getClient(options)

        var tempPcmFile: File? = null
        var readPfd: ParcelFileDescriptor? = null
        val streamingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            // 3. Verify Model Status
            val modelStatus = recognizer.checkStatus()
            Log.d(TAG, "Speech model status for $targetLocale is: $modelStatus")

            if (modelStatus == FeatureStatus.UNAVAILABLE) {
                emit(
                    TranscriptionState.Error(
                        errorType = TranscriptionErrorType.MODEL_UNAVAILABLE,
                        message = "On-device speech recognition model for ${targetLocale.displayName} is not available on this device."
                    )
                )
                return@flow
            }

            if (modelStatus == FeatureStatus.DOWNLOADABLE) {
                emit(
                    TranscriptionState.Transcribing(
                        partialText = "",
                        progress = 0.1f,
                        stage = TranscriptionStage.DOWNLOADING_MODEL
                    )
                )
                try {
                    recognizer.download().collect { dStatus ->
                        when (dStatus) {
                            is DownloadStatus.DownloadProgress -> {
                                emit(
                                    TranscriptionState.Transcribing(
                                        partialText = "",
                                        progress = 0.15f,
                                        stage = TranscriptionStage.DOWNLOADING_MODEL
                                    )
                                )
                            }
                            is DownloadStatus.DownloadFailed -> {
                                throw dStatus.e
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    emit(
                        TranscriptionState.Error(
                            errorType = TranscriptionErrorType.MODEL_DOWNLOAD_FAILED,
                            message = "Failed to download on-device speech model for ${targetLocale.displayName}: ${e.message}",
                            cause = e
                        )
                    )
                    return@flow
                }
            }

            // 4. Preprocess audio locally: convert to 16kHz 16-bit mono PCM
            emit(
                TranscriptionState.Transcribing(
                    partialText = "",
                    progress = 0.2f,
                    stage = TranscriptionStage.PREPARING_AUDIO
                )
            )

            val cacheDir = File(context.cacheDir, "mlkit_transcribe")
            cacheDir.mkdirs()
            tempPcmFile = File(cacheDir, "input_${System.currentTimeMillis()}.pcm")

            val conversionResult = try {
                AudioFormatConverter.convertToPcm16kMono(
                    inputFile = audioFile,
                    outputFile = tempPcmFile,
                    onProgress = { p ->
                        // Preprocessing progress from 0.2 to 0.35
                    }
                )
            } catch (e: Exception) {
                emit(
                    TranscriptionState.Error(
                        errorType = TranscriptionErrorType.AUDIO_PROCESSING_ERROR,
                        message = "Could not preprocess audio locally: ${e.message}",
                        cause = e
                    )
                )
                return@flow
            }

            Log.d(
                TAG,
                "Preprocessed audio successfully: duration=${conversionResult.durationMs}ms, " +
                    "pcmBytes=${conversionResult.totalPcmBytes}"
            )

            // 5. Build ML Kit request with paced ParcelFileDescriptor pipe
            emit(
                TranscriptionState.Transcribing(
                    partialText = "",
                    progress = 0.35f,
                    stage = TranscriptionStage.RECOGNIZING
                )
            )

            readPfd = AudioFormatConverter.createPacedPipe(
                pcmFile = conversionResult.pcmFile,
                scope = streamingScope,
                onProgress = { streamProgress ->
                    // Stream progress maps between 0.35 and 0.95
                }
            )

            val audioSource = AudioSource.fromPfd(readPfd)
            val request = SpeechRecognizerRequest.Builder().apply {
                this.audioSource = audioSource
            }.build()

            // 6. Collect recognition results
            val assembledText = StringBuilder()
            val segments = mutableListOf<TranscriptionSegment>()
            var lastSegmentEndTimeMs = 0L

            val recognitionFlow = recognizer.startRecognition(request)
            var hasCompleted = false

            recognitionFlow.collect { response ->
                when (response) {
                    is SpeechRecognizerResponse.PartialTextResponse -> {
                        val currentChunk = response.text.trim()
                        if (currentChunk.isNotEmpty()) {
                            val combined = if (assembledText.isNotEmpty()) {
                                "$assembledText $currentChunk"
                            } else currentChunk

                            emit(
                                TranscriptionState.Transcribing(
                                    partialText = combined,
                                    progress = 0.5f,
                                    stage = TranscriptionStage.RECOGNIZING,
                                    currentSegment = currentChunk
                                )
                            )
                        }
                    }

                    is SpeechRecognizerResponse.FinalTextResponse -> {
                        val finalTextChunk = response.text.trim()
                        if (finalTextChunk.isNotEmpty()) {
                            if (assembledText.isNotEmpty()) {
                                assembledText.append(" ")
                            }
                            assembledText.append(finalTextChunk)

                            val estimatedChunkDurationMs = 2500L
                            val startMs = lastSegmentEndTimeMs
                            val endMs = (startMs + estimatedChunkDurationMs).coerceAtMost(conversionResult.durationMs)
                            lastSegmentEndTimeMs = endMs

                            segments.add(
                                TranscriptionSegment(
                                    startMs = startMs,
                                    endMs = endMs,
                                    text = finalTextChunk
                                )
                            )

                            emit(
                                TranscriptionState.Transcribing(
                                    partialText = assembledText.toString(),
                                    progress = 0.8f,
                                    stage = TranscriptionStage.RECOGNIZING,
                                    currentSegment = finalTextChunk
                                )
                            )
                        }
                    }

                    is SpeechRecognizerResponse.CompletedResponse -> {
                        hasCompleted = true
                    }

                    is SpeechRecognizerResponse.ErrorResponse -> {
                        emit(
                            TranscriptionState.Error(
                                errorType = TranscriptionErrorType.RECOGNITION_ERROR,
                                message = response.e.message ?: "On-device recognition error",
                                cause = response.e
                            )
                        )
                    }
                }
            }

            // 7. Emit Success
            val finalResultString = assembledText.toString().trim()
            emit(
                TranscriptionState.Success(
                    fullText = finalResultString,
                    segments = segments,
                    durationMs = conversionResult.durationMs,
                    language = targetLocale.toLanguageTag()
                )
            )
        } catch (c: CancellationException) {
            emit(
                TranscriptionState.Error(
                    errorType = TranscriptionErrorType.CANCELLED,
                    message = "Transcription cancelled by caller."
                )
            )
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed with exception: ${e.message}", e)
            emit(
                TranscriptionState.Error(
                    errorType = TranscriptionErrorType.RECOGNITION_ERROR,
                    message = "Transcription failed: ${e.message}",
                    cause = e
                )
            )
        } finally {
            streamingScope.cancel()
            try {
                readPfd?.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                tempPcmFile?.delete()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                recognizer.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Builds configured SpeechRecognizerOptions.
     */
    private fun buildRecognizerOptions(
        locale: Locale,
        preferAdvancedMode: Boolean
    ): SpeechRecognizerOptions {
        val builder = SpeechRecognizerOptions.Builder()
        builder.locale = locale
        val mode = if (preferAdvancedMode) {
            SpeechRecognizerOptions.Mode.MODE_ADVANCED
        } else {
            SpeechRecognizerOptions.Mode.MODE_BASIC
        }
        builder.preferredMode = mode
        return builder.build()
    }
}
