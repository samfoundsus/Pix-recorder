package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ActiveRecordingState
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.PlayerState
import com.example.data.db.RecorderDatabase
import com.example.data.model.RecordingEntity
import com.example.data.model.TranscriptSegment
import com.example.data.repository.RecordingRepository
import com.example.data.settings.AudioFormatOption
import com.example.data.settings.SettingsManager
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen {
    LIBRARY,
    RECORDING,
    PLAYBACK,
    SETTINGS
}

class RecorderViewModel(application: Application) : AndroidViewModel(application) {

    val settingsManager = SettingsManager(application)

    private val db = RecorderDatabase.getDatabase(application)
    private val repository = RecordingRepository(db.recordingDao())

    val storageTreeUri: StateFlow<String?> = settingsManager.storageTreeUri
    val storageDisplayName: StateFlow<String> = settingsManager.storageDisplayName

    fun isStorageLocationConfigured(): Boolean = settingsManager.isStorageLocationConfigured()

    fun setStorageFolderUri(uri: Uri): Boolean = settingsManager.setStorageTreeUri(uri)

    val recorderManager = AudioRecorderManager(application, settingsManager)
    val recordingManager: AudioRecorderManager get() = recorderManager
    val amplitudeFlow: StateFlow<List<Float>> = recorderManager.amplitudeFlow
    val playerManager = AudioPlayerManager(application, settingsManager)

    val recordingState: StateFlow<ActiveRecordingState> = recorderManager.recordingState
    val playerState: StateFlow<PlayerState> = playerManager.playerState

    val playingRecordingId: StateFlow<Long?> = playerState
        .map { if (it.isPlaying) it.currentRecordingId else null }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _currentScreen = MutableStateFlow(AppScreen.LIBRARY)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedRecordingId = MutableStateFlow<Long?>(null)
    val selectedRecordingId: StateFlow<Long?> = _selectedRecordingId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow("All")
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    private val _recordingDetailTab = MutableStateFlow(0) // 0 = Audio, 1 = Transcript
    val recordingDetailTab: StateFlow<Int> = _recordingDetailTab.asStateFlow()

    private val _activeRecordTab = MutableStateFlow(0) // 0 = Audio, 1 = Transcript
    val activeRecordTab: StateFlow<Int> = _activeRecordTab.asStateFlow()

    val availableTags = listOf("All", "Notes", "Meetings", "Ideas", "Interviews", "Starred")

    val recordingsList: StateFlow<List<RecordingEntity>> = combine(
        repository.allRecordings,
        _searchQuery,
        _selectedTag
    ) { list, query, tag ->
        var filtered = list
        if (tag == "Starred") {
            filtered = filtered.filter { it.isFavorite }
        } else if (tag != "All") {
            filtered = filtered.filter { it.tag.equals(tag, ignoreCase = true) }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase(Locale.getDefault())
            filtered = filtered.filter { item ->
                item.title.lowercase(Locale.getDefault()).contains(q) ||
                item.getFullTranscriptText().lowercase(Locale.getDefault()).contains(q)
            }
        }
        filtered
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedRecording = MutableStateFlow<RecordingEntity?>(null)
    val selectedRecording: StateFlow<RecordingEntity?> = _selectedRecording.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsManager.themeMode

    fun setThemeMode(mode: ThemeMode) {
        settingsManager.setThemeMode(mode)
    }

    fun openSettings() {
        settingsManager.refreshStorageStats()
        _currentScreen.value = AppScreen.SETTINGS
    }

    fun closeSettings() {
        _currentScreen.value = AppScreen.LIBRARY
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Seed demo recordings if empty so Pixel Recorder features are immediately previewable
                val existing = repository.allRecordings.first()
                if (existing.isEmpty()) {
                    seedInitialPixelRecordings()
                }
            } catch (e: Exception) {
                android.util.Log.w("RecorderViewModel", "Initial seeding skipped: ${e.message}")
            }
        }

