package com.example.transcription

import java.io.File

/**
 * High-level state of the on-device transcription engine.
 */
sealed class TranscriptionState {
    /**
     * Engine is idle, waiting for transcription request.
     */
    object Idle : TranscriptionState()

    /**
     * Transcription or preprocessing is actively running.
     *
     * @param partialText The intermediate live transcribed text received so far.
     * @param progress Estimated progress from 0.0f to 1.0f.
     * @param stage The current stage of transcription (preprocessing, model check, recognizing).
     * @param currentSegment The most recently recognized partial segment text.
     */
    data class Transcribing(
        val partialText: String = "",
        val progress: Float = 0f,
        val stage: TranscriptionStage = TranscriptionStage.RECOGNIZING,
        val currentSegment: String? = null
    ) : TranscriptionState()

    /**
     * Transcription completed successfully.
     *
     * @param fullText The complete assembled transcription text.
     * @param segments Timestamped transcript segments for display and playback sync.
     * @param durationMs Total duration of transcribed audio in milliseconds.
     * @param language The locale or language tag used for transcription.
     */
    data class Success(
        val fullText: String,
        val segments: List<TranscriptionSegment>,
        val durationMs: Long,
        val language: String
    ) : TranscriptionState()

    /**
     * Transcription failed or could not proceed.
     *
     * @param errorType Categorized error type.
     * @param message Human-readable error description.
     * @param cause Underlying exception, if any.
     */
    data class Error(
        val errorType: TranscriptionErrorType,
        val message: String,
        val cause: Throwable? = null
    ) : TranscriptionState()
}

/**
 * Fine-grained operational stage during transcription.
 */
enum class TranscriptionStage {
    PREPARING_AUDIO,
    CHECKING_MODEL,
    DOWNLOADING_MODEL,
    RECOGNIZING,
    FINALIZING
}

/**
 * Structured error categories for on-device ML Kit transcription.
 */
enum class TranscriptionErrorType {
    /**
     * Device running Android version below Android 12 (API 31). Android 11 and lower are not supported.
     */
    UNSUPPORTED_OS_VERSION,

    /**
     * On-device speech recognition model is not available or supported on this device.
     */
    MODEL_UNAVAILABLE,

    /**
     * On-device model download was required but failed.
     */
    MODEL_DOWNLOAD_FAILED,

    /**
     * Requested language is not supported by the on-device speech recognizer.
     */
    UNSUPPORTED_LANGUAGE,

    /**
     * Audio file is empty, missing, or corrupted.
     */
    INVALID_AUDIO,

    /**
     * Error during local decoding or resampling to ML Kit format (16kHz 16-bit mono PCM).
     */
    AUDIO_PROCESSING_ERROR,

    /**
     * ML Kit speech recognizer returned an inference failure.
     */
    RECOGNITION_ERROR,

    /**
     * Operation was cancelled by caller.
     */
    CANCELLED
}

/**
 * Represents a single timestamped transcribed segment.
 */
data class TranscriptionSegment(
    val id: Long = System.currentTimeMillis(),
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 1.0f,
    val speaker: String? = null
)

/**
 * On-device speech model availability status.
 */
enum class ModelAvailabilityStatus {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
    UNSUPPORTED_OS
}

/**
 * Progress when downloading an on-device speech model.
 */
data class ModelDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isCompleted: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * Configuration options for an on-device transcription session.
 */
data class TranscriptionConfig(
    val languageCode: String = "en-US",
    val preferAdvancedMode: Boolean = false,
    val maxAudioDurationMs: Long = 60 * 60 * 1000L // 1 hour max default
)
