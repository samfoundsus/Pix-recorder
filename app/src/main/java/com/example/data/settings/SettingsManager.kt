package com.example.data.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pixel_recorder_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Keys
    private object Keys {
        const val AUDIO_QUALITY = "audio_quality"
        const val AUDIO_FORMAT = "audio_format"
        const val SAMPLE_RATE = "sample_rate"
        const val BITRATE = "bitrate"
        const val AUDIO_SOURCE = "audio_source"
        const val TRANSCRIPTION_LANGUAGE = "transcription_language"
        const val SPEAKER_LABELS = "speaker_labels"
        const val AUTO_TRANSCRIPTION = "auto_transcription"
        const val SKIP_FORWARD_SEC = "skip_forward_sec"
        const val SKIP_BACKWARD_SEC = "skip_backward_sec"
        const val THEME_MODE = "theme_mode"
        const val STORAGE_TREE_URI = "storage_tree_uri"
        const val STORAGE_DISPLAY_NAME = "storage_display_name"
    }

    // StateFlows
    private val _storageTreeUri = MutableStateFlow(prefs.getString(Keys.STORAGE_TREE_URI, null))
    val storageTreeUri: StateFlow<String?> = _storageTreeUri.asStateFlow()

    private val _storageDisplayName = MutableStateFlow(
        prefs.getString(Keys.STORAGE_DISPLAY_NAME, null) ?: loadInitialStorageDisplayName()
    )
    val storageDisplayName: StateFlow<String> = _storageDisplayName.asStateFlow()

    private val _audioQuality = MutableStateFlow(loadAudioQuality())
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private val _audioFormat = MutableStateFlow(loadAudioFormat())
    val audioFormat: StateFlow<AudioFormatOption> = _audioFormat.asStateFlow()

    private val _sampleRate = MutableStateFlow(loadSampleRate())
    val sampleRate: StateFlow<SampleRateOption> = _sampleRate.asStateFlow()

    private val _bitrate = MutableStateFlow(loadBitrate())
    val bitrate: StateFlow<BitrateOption> = _bitrate.asStateFlow()

    private val _audioSource = MutableStateFlow(loadAudioSource())
    val audioSource: StateFlow<AudioSourceOption> = _audioSource.asStateFlow()

    private val _transcriptionLanguage = MutableStateFlow(loadTranscriptionLanguage())
    val transcriptionLanguage: StateFlow<TranscriptionLanguage> = _transcriptionLanguage.asStateFlow()

    private val _speakerLabelsEnabled = MutableStateFlow(prefs.getBoolean(Keys.SPEAKER_LABELS, true))
    val speakerLabelsEnabled: StateFlow<Boolean> = _speakerLabelsEnabled.asStateFlow()

    private val _autoTranscriptionEnabled = MutableStateFlow(prefs.getBoolean(Keys.AUTO_TRANSCRIPTION, true))
    val autoTranscriptionEnabled: StateFlow<Boolean> = _autoTranscriptionEnabled.asStateFlow()

    private val _skipForwardSec = MutableStateFlow(prefs.getInt(Keys.SKIP_FORWARD_SEC, 30))
    val skipForwardSec: StateFlow<Int> = _skipForwardSec.asStateFlow()

    private val _skipBackwardSec = MutableStateFlow(prefs.getInt(Keys.SKIP_BACKWARD_SEC, 10))
    val skipBackwardSec: StateFlow<Int> = _skipBackwardSec.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _storageStats = MutableStateFlow(
        StorageStats(
            recordingCount = 0,
            totalSizeBytes = 0L,
            tempSizeBytes = 0L,
            storagePath = _storageDisplayName.value
        )
    )
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    init {
        refreshStorageStats()
    }

    private fun loadInitialStorageDisplayName(): String {
        val uriStr = prefs.getString(Keys.STORAGE_TREE_URI, null)
        if (!uriStr.isNullOrBlank()) {
            return formatTreeUriName(Uri.parse(uriStr))
        }
        return "Not configured (Tap to select folder)"
    }

    fun isStorageLocationConfigured(): Boolean {
        val uriStr = _storageTreeUri.value
        if (uriStr.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriStr)
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    fun setStorageTreeUri(uri: Uri): Boolean {
        return try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.w("SettingsManager", "takePersistableUriPermission error: ${e.message}")
            }

            val friendlyName = formatTreeUriName(uri)
            prefs.edit()
                .putString(Keys.STORAGE_TREE_URI, uri.toString())
                .putString(Keys.STORAGE_DISPLAY_NAME, friendlyName)
                .apply()

            _storageTreeUri.value = uri.toString()
            _storageDisplayName.value = friendlyName
            refreshStorageStats()
            true
        } catch (e: Exception) {
            Log.e("SettingsManager", "Failed to save storage URI: ${e.message}", e)
            false
        }
    }

    fun formatTreeUriName(uri: Uri): String {
        return try {
            val doc = DocumentFile.fromTreeUri(context, uri)
            val docName = doc?.name
            val decoded = Uri.decode(uri.toString())
            when {
                decoded.contains("primary:") -> {
                    val path = decoded.substringAfter("primary:").replace("/", " > ")
                    if (path.isNotBlank()) "Internal Storage > $path" else "Internal Storage"
                }
                !docName.isNullOrBlank() -> "Internal Storage > $docName"
                else -> "Shared Storage Folder"
            }
        } catch (e: Exception) {
            "Shared Storage Folder"
        }
    }

    suspend fun saveAudioToUserStorage(
        sourceFile: File,
        preferredName: String,
        mimeType: String = "audio/mp4"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val treeUriStr = _storageTreeUri.value
            if (!treeUriStr.isNullOrBlank()) {
                val treeUri = Uri.parse(treeUriStr)
                val parentDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (parentDoc != null && parentDoc.canWrite()) {
                    val extension = sourceFile.extension.ifBlank { "m4a" }
                    val targetFileName = if (preferredName.endsWith(".$extension", ignoreCase = true)) {
                        preferredName
                    } else {
                        "$preferredName.$extension"
                    }

                    val newDoc = parentDoc.createFile(mimeType, targetFileName)
                    if (newDoc != null) {
                        context.contentResolver.openOutputStream(newDoc.uri)?.use { outStream ->
                            sourceFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                            outStream.flush()
                        }
                        refreshStorageStats()
                        return@withContext newDoc.uri
                    }
                }
            }

            // Safe fallback to shared external documents directory if tree URI is unavailable
            val sharedDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "PixelRecordings"
            ).apply { if (!exists()) mkdirs() }
            val extension = sourceFile.extension.ifBlank { "m4a" }
            val targetFileName = if (preferredName.endsWith(".$extension", ignoreCase = true)) preferredName else "$preferredName.$extension"
            val targetFile = File(sharedDir, targetFileName)
            sourceFile.copyTo(targetFile, overwrite = true)
            refreshStorageStats()
            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            Log.e("SettingsManager", "Error saving to user storage: ${e.message}", e)
            null
        }
    }

    private fun loadAudioQuality(): AudioQuality {
        val saved = prefs.getString(Keys.AUDIO_QUALITY, AudioQuality.STANDARD.name)
        return try {
            AudioQuality.valueOf(saved ?: AudioQuality.STANDARD.name)
        } catch (e: Exception) {
            AudioQuality.STANDARD
        }
    }

    private fun loadAudioFormat(): AudioFormatOption {
        val saved = prefs.getString(Keys.AUDIO_FORMAT, AudioFormatOption.M4A.name)
        return try {
            val format = AudioFormatOption.valueOf(saved ?: AudioFormatOption.M4A.name)
            if (format.isSupported) format else AudioFormatOption.M4A
        } catch (e: Exception) {
            AudioFormatOption.M4A
        }
    }

    private fun loadSampleRate(): SampleRateOption {
        val saved = prefs.getString(Keys.SAMPLE_RATE, SampleRateOption.SR_44_1K.name)
        return try {
            SampleRateOption.valueOf(saved ?: SampleRateOption.SR_44_1K.name)
        } catch (e: Exception) {
            SampleRateOption.SR_44_1K
        }
    }

    private fun loadBitrate(): BitrateOption {
        val saved = prefs.getString(Keys.BITRATE, BitrateOption.BR_128K.name)
        return try {
            BitrateOption.valueOf(saved ?: BitrateOption.BR_128K.name)
        } catch (e: Exception) {
            BitrateOption.BR_128K
        }
    }

    private fun loadAudioSource(): AudioSourceOption {
        val saved = prefs.getString(Keys.AUDIO_SOURCE, AudioSourceOption.MIC.name)
        return try {
            AudioSourceOption.valueOf(saved ?: AudioSourceOption.MIC.name)
        } catch (e: Exception) {
            AudioSourceOption.MIC
        }
    }

    private fun loadTranscriptionLanguage(): TranscriptionLanguage {
        val saved = prefs.getString(Keys.TRANSCRIPTION_LANGUAGE, TranscriptionLanguage.SYSTEM_DEFAULT.name)
        return try {
            TranscriptionLanguage.valueOf(saved ?: TranscriptionLanguage.SYSTEM_DEFAULT.name)
        } catch (e: Exception) {
            TranscriptionLanguage.SYSTEM_DEFAULT
        }
    }

    private fun loadThemeMode(): ThemeMode {
        val saved = prefs.getString(Keys.THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(saved ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        prefs.edit().putString(Keys.AUDIO_QUALITY, quality.name).apply()

        // When quality is changed, adapt default sample rate & bitrate accordingly
        when (quality) {
            AudioQuality.LOW -> {
                setSampleRate(SampleRateOption.SR_16K)
                setBitrate(BitrateOption.BR_64K)
            }
            AudioQuality.STANDARD -> {
                setSampleRate(SampleRateOption.SR_44_1K)
                setBitrate(BitrateOption.BR_128K)
            }
            AudioQuality.HIGH -> {
                setSampleRate(SampleRateOption.SR_48K)
                setBitrate(BitrateOption.BR_256K)
            }
        }
    }

    fun setAudioFormat(format: AudioFormatOption) {
        if (!format.isSupported) return
        _audioFormat.value = format
        prefs.edit().putString(Keys.AUDIO_FORMAT, format.name).apply()
    }

    fun setSampleRate(rate: SampleRateOption) {
        _sampleRate.value = rate
        prefs.edit().putString(Keys.SAMPLE_RATE, rate.name).apply()
    }

    fun setBitrate(bitrate: BitrateOption) {
        _bitrate.value = bitrate
        prefs.edit().putString(Keys.BITRATE, bitrate.name).apply()
    }

    fun setAudioSource(source: AudioSourceOption) {
        _audioSource.value = source
        prefs.edit().putString(Keys.AUDIO_SOURCE, source.name).apply()
    }

    fun setTranscriptionLanguage(lang: TranscriptionLanguage) {
        _transcriptionLanguage.value = lang
        prefs.edit().putString(Keys.TRANSCRIPTION_LANGUAGE, lang.name).apply()
    }

    fun setSpeakerLabelsEnabled(enabled: Boolean) {
        _speakerLabelsEnabled.value = enabled
        prefs.edit().putBoolean(Keys.SPEAKER_LABELS, enabled).apply()
    }

    fun setAutoTranscriptionEnabled(enabled: Boolean) {
        _autoTranscriptionEnabled.value = enabled
        prefs.edit().putBoolean(Keys.AUTO_TRANSCRIPTION, enabled).apply()
    }

    fun getRecordingSpeed(recordingId: Long): Float {
        return prefs.getFloat("speed_rec_$recordingId", 1.0f)
    }

    fun setRecordingSpeed(recordingId: Long, speed: Float) {
        prefs.edit().putFloat("speed_rec_$recordingId", speed).apply()
    }

    fun setSkipForwardSec(sec: Int) {
        _skipForwardSec.value = sec
        prefs.edit().putInt(Keys.SKIP_FORWARD_SEC, sec).apply()
    }

    fun setSkipBackwardSec(sec: Int) {
        _skipBackwardSec.value = sec
        prefs.edit().putInt(Keys.SKIP_BACKWARD_SEC, sec).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(Keys.THEME_MODE, mode.name).apply()
    }

    fun getRecordingsDirectory(): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun refreshStorageStats() {
        scope.launch {
            var totalSize = 0L
            var count = 0
            val pathDisplay = _storageDisplayName.value

            val treeUriStr = _storageTreeUri.value
            if (!treeUriStr.isNullOrBlank()) {
                try {
                    val treeUri = Uri.parse(treeUriStr)
                    val doc = DocumentFile.fromTreeUri(context, treeUri)
                    if (doc != null && doc.isDirectory) {
                        val children = doc.listFiles()
                        for (child in children) {
                            if (child.isFile) {
                                val name = child.name.orEmpty().lowercase()
                                if (name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".ogg")) {
                                    totalSize += child.length()
                                    count++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SettingsManager", "Failed to list tree files: ${e.message}")
                }
            } else {
                val dir = getRecordingsDirectory()
                val files = dir.listFiles() ?: emptyArray()
                for (f in files) {
                    if (f.isFile) {
                        totalSize += f.length()
                        count++
                    }
                }
            }

            // Calculate cache / temp dir size
            val cacheDir = context.cacheDir
            var cacheSize = 0L
            cacheDir.walkTopDown().forEach { f ->
                if (f.isFile) cacheSize += f.length()
            }

            _storageStats.value = StorageStats(
                recordingCount = count,
                totalSizeBytes = totalSize,
                tempSizeBytes = cacheSize,
                storagePath = pathDisplay
            )
        }
    }

    suspend fun clearTemporaryFiles(): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                freedBytes += file.length()
                file.deleteRecursively()
            }
        } catch (e: Exception) {
            // Ignore
        }
        refreshStorageStats()
        freedBytes
    }
}