        viewModelScope.launch {
            _selectedRecordingId.collect { id ->
                if (id != null) {
                    try {
                        repository.getRecordingById(id).collect { entity ->
                            _selectedRecording.value = entity
                        }
                    } catch (e: Exception) {
                        _selectedRecording.value = null
                    }
                } else {
                    _selectedRecording.value = null
                }
            }
        }
    }

    private suspend fun seedInitialPixelRecordings() {
        val (sampleFile1, sampleFile2) = com.example.audio.AudioFileHelper.ensureSampleAudioFiles(getApplication())

        val sampleAmps1 = listOf(
            0.15f, 0.22f, 0.45f, 0.78f, 0.82f, 0.65f, 0.35f, 0.42f, 0.88f, 0.95f,
            0.70f, 0.55f, 0.30f, 0.25f, 0.60f, 0.85f, 0.92f, 0.75f, 0.40f, 0.18f,
            0.28f, 0.62f, 0.80f, 0.50f, 0.38f, 0.72f, 0.85f, 0.90f, 0.64f, 0.32f,
            0.48f, 0.76f, 0.89f, 0.81f, 0.52f, 0.31f, 0.68f, 0.84f, 0.73f, 0.44f,
            0.20f, 0.35f, 0.65f, 0.91f, 0.88f, 0.72f, 0.41f, 0.29f, 0.58f, 0.70f
        )
        val sampleTranscript1 = listOf(
            TranscriptSegment(
                speaker = "Speaker 1",
                startMs = 0L,
                endMs = 8200L,
                text = "Welcome to the team standup. Today we're reviewing the new Pixel Recorder interface design and speech processing pipeline."
            ),
            TranscriptSegment(
                speaker = "Speaker 2",
                startMs = 8500L,
                endMs = 17400L,
                text = "The dynamic audio waveform visualization is feeling really responsive, especially with the real-time amplitude track."
            ),
            TranscriptSegment(
                speaker = "Speaker 1",
                startMs = 17800L,
                endMs = 28600L,
                text = "Agreed. Live transcription and interactive seeking from transcript words make reviewing recordings effortless."
            ),
            TranscriptSegment(
                speaker = "Speaker 2",
                startMs = 29000L,
                endMs = 38000L,
                text = "Everything looks clean and matches Material 3 specs. Let's wrap this up and test on device."
            )
        )

        val sampleAmps2 = listOf(
            0.20f, 0.35f, 0.50f, 0.70f, 0.65f, 0.40f, 0.25f, 0.55f, 0.80f, 0.75f,
            0.60f, 0.30f, 0.45f, 0.85f, 0.90f, 0.68f, 0.42f, 0.30f, 0.70f, 0.88f,
            0.82f, 0.58f, 0.35f, 0.48f, 0.78f, 0.92f, 0.80f, 0.52f, 0.28f, 0.62f
        )
        val sampleTranscript2 = listOf(
            TranscriptSegment(
                speaker = "Speaker 1",
                startMs = 0L,
                endMs = 9500L,
                text = "Quick voice note on the user experience: make sure the big red record button has a smooth tactile pulse."
            ),
            TranscriptSegment(
                speaker = "Speaker 1",
                startMs = 9800L,
                endMs = 21000L,
                text = "Playback scrubber should support scrubbing and speed toggle up to 2x without audio distortion."
            )
        )

        repository.insertRecording(
            RecordingEntity(
                title = "Design Sync & Architecture",
                filePath = sampleFile1,
                createdAt = System.currentTimeMillis() - 3600000L * 2,
                durationMs = 38000L,
                amplitudesJson = RecordingEntity.amplitudesToJson(sampleAmps1),
                transcriptJson = TranscriptSegment.listToJson(sampleTranscript1),
                tag = "Meetings",
                isFavorite = true
            )
        )

        repository.insertRecording(
            RecordingEntity(
                title = "Voice Memo: Audio Scrubber UX",
                filePath = sampleFile2,
                createdAt = System.currentTimeMillis() - 86400000L,
                durationMs = 21000L,
                amplitudesJson = RecordingEntity.amplitudesToJson(sampleAmps2),
                transcriptJson = TranscriptSegment.listToJson(sampleTranscript2),
                tag = "Ideas",
                isFavorite = false
            )
        )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTag(tag: String) {
        _selectedTag.value = tag
    }

    fun setRecordingDetailTab(index: Int) {
        _recordingDetailTab.value = index
    }

    fun setActiveRecordTab(index: Int) {
        _activeRecordTab.value = index
    }

    fun startNewRecording(): Boolean {
        playerManager.stop()
        val started = recorderManager.startRecording()
        if (started) {
            _activeRecordTab.value = 0
            _currentScreen.value = AppScreen.RECORDING
        }
        return started
    }

    fun pauseRecording() {
        recorderManager.pauseRecording()
    }

    fun resumeRecording() {
        recorderManager.resumeRecording()
    }

    fun cancelRecording() {
        recorderManager.cancelRecording()
        _currentScreen.value = AppScreen.LIBRARY
    }

    fun saveRecording(customTitle: String? = null) {
        viewModelScope.launch {
            val output = recorderManager.stopRecording()
            if (output != null) {
                val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                val defaultTitle = "Recording ${dateFormat.format(Date())}"
                val title = if (!customTitle.isNullOrBlank()) customTitle else defaultTitle

                val formatOpt = settingsManager.audioFormat.value
                val mimeType = if (formatOpt == AudioFormatOption.WAV || output.file.extension.equals("wav", ignoreCase = true)) {
                    "audio/wav"
                } else {
                    "audio/mp4"
                }
                val fileSafeTitle = title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val fileName = "${fileSafeTitle}_${System.currentTimeMillis()}"

                // Save recording into the user's selected SAF shared storage folder
                val userStorageUri = settingsManager.saveAudioToUserStorage(
                    sourceFile = output.file,
                    preferredName = fileName,
                    mimeType = mimeType
                )

                val permanentPath = userStorageUri?.toString() ?: output.file.absolutePath

                val entity = RecordingEntity(
                    title = title,
                    filePath = permanentPath,
                    createdAt = System.currentTimeMillis(),
                    durationMs = output.durationMs,
                    amplitudesJson = RecordingEntity.amplitudesToJson(output.amplitudes),
                    transcriptJson = TranscriptSegment.listToJson(output.transcriptSegments),
                    tag = "Notes"
                )

                val newId = repository.insertRecording(entity)
                settingsManager.refreshStorageStats()
                openRecordingPlayback(newId)
            } else {
                _currentScreen.value = AppScreen.LIBRARY
            }
        }
    }

    fun openRecordingPlayback(id: Long) {
        _selectedRecordingId.value = id
        _recordingDetailTab.value = 0
        _currentScreen.value = AppScreen.PLAYBACK

        viewModelScope.launch {
            val recording = repository.getRecordingById(id).first()
            if (recording != null) {
                var path = recording.filePath
                if (path.isBlank()) {
                    val (s1, s2) = com.example.audio.AudioFileHelper.ensureSampleAudioFiles(getApplication())
                    path = if (recording.title.contains("Design", ignoreCase = true)) s1 else s2
                }
                playerManager.loadAndPlay(recording.id, path, recording.durationMs)
            }
        }
    }

    fun closePlayback() {
        playerManager.stop()
        _selectedRecordingId.value = null
        _currentScreen.value = AppScreen.LIBRARY
    }

    fun backToLibrary() {
        _currentScreen.value = AppScreen.LIBRARY
    }

    fun togglePlayPauseCurrent() {
        playerManager.togglePlayPause()
    }

    fun playRecordingInline(recording: RecordingEntity) {
        if (playerState.value.currentRecordingId == recording.id) {
            playerManager.togglePlayPause()
        } else {
            viewModelScope.launch {
                var path = recording.filePath
                if (path.isBlank()) {
                    val (s1, s2) = com.example.audio.AudioFileHelper.ensureSampleAudioFiles(getApplication())
                    path = if (recording.title.contains("Design", ignoreCase = true)) s1 else s2
                }
                playerManager.loadAndPlay(recording.id, path, recording.durationMs)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun seekToTranscriptSegment(segment: TranscriptSegment) {
        playerManager.seekTo(segment.startMs)
        if (!playerState.value.isPlaying) {
            playerManager.resume()
        }
    }

    fun replay10() {
        playerManager.replay10()
    }

    fun forward30() {
        playerManager.forward30()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    fun cyclePlaybackSpeed() {
        val current = playerState.value.speed
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIndex = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val nextIndex = if (currentIndex == -1 || currentIndex == speeds.lastIndex) 0 else currentIndex + 1
        playerManager.setSpeed(speeds[nextIndex])
    }

    fun toggleFavorite(recording: RecordingEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(recording)
        }
    }

    fun updateRecordingTitle(recordingId: Long, newTitle: String) {
        viewModelScope.launch {
            val recording = repository.getRecordingById(recordingId).first()
            if (recording != null && newTitle.isNotBlank()) {
                repository.updateRecording(recording.copy(title = newTitle.trim()))
            }
        }
    }

    fun updateRecordingTag(recordingId: Long, newTag: String) {
        viewModelScope.launch {
            val recording = repository.getRecordingById(recordingId).first()
            if (recording != null) {
                repository.updateRecording(recording.copy(tag = newTag))
            }
        }
    }

    fun deleteRecording(recordingId: Long) {
        deleteRecordings(setOf(recordingId))
    }

    fun deleteRecordings(recordingIds: Set<Long>) {
        viewModelScope.launch {
            for (recordingId in recordingIds) {
                if (_selectedRecordingId.value == recordingId) {
                    playerManager.stop()
                    _selectedRecordingId.value = null
                    _currentScreen.value = AppScreen.LIBRARY
                }
                val rec = repository.getRecordingById(recordingId).first()
                if (rec != null && rec.filePath.isNotBlank()) {
                    try {
                        if (rec.filePath.startsWith("content://")) {
                            val uri = Uri.parse(rec.filePath)
                            val doc = DocumentFile.fromSingleUri(getApplication(), uri)
                            val deleted = doc?.delete() == true
                            if (!deleted) {
                                getApplication<Application>().contentResolver.delete(uri, null, null)
                            }
                        } else {
                            val file = java.io.File(rec.filePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                repository.deleteRecording(recordingId)
            }
            settingsManager.refreshStorageStats()
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorderManager.release()
        playerManager.release()
    }
}
