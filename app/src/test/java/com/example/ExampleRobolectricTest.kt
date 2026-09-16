package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.FallbackDarkColorScheme
import com.example.ui.theme.FallbackLightColorScheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
}
