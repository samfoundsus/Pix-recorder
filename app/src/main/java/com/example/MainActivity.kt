package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.RecorderViewModel
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaybackScreen
import com.example.ui.screens.RecordScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private val viewModel: RecorderViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      MyApplicationTheme(themeMode = themeMode) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          PixelRecorderApp(viewModel = viewModel)
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    viewModel.pausePlayback()
  }

  override fun onStop() {
    super.onStop()
    viewModel.pausePlayback()
  }
}

@Composable
fun PixelRecorderApp(viewModel: RecorderViewModel) {
  val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

  BackHandler(enabled = currentScreen != AppScreen.LIBRARY) {
    viewModel.pausePlayback()
    when (currentScreen) {
      AppScreen.PLAYBACK -> viewModel.closePlayback()
      AppScreen.RECORDING -> viewModel.cancelRecording()
      AppScreen.SETTINGS -> viewModel.closeSettings()
      AppScreen.LIBRARY -> {}
    }
  }

  AnimatedContent(
    targetState = currentScreen,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "ScreenTransition"
  ) { screen ->
    when (screen) {
      AppScreen.LIBRARY -> {
        val recordings by viewModel.recordingsList.collectAsStateWithLifecycle()
        val playingRecordingId by viewModel.playingRecordingId.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
        val storageTreeUri by viewModel.storageTreeUri.collectAsStateWithLifecycle()

        LibraryScreen(
          recordings = recordings,
          playingRecordingId = playingRecordingId,
          searchQuery = searchQuery,
          onSearchQueryChange = { viewModel.setSearchQuery(it) },
          selectedTag = selectedTag,
          onTagSelect = { viewModel.setSelectedTag(it) },
          availableTags = viewModel.availableTags,
          isStorageConfigured = !storageTreeUri.isNullOrBlank() && viewModel.isStorageLocationConfigured(),
          onStorageFolderSelected = { uri -> viewModel.setStorageFolderUri(uri) },
          onRecordClick = { viewModel.startNewRecording() },
          onCardClick = { recording -> viewModel.openRecordingPlayback(recording.id) },
          onPlayPause = { recording ->
            viewModel.playRecordingInline(recording)
          },
          onPausePlayback = { viewModel.pausePlayback() },
          onToggleFavorite = { recording -> viewModel.toggleFavorite(recording) },
          onRename = { id, newTitle -> viewModel.updateRecordingTitle(id, newTitle) },
          onTagChange = { id, newTag -> viewModel.updateRecordingTag(id, newTag) },
          onDelete = { id -> viewModel.deleteRecording(id) },
          onBulkDelete = { ids -> viewModel.deleteRecordings(ids) },
          onSettingsClick = { viewModel.openSettings() }
        )
      }

      AppScreen.RECORDING -> {
        val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
        val activeRecordTab by viewModel.activeRecordTab.collectAsStateWithLifecycle()

        RecordScreen(
          recordingState = recordingState,
          selectedTab = activeRecordTab,
          onTabSelected = { viewModel.setActiveRecordTab(it) },
          onPause = { viewModel.pauseRecording() },
          onResume = { viewModel.resumeRecording() },
          onDiscard = { viewModel.cancelRecording() },
          onSave = { customTitle -> viewModel.saveRecording(customTitle) },
          recordingManager = viewModel.recorderManager,
          amplitudeFlow = viewModel.amplitudeFlow,
          onPausePlayback = { viewModel.pausePlayback() }
        )
      }

      AppScreen.PLAYBACK -> {
        val selectedRecording by viewModel.selectedRecording.collectAsStateWithLifecycle()
        val playerState by viewModel.playerState.collectAsStateWithLifecycle()
        val playbackTab by viewModel.recordingDetailTab.collectAsStateWithLifecycle()
        val transcriptionStates by viewModel.transcriptionStates.collectAsStateWithLifecycle()
        val currentLanguage by viewModel.settingsManager.transcriptionLanguage.collectAsStateWithLifecycle()

        val rec = selectedRecording
        if (rec != null) {
          val currentTranscriptionState = transcriptionStates[rec.id] ?: com.example.transcription.TranscriptionState.Idle

          PlaybackScreen(
            recording = rec,
            playerState = playerState,
            selectedTab = playbackTab,
            onTabSelected = { viewModel.setRecordingDetailTab(it) },
            onBack = {
              viewModel.pausePlayback()
              viewModel.closePlayback()
            },
            onPlayPause = { viewModel.togglePlayPauseCurrent() },
            onPausePlayback = { viewModel.pausePlayback() },
            onSeek = { viewModel.seekTo(it) },
            onReplay10 = { viewModel.replay10() },
            onForward30 = { viewModel.forward30() },
            onSpeedCycle = { viewModel.cyclePlaybackSpeed() },
            onSetSpeed = { viewModel.setPlaybackSpeed(it) },
            onToggleFavorite = { viewModel.toggleFavorite(rec) },
            onUpdateTitle = { viewModel.updateRecordingTitle(rec.id, it) },
            onUpdateTag = { viewModel.updateRecordingTag(rec.id, it) },
            onDelete = { viewModel.deleteRecording(rec.id) },
            onSegmentClick = { segment -> viewModel.seekToTranscriptSegment(segment) },
            transcriptionState = currentTranscriptionState,
            onTranscribe = { viewModel.transcribeRecording(rec.id, forceRetry = true) },
            configuredLanguageDisplayName = currentLanguage.displayName
          )
        } else {
          viewModel.backToLibrary()
        }
      }

      AppScreen.SETTINGS -> {
        SettingsScreen(
          settingsManager = viewModel.settingsManager,
          onBack = {
            viewModel.pausePlayback()
            viewModel.closeSettings()
          },
          onPausePlayback = { viewModel.pausePlayback() }
        )
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "Hello $name!",
        color = MaterialTheme.colorScheme.onBackground
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Pixel Recorder") }
}
