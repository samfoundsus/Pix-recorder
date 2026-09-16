package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerState(
    val currentRecordingId: Long? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val speed: Float = 1.0f
)

class AudioPlayerManager(
    private val context: Context,
    val settingsManager: SettingsManager = SettingsManager(context)
) {

    private val tag = "AudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _playerState = MutableStateFlow(PlayerState(speed = 1.0f))
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun loadAndPlay(recordingId: Long, filePath: String, durationMs: Long) {
        val recordingSpeed = settingsManager.getRecordingSpeed(recordingId)
        val isContentUri = filePath.startsWith("content://")
        val isFileValid = if (isContentUri) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(filePath), "r")
                val valid = pfd != null && pfd.statSize > 0
                pfd?.close()
                valid
            } catch (e: Exception) {
                false
            }
        } else {
            val f = File(filePath)
            f.exists() && f.length() > 0L
        }

        if (!isFileValid) {
            // Simulated playback for demo recordings where physical audio file doesn't exist
            startSimulatedPlayback(recordingId, durationMs, recordingSpeed)
            return
        }

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (isContentUri) {
                    setDataSource(context, Uri.parse(filePath))
                } else {
                    setDataSource(filePath)
                }
                prepare()
                setVolume(1.0f, 1.0f)
                val actualDuration = if (duration > 0) duration.toLong() else durationMs
                _playerState.value = PlayerState(
                    currentRecordingId = recordingId,
                    isPlaying = true,
                    currentPositionMs = 0L,
                    totalDurationMs = actualDuration,
                    speed = recordingSpeed
                )
                applySpeed(recordingSpeed)
                start()

                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        currentPositionMs = _playerState.value.totalDurationMs
                    )
                    progressJob?.cancel()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    true
                }
            }
            startProgressUpdates()
        } catch (e: Exception) {
            Log.e(tag, "Failed to play audio: ${e.message}", e)
            startSimulatedPlayback(recordingId, durationMs, recordingSpeed)
        }
    }

    private fun startSimulatedPlayback(recordingId: Long, durationMs: Long, speed: Float = 1.0f) {
        stop()
        val total = if (durationMs > 0) durationMs else 30000L
        _playerState.value = PlayerState(
            currentRecordingId = recordingId,
            isPlaying = true,
            currentPositionMs = 0L,
            totalDurationMs = total,
            speed = speed
        )
        startProgressUpdates()
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            // Ignore
        }
        progressJob?.cancel()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun resume() {
        if (_playerState.value.currentRecordingId == null) return

        if (_playerState.value.currentPositionMs >= _playerState.value.totalDurationMs) {
            seekTo(0L)
        }

        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            // Ignore
        }
        _playerState.value = _playerState.value.copy(isPlaying = true)
        startProgressUpdates()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _playerState.value.totalDurationMs)
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (e: Exception) {
            // Ignore
        }
        _playerState.value = _playerState.value.copy(currentPositionMs = clamped)
    }

    fun replay10() {
        val delta = settingsManager.skipBackwardSec.value * 1000L
        val newPos = (_playerState.value.currentPositionMs - delta).coerceAtLeast(0L)
        seekTo(newPos)
    }

    fun forward30() {
        val delta = settingsManager.skipForwardSec.value * 1000L
        val newPos = (_playerState.value.currentPositionMs + delta).coerceAtMost(_playerState.value.totalDurationMs)
        seekTo(newPos)
    }

    fun setSpeed(speed: Float) {
        val currentId = _playerState.value.currentRecordingId
        if (currentId != null) {
            settingsManager.setRecordingSpeed(currentId, speed)
        }
        _playerState.value = _playerState.value.copy(speed = speed)
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.playbackParams = PlaybackParams().apply {
                    this.speed = speed
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Cannot set playback speed: ${e.message}")
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            val stepMs = 50L
            while (isActive && _playerState.value.isPlaying) {
                val current = if (mediaPlayer != null) {
                    try {
                        mediaPlayer?.currentPosition?.toLong() ?: _playerState.value.currentPositionMs
                    } catch (e: Exception) {
                        _playerState.value.currentPositionMs
                    }
                } else {
                    // Simulated increment for sample recordings
                    val speed = _playerState.value.speed
                    val next = _playerState.value.currentPositionMs + (stepMs * speed).toLong()
                    if (next >= _playerState.value.totalDurationMs) {
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            currentPositionMs = _playerState.value.totalDurationMs
                        )
                        break
                    }
                    next
                }

                _playerState.value = _playerState.value.copy(currentPositionMs = current)
                delay(stepMs)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }

    fun release() {
        stop()
    }
}
