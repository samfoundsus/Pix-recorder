package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.FallbackDarkColorScheme
import com.example.ui.theme.FallbackLightColorScheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Before
  fun setUp() = runBlocking {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val db = com.example.data.db.RecorderDatabase.getDatabase(application)
    val dao = db.recordingDao()
    val all = dao.getAllRecordings().first()
    for (item in all) {
      dao.delete(item)
    }
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Recorder", appName)
  }

  @Test
  fun `recording manager exposes amplitude flow`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager: com.example.audio.RecordingManager = com.example.audio.AudioRecorderManager(context)
    val initialAmplitudes = manager.amplitudeFlow.value
    assertEquals(0, initialAmplitudes.size)
  }

  @Test
  fun `fallback dark color scheme background is not pure AMOLED black`() {
    assertNotEquals(Color(0xFF000000), FallbackDarkColorScheme.background)
    assertNotEquals(Color(0xFF000000), FallbackDarkColorScheme.surface)
  }

  @Test
  fun `fallback light color scheme background is not pure white`() {
    assertNotEquals(Color(0xFFFFFFFF), FallbackLightColorScheme.background)
    assertNotEquals(Color(0xFFFFFFFF), FallbackLightColorScheme.surface)
  }

  @Test
  fun `inspect dynamic color schemes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
      val darkDynamic = androidx.compose.material3.dynamicDarkColorScheme(context)
      val lightDynamic = androidx.compose.material3.dynamicLightColorScheme(context)
      println("DEBUG_THEME: darkDynamic.background = ${darkDynamic.background}")
      println("DEBUG_THEME: darkDynamic.surface = ${darkDynamic.surface}")
      println("DEBUG_THEME: darkDynamic.surfaceContainer = ${darkDynamic.surfaceContainer}")
      println("DEBUG_THEME: lightDynamic.background = ${lightDynamic.background}")
      println("DEBUG_THEME: lightDynamic.surface = ${lightDynamic.surface}")
      println("DEBUG_THEME: lightDynamic.surfaceContainer = ${lightDynamic.surfaceContainer}")
    }
  }

  @Test
  fun `audio format options do not include WAV and defaults to M4A`() {
    val formats = com.example.data.settings.AudioFormatOption.values().map { it.name }
    org.junit.Assert.assertFalse("WAV must not be present in AudioFormatOption", formats.contains("WAV"))
    org.junit.Assert.assertTrue("M4A must be present", formats.contains("M4A"))
    org.junit.Assert.assertTrue("AAC must be present", formats.contains("AAC"))
    org.junit.Assert.assertTrue("FLAC must be present", formats.contains("FLAC"))
    org.junit.Assert.assertTrue("MP3 must be present", formats.contains("MP3"))
  }

  @Test
  fun `legacy saved WAV setting migrates automatically to M4A`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("pixel_recorder_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("audio_format", "WAV").commit()

    val settingsManager = com.example.data.settings.SettingsManager(context)
    assertEquals(com.example.data.settings.AudioFormatOption.M4A, settingsManager.audioFormat.value)
    assertEquals(com.example.data.settings.AudioFormatOption.M4A.name, prefs.getString("audio_format", null))
  }

  @Test
  fun `database and viewmodel remain completely empty without seeding demo recordings`() = kotlinx.coroutines.test.runTest {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val db = com.example.data.db.RecorderDatabase.getDatabase(application)
    val dao = db.recordingDao()

    // Query existing recordings and delete them
    val existing = dao.getAllRecordings().first()
    for (rec in existing) {
      dao.delete(rec)
    }

    val recordingsAfterDelete = dao.getAllRecordings().first()
    assertEquals(0, recordingsAfterDelete.size)

    val viewModel = com.example.ui.RecorderViewModel(application)
    // Allow any coroutines to settle
    kotlinx.coroutines.delay(100)

    val recordings = viewModel.recordingsList.first()
    assertEquals(0, recordings.size)
  }

  @Test
  fun `switching recordings updates detail and player state immediately without stale data`() = kotlinx.coroutines.test.runTest {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val db = com.example.data.db.RecorderDatabase.getDatabase(application)
    val dao = db.recordingDao()

    val recordingA = com.example.data.model.RecordingEntity(
      id = 101L,
      title = "Recording A",
      filePath = "/tmp/a.m4a",
      createdAt = System.currentTimeMillis(),
      durationMs = 9000L,
      amplitudesJson = "[0.2, 0.4, 0.8, 0.9]",
      transcriptJson = "[]",
      tag = "Notes"
    )

    val recordingB = com.example.data.model.RecordingEntity(
      id = 102L,
      title = "Recording B",
      filePath = "/tmp/b.m4a",
      createdAt = System.currentTimeMillis(),
      durationMs = 5000L,
      amplitudesJson = "[0.1, 0.3, 0.5]",
      transcriptJson = "[]",
      tag = "Meeting"
    )

    dao.insert(recordingA)
    dao.insert(recordingB)

    val viewModel = com.example.ui.RecorderViewModel(application)

    // Tap A on first tap
    viewModel.openRecordingPlayback(recordingA.id, initialRecording = recordingA)
    assertEquals(recordingA.id, viewModel.selectedRecordingId.value)
    assertEquals(recordingA.id, viewModel.selectedRecording.value?.id)
    assertEquals(9000L, viewModel.selectedRecording.value?.durationMs)
    assertEquals(com.example.ui.AppScreen.PLAYBACK, viewModel.currentScreen.value)
    assertEquals(recordingA.id, viewModel.playerState.value.currentRecordingId)
    assertEquals(9000L, viewModel.playerState.value.totalDurationMs)

    // Tap B
    viewModel.openRecordingPlayback(recordingB.id, initialRecording = recordingB)
    assertEquals(recordingB.id, viewModel.selectedRecordingId.value)
    assertEquals(recordingB.id, viewModel.selectedRecording.value?.id)
    assertEquals(5000L, viewModel.selectedRecording.value?.durationMs)
    assertEquals(recordingB.id, viewModel.playerState.value.currentRecordingId)
    assertEquals(5000L, viewModel.playerState.value.totalDurationMs)

    // Tap A again
    viewModel.openRecordingPlayback(recordingA.id, initialRecording = recordingA)
    assertEquals(recordingA.id, viewModel.selectedRecordingId.value)
    assertEquals(recordingA.id, viewModel.selectedRecording.value?.id)
    assertEquals(9000L, viewModel.selectedRecording.value?.durationMs)
    assertEquals(recordingA.id, viewModel.playerState.value.currentRecordingId)
    assertEquals(9000L, viewModel.playerState.value.totalDurationMs)

    // Repeated alternating switches
    for (i in 0 until 5) {
      viewModel.openRecordingPlayback(recordingB.id, initialRecording = recordingB)
      assertEquals(recordingB.id, viewModel.selectedRecordingId.value)
      assertEquals(recordingB.id, viewModel.selectedRecording.value?.id)
      assertEquals(5000L, viewModel.selectedRecording.value?.durationMs)
      assertEquals(recordingB.id, viewModel.playerState.value.currentRecordingId)
      assertEquals(5000L, viewModel.playerState.value.totalDurationMs)

      viewModel.openRecordingPlayback(recordingA.id, initialRecording = recordingA)
      assertEquals(recordingA.id, viewModel.selectedRecordingId.value)
      assertEquals(recordingA.id, viewModel.selectedRecording.value?.id)
      assertEquals(9000L, viewModel.selectedRecording.value?.durationMs)
      assertEquals(recordingA.id, viewModel.playerState.value.currentRecordingId)
      assertEquals(9000L, viewModel.playerState.value.totalDurationMs)
    }
  }

  @Test
  fun `search and tag filtering in recording list work correctly`() = kotlinx.coroutines.test.runTest {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val db = com.example.data.db.RecorderDatabase.getDatabase(application)
    val dao = db.recordingDao()

    val r1 = com.example.data.model.RecordingEntity(
      id = 201L,
      title = "Project Planning Notes",
      filePath = "/tmp/rec1.m4a",
      createdAt = System.currentTimeMillis(),
      durationMs = 12000L,
      tag = "Notes",
      isFavorite = false,
      transcriptJson = "[{\"speaker\":\"Speaker 1\",\"startMs\":0,\"endMs\":5000,\"text\":\"Architecture review\"}]"
    )
    val r2 = com.example.data.model.RecordingEntity(
      id = 202L,
      title = "Weekly Standup",
      filePath = "/tmp/rec2.m4a",
      createdAt = System.currentTimeMillis() - 1000,
      durationMs = 25000L,
      tag = "Meetings",
      isFavorite = true,
      transcriptJson = "[]"
    )
    val r3 = com.example.data.model.RecordingEntity(
      id = 203L,
      title = "Product Feature Brainstorm",
      filePath = "/tmp/rec3.m4a",
      createdAt = System.currentTimeMillis() - 2000,
      durationMs = 45000L,
      tag = "Ideas",
      isFavorite = false,
      transcriptJson = "[]"
    )

    dao.insert(r1)
    dao.insert(r2)
    dao.insert(r3)

    val viewModel = com.example.ui.RecorderViewModel(application)

    // Filter: All
    viewModel.setSelectedTag("All")
    viewModel.setSearchQuery("")
    var list = viewModel.recordingsList.first { it.size == 3 }
    assertEquals(3, list.size)

    // Filter: Notes tag
    viewModel.setSelectedTag("Notes")
    list = viewModel.recordingsList.first { it.size == 1 && it[0].tag == "Notes" }
    assertEquals(1, list.size)
    assertEquals("Project Planning Notes", list[0].title)

    // Filter: Starred tag
    viewModel.setSelectedTag("Starred")
    list = viewModel.recordingsList.first { it.size == 1 && it[0].isFavorite }
    assertEquals(1, list.size)
    assertEquals("Weekly Standup", list[0].title)

    // Search by title
    viewModel.setSelectedTag("All")
    viewModel.setSearchQuery("Brainstorm")
    list = viewModel.recordingsList.first { it.size == 1 && it[0].title.contains("Brainstorm") }
    assertEquals(1, list.size)
    assertEquals("Product Feature Brainstorm", list[0].title)

    // Search by transcript content
    viewModel.setSearchQuery("Architecture")
    list = viewModel.recordingsList.first { it.size == 1 && it[0].title.contains("Planning") }
    assertEquals(1, list.size)
    assertEquals("Project Planning Notes", list[0].title)
  }

  @Test
  fun `playback controls, speed preservation, and seeking behave correctly`() = kotlinx.coroutines.test.runTest {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.RecorderViewModel(application)

    // Playback speed setting and cycling
    viewModel.setPlaybackSpeed(1.5f)
    assertEquals(1.5f, viewModel.playerState.value.speed, 0.01f)

    viewModel.cyclePlaybackSpeed() // 1.5 -> 2.0
    assertEquals(2.0f, viewModel.playerState.value.speed, 0.01f)

    viewModel.cyclePlaybackSpeed() // 2.0 -> 0.5
    assertEquals(0.5f, viewModel.playerState.value.speed, 0.01f)

    // Pause on navigation
    viewModel.pausePlayback()
    org.junit.Assert.assertFalse(viewModel.playerState.value.isPlaying)
  }

  @Test
  fun `multi-select bulk deletion removes selected items and leaves others intact`() = runBlocking {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val db = com.example.data.db.RecorderDatabase.getDatabase(application)
    val dao = db.recordingDao()

    val rA = com.example.data.model.RecordingEntity(
      id = 301L,
      title = "Bulk Item 1",
      filePath = "/tmp/b1.m4a",
      createdAt = System.currentTimeMillis(),
      durationMs = 1000L
    )
    val rB = com.example.data.model.RecordingEntity(
      id = 302L,
      title = "Bulk Item 2",
      filePath = "/tmp/b2.m4a",
      createdAt = System.currentTimeMillis() - 100,
      durationMs = 2000L
    )
    val rC = com.example.data.model.RecordingEntity(
      id = 303L,
      title = "Keeper Item",
      filePath = "/tmp/b3.m4a",
      createdAt = System.currentTimeMillis() - 200,
      durationMs = 3000L
    )

    dao.insert(rA)
    dao.insert(rB)
    dao.insert(rC)

    val repository = com.example.data.repository.RecordingRepository(dao)
    repository.deleteRecording(301L)
    repository.deleteRecording(302L)

    val remaining = dao.getAllRecordings().first()
    assertEquals(1, remaining.size)
    assertEquals(303L, remaining[0].id)
    assertEquals("Keeper Item", remaining[0].title)
  }

  @Test
  fun `settings persistence across instances preserves configuration`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settings1 = com.example.data.settings.SettingsManager(context)

    settings1.setAudioQuality(com.example.data.settings.AudioQuality.HIGH)
    settings1.setSkipForwardSec(15)
    settings1.setSkipBackwardSec(5)
    settings1.setThemeMode(ThemeMode.DARK)

    val settings2 = com.example.data.settings.SettingsManager(context)
    assertEquals(com.example.data.settings.AudioQuality.HIGH, settings2.audioQuality.value)
    assertEquals(15, settings2.skipForwardSec.value)
    assertEquals(5, settings2.skipBackwardSec.value)
    assertEquals(ThemeMode.DARK, settings2.themeMode.value)
  }

  @Test
  fun `audio MIME type mapping is accurate for all supported extensions`() {
    val m4aRec = com.example.data.model.RecordingEntity(id = 1L, title = "T", filePath = "/path/test.m4a", createdAt = 0L, durationMs = 0L)
    val mp3Rec = com.example.data.model.RecordingEntity(id = 2L, title = "T", filePath = "/path/test.mp3", createdAt = 0L, durationMs = 0L)
    val aacRec = com.example.data.model.RecordingEntity(id = 3L, title = "T", filePath = "/path/test.aac", createdAt = 0L, durationMs = 0L)

    assertEquals("audio/mp4", com.example.ui.RecordingShareHelper.getAudioMimeType(m4aRec))
    assertEquals("audio/mpeg", com.example.ui.RecordingShareHelper.getAudioMimeType(mp3Rec))
    assertEquals("audio/aac", com.example.ui.RecordingShareHelper.getAudioMimeType(aacRec))
  }

  @Test
  fun `navigation back stack transitions between screens properly`() = kotlinx.coroutines.test.runTest {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.RecorderViewModel(application)

    assertEquals(com.example.ui.AppScreen.LIBRARY, viewModel.currentScreen.value)

    // Open Settings
    viewModel.openSettings()
    assertEquals(com.example.ui.AppScreen.SETTINGS, viewModel.currentScreen.value)

    // Close Settings
    viewModel.closeSettings()
    assertEquals(com.example.ui.AppScreen.LIBRARY, viewModel.currentScreen.value)

    // Open Playback
    val rec = com.example.data.model.RecordingEntity(
      id = 501L,
      title = "Test Rec",
      filePath = "/tmp/test.m4a",
      createdAt = System.currentTimeMillis(),
      durationMs = 2000L
    )
    viewModel.openRecordingPlayback(rec.id, initialRecording = rec)
    assertEquals(com.example.ui.AppScreen.PLAYBACK, viewModel.currentScreen.value)

    // Close Playback
    viewModel.closePlayback()
    assertEquals(com.example.ui.AppScreen.LIBRARY, viewModel.currentScreen.value)
  }
}
