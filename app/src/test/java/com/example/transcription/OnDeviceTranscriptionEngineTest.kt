package com.example.transcription

import com.example.data.settings.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class OnDeviceTranscriptionEngineTest {

    @Test
    fun testPlatformRequirements_enforcesAndroid12MinSdk() {
        // Enforce Android 12 (API 31) minimum requirement per spec
        assertEquals(31, MlKitSpeechTranscriptionEngine.MIN_SUPPORTED_SDK_INT)
    }

    @Test
    fun testAudioFormatSpecifications() {
        assertEquals(16000, AudioFormatConverter.TARGET_SAMPLE_RATE)
        assertEquals(1, AudioFormatConverter.TARGET_CHANNEL_COUNT)
        assertEquals(2, AudioFormatConverter.BYTES_PER_SAMPLE)
        assertEquals(32000, AudioFormatConverter.BYTES_PER_SECOND)
    }

    @Test
    fun testLanguageLocaleMapping_supportsEnglishAndHindi() {
        // Test English mapping
        val enUs = TranscriptionLanguage.EN_US
        assertEquals("en-US", enUs.code)
        assertEquals(Locale.US.language, "en")

        // Test Hindi mapping
        val hiIn = TranscriptionLanguage.HI_IN
        assertEquals("hi-IN", hiIn.code)
        val hindiLocale = Locale("hi", "IN")
        assertEquals("hi", hindiLocale.language)
        assertEquals("IN", hindiLocale.country)
    }

    @Test
    fun testTranscriptionStates_creationAndIntegrity() {
        val idle = TranscriptionState.Idle
        assertNotNull(idle)

        val transcribing = TranscriptionState.Transcribing(
            partialText = "Hello world",
            progress = 0.5f,
            stage = TranscriptionStage.RECOGNIZING,
            currentSegment = "world"
        )
        assertEquals("Hello world", transcribing.partialText)
        assertEquals(0.5f, transcribing.progress, 0.01f)
        assertEquals(TranscriptionStage.RECOGNIZING, transcribing.stage)

        val success = TranscriptionState.Success(
            fullText = "Hello world transcription",
            segments = listOf(
                TranscriptionSegment(
                    startMs = 0L,
                    endMs = 1200L,
                    text = "Hello world"
                )
            ),
            durationMs = 1200L,
            language = "en-US"
        )
        assertEquals("Hello world transcription", success.fullText)
        assertEquals(1, success.segments.size)
        assertEquals(1200L, success.durationMs)

        val error = TranscriptionState.Error(
            errorType = TranscriptionErrorType.UNSUPPORTED_OS_VERSION,
            message = "Android 12 or higher required"
        )
        assertEquals(TranscriptionErrorType.UNSUPPORTED_OS_VERSION, error.errorType)
    }

    @Test
    fun testDownloadProgressCalculation() {
        val progress = ModelDownloadProgress(bytesDownloaded = 500, totalBytes = 1000)
        assertEquals(0.5f, progress.progressFraction, 0.01f)
        assertFalse(progress.isCompleted)

        val completed = ModelDownloadProgress(bytesDownloaded = 1000, totalBytes = 1000, isCompleted = true)
        assertEquals(1.0f, completed.progressFraction, 0.01f)
        assertTrue(completed.isCompleted)
    }
}
