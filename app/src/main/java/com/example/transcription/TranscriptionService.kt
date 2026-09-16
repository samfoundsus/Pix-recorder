package com.example.transcription

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.settings.TranscriptionLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Reusable service contract for on-device speech transcription.
 * Designed to be cleanly injected and integrated into recording, playback, and detail screens in Part 2.
 */
interface TranscriptionService {

    /**
     * Current reactive state of the transcription service.
     */
    val state: StateFlow<TranscriptionState>

    /**
     * Returns true if current device meets minimum requirements (Android 12 / API 31+).
     */
    fun isSupportedOnDevice(): Boolean

    /**
     * Checks if the required speech recognition model is installed/available on-device.
     */
    suspend fun checkModelAvailability(language: TranscriptionLanguage): ModelAvailabilityStatus

    /**
     * Proactively downloads the on-device speech model for the specified language.
     */
    fun downloadModel(language: TranscriptionLanguage): Flow<ModelDownloadProgress>

    /**
     * Transcribes a real local audio file asynchronously off the main UI thread.
     */
    fun transcribeAudioFile(
        file: File,
        language: TranscriptionLanguage = TranscriptionLanguage.SYSTEM_DEFAULT,
        preferAdvancedMode: Boolean = false
    ): Flow<TranscriptionState>

    /**
     * Transcribes an audio file referenced by Android content Uri.
     */
    fun transcribeAudioUri(
        uri: Uri,
        language: TranscriptionLanguage = TranscriptionLanguage.SYSTEM_DEFAULT,
        preferAdvancedMode: Boolean = false
    ): Flow<TranscriptionState>

    /**
     * Cancels any ongoing transcription session.
     */
    suspend fun cancel()

    /**
     * Releases resources held by the transcription engine.
     */
    fun release()
}

/**
 * Default implementation of TranscriptionService using on-device ML Kit Speech Recognition.
 */
class OnDeviceTranscriptionService(
    private val context: Context,
    private val engine: MlKitSpeechTranscriptionEngine = MlKitSpeechTranscriptionEngine(context)
) : TranscriptionService {

    private val tag = "OnDeviceTranscriptionService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    override val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private var activeJob: Job? = null

    override fun isSupportedOnDevice(): Boolean {
        return engine.isSupportedPlatform()
    }

    override suspend fun checkModelAvailability(language: TranscriptionLanguage): ModelAvailabilityStatus {
        return engine.checkModelAvailability(language)
    }

    override fun downloadModel(language: TranscriptionLanguage): Flow<ModelDownloadProgress> {
        return engine.downloadModel(language)
    }

    override fun transcribeAudioFile(
        file: File,
        language: TranscriptionLanguage,
        preferAdvancedMode: Boolean
    ): Flow<TranscriptionState> = flow {
        if (!isSupportedOnDevice()) {
            val error = TranscriptionState.Error(
                errorType = TranscriptionErrorType.UNSUPPORTED_OS_VERSION,
                message = "On-device transcription requires Android 12 (API 31) or higher. Android 11 is not supported."
            )
            _state.value = error
            emit(error)
            return@flow
        }

        if (!file.exists() || file.length() == 0L) {
            val error = TranscriptionState.Error(
                errorType = TranscriptionErrorType.INVALID_AUDIO,
                message = "Audio file does not exist or has 0 bytes: ${file.name}"
            )
            _state.value = error
            emit(error)
            return@flow
        }

        _state.value = TranscriptionState.Transcribing(
            partialText = "",
            progress = 0f,
            stage = TranscriptionStage.PREPARING_AUDIO
        )

        engine.transcribeAudio(file, language, preferAdvancedMode).collect { currentState ->
            _state.value = currentState
            emit(currentState)
        }
    }.flowOn(Dispatchers.IO)

    override fun transcribeAudioUri(
        uri: Uri,
        language: TranscriptionLanguage,
        preferAdvancedMode: Boolean
    ): Flow<TranscriptionState> = flow {
        val tempFile = copyUriToTempFile(uri)
        if (tempFile == null) {
            val error = TranscriptionState.Error(
                errorType = TranscriptionErrorType.INVALID_AUDIO,
                message = "Failed to read audio from Uri: $uri"
            )
            _state.value = error
            emit(error)
            return@flow
        }

        try {
            transcribeAudioFile(tempFile, language, preferAdvancedMode).collect { currentState ->
                emit(currentState)
            }
        } finally {
            try {
                tempFile.delete()
            } catch (e: Exception) {
                // Ignore temp delete
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun copyUriToTempFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "transcription_inbox")
            cacheDir.mkdirs()
            val tempFile = File(cacheDir, "input_${System.currentTimeMillis()}.audio")

            context.contentResolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(tempFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            if (tempFile.length() > 0L) tempFile else null
        } catch (e: Exception) {
            Log.e(tag, "copyUriToTempFile failed: ${e.message}", e)
            null
        }
    }

    override suspend fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value = TranscriptionState.Idle
    }

    override fun release() {
        activeJob?.cancel()
        activeJob = null
        serviceScope.cancel()
        _state.value = TranscriptionState.Idle
    }
}
