package com.example.data.settings

import android.media.MediaRecorder
import android.os.Build

enum class AudioQuality(val displayName: String, val bitrate: Int, val sampleRate: Int, val subtitle: String) {
    LOW("Low", 64_000, 16_000, "Smaller file size, ideal for voice memos"),
    STANDARD("Standard", 128_000, 44_100, "Balanced clarity and file size"),
    HIGH("High", 256_000, 48_000, "High fidelity, clear voice & acoustic detail")
}

enum class AudioFormatOption(
    val displayName: String,
    val extension: String,
    val outputFormat: Int,
    val audioEncoder: Int,
    val isSupported: Boolean,
    val subtitle: String
) {
    M4A("M4A (AAC)", "m4a", MediaRecorder.OutputFormat.MPEG_4, MediaRecorder.AudioEncoder.AAC, true, "Standard Pixel audio format with high compression efficiency"),
    AAC("AAC", "aac", MediaRecorder.OutputFormat.AAC_ADTS, MediaRecorder.AudioEncoder.AAC, true, "Advanced Audio Coding stream"),
    FLAC("FLAC (Lossless)", "flac", -1, -1, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O, "Free Lossless Audio Codec (API 27+)"),
    MP3("MP3", "mp3", -1, -1, false, "Not natively supported by MediaRecorder encoder on Android")
}

enum class SampleRateOption(val rate: Int, val displayName: String) {
    SR_16K(16_000, "16 kHz (Voice)"),
    SR_44_1K(44_100, "44.1 kHz (CD Standard)"),
    SR_48K(48_000, "48 kHz (Studio)")
}

enum class BitrateOption(val bitrate: Int, val displayName: String) {
    BR_64K(64_000, "64 kbps"),
    BR_128K(128_000, "128 kbps (Default)"),
    BR_192K(192_000, "192 kbps"),
    BR_256K(256_000, "256 kbps (High Quality)"),
    BR_320K(320_000, "320 kbps (Maximum)")
}

enum class AudioSourceOption(val source: Int, val displayName: String, val subtitle: String) {
    MIC(MediaRecorder.AudioSource.MIC, "Microphone", "Device microphone capture"),
    DEFAULT(MediaRecorder.AudioSource.DEFAULT, "Default", "System default audio source"),
    VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION, "Voice Recognition", "Tuned for speech & noise suppression"),
    CAMCORDER(MediaRecorder.AudioSource.CAMCORDER, "Camcorder", "Directional microphone tuning")
}

enum class TranscriptionLanguage(val code: String, val displayName: String) {
    SYSTEM_DEFAULT("", "System Default"),
    EN_US("en-US", "English (United States)"),
    EN_GB("en-GB", "English (United Kingdom)"),
    ES_ES("es-ES", "Spanish (Spain)"),
    FR_FR("fr-FR", "French (France)"),
    DE_DE("de-DE", "German (Germany)"),
    JA_JP("ja-JP", "Japanese (Japan)"),
    HI_IN("hi-IN", "Hindi (India)"),
    ZH_CN("zh-CN", "Chinese (Simplified)")
}

data class StorageStats(
    val recordingCount: Int,
    val totalSizeBytes: Long,
    val tempSizeBytes: Long,
    val storagePath: String
) {
    fun formatTotalSize(): String = formatBytes(totalSizeBytes)
    fun formatTempSize(): String = formatBytes(tempSizeBytes)

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024) {
            return "%.1f KB".format(kb)
        }
        val mb = kb / 1024.0
        if (mb < 1024) {
            return "%.1f MB".format(mb)
        }
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
