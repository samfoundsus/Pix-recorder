package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ActiveRecordingState
import com.example.audio.AudioRecorderManager
import com.example.ui.components.LiveRecordingWaveform
import com.example.ui.components.RealtimeAudioWaveform
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimer
import kotlinx.coroutines.flow.Flow

@Composable
fun RecordScreen(
    recordingState: ActiveRecordingState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (customTitle: String?) -> Unit,
    modifier: Modifier = Modifier,
    recordingManager: AudioRecorderManager? = null,
    amplitudeFlow: Flow<List<Float>>? = null,
    onPausePlayback: () -> Unit = {}
) {
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var showSaveNamingDialog by remember { mutableStateOf(false) }
    var customTitleInput by remember { mutableStateOf("") }
    var wasRecordingBeforeDialog by remember { mutableStateOf(false) }

    val onDismissOrCancelDialog = {
        if (wasRecordingBeforeDialog) {
            onResume()
        }
        wasRecordingBeforeDialog = false
    }

    BackHandler(enabled = true) {
        if (showSaveNamingDialog) {
            showSaveNamingDialog = false
            onDismissOrCancelDialog()
        } else if (showDiscardConfirmDialog) {
            showDiscardConfirmDialog = false
            onDismissOrCancelDialog()
        } else {
            onPausePlayback()
            val wasRecording = !recordingState.isPaused
            wasRecordingBeforeDialog = wasRecording
            if (wasRecording) {
                onPause()
            }
            showDiscardConfirmDialog = true
        }
    }

    val dotAlpha = if (!recordingState.isPaused) {
        val infiniteTransition = rememberInfiniteTransition(label = "recDot")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        )
        alpha
    } else 1.0f

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardConfirmDialog = false
                onDismissOrCancelDialog()
            },
            title = { Text("Discard recording?") },
            text = { Text("This recording and its live transcript will not be saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                        wasRecordingBeforeDialog = false
                        onDiscard()
                    },
                    modifier = Modifier.testTag("confirm_discard_button")
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onDismissOrCancelDialog()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSaveNamingDialog) {
        AlertDialog(
            onDismissRequest = {
                showSaveNamingDialog = false
                onDismissOrCancelDialog()
            },
            title = { Text("Save recording") },
            text = {
                Column {
                    Text(
                        "Enter a title or leave blank for automatic timestamp:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customTitleInput,
                        onValueChange = { customTitleInput = it },
                        placeholder = { Text("e.g. Brainstorming session") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("save_title_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveNamingDialog = false
                        wasRecordingBeforeDialog = false
                        onSave(customTitleInput.ifBlank { null })
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("confirm_save_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveNamingDialog = false
                        onDismissOrCancelDialog()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Status Bar: Status Pill & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recording Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (recordingState.isPaused) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (recordingState.isPaused) "PAUSED" else "RECORDING",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (recordingState.isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                }

                // Audio Quality / Mic info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Standard • 44.1 kHz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Prominent Duration Timer
            Text(
                text = formatTimer(recordingState.durationMs),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Dual Tab Switcher: Audio vs Transcript
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val audioSelected = selectedTab == 0
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (audioSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { onTabSelected(0) }
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .testTag("record_tab_audio")
                    ) {
                        Text(
                            text = "Audio",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (audioSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (audioSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val transcriptSelected = selectedTab == 1
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (transcriptSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { onTabSelected(1) }
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .testTag("record_tab_transcript")
                    ) {
                        Text(
                            text = "Transcript",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (transcriptSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (transcriptSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Center Content: Audio Waveform vs Live Transcript
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedTab == 0) {
                    // Audio Tab View: Dynamic Waveform Canvas & dB Level
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (recordingManager != null) {
                            RealtimeAudioWaveform(
                                recordingManager = recordingManager,
                                isPaused = recordingState.isPaused,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            )
                        } else if (amplitudeFlow != null) {
                            RealtimeAudioWaveform(
                                amplitudeFlow = amplitudeFlow,
                                isPaused = recordingState.isPaused,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            )
                        } else {
                            LiveRecordingWaveform(
                                amplitudes = recordingState.amplitudes,
                                isPaused = recordingState.isPaused,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Mic Level / dB Indicator with visual audio meter
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (recordingState.isPaused) "Recording paused"
                                else "Sound level: ${recordingState.currentDb.toInt()} dB",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!recordingState.isPaused) {
                                val meterProgress = ((recordingState.currentDb - 30f) / 50f).coerceIn(0.1f, 1f)
                                Surface(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(6.dp),
                                    shape = RoundedCornerShape(3.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = meterProgress)
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                        MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Transcript Tab View: Real-time Live Speech Stream
                    val listState = rememberLazyListState()

                    LaunchedEffect(recordingState.segments.size, recordingState.liveTranscript) {
                        if (recordingState.segments.isNotEmpty()) {
                            listState.animateScrollToItem(recordingState.segments.lastIndex)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            if (recordingState.segments.isEmpty() && recordingState.liveTranscript.isBlank()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Speak into the microphone.\nTranscription will appear here automatically.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            items(recordingState.segments, key = { "${it.startMs}_${it.endMs}_${it.speaker}" }) { segment ->
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (segment.speaker.isNotBlank()) {
                                            Text(
                                                text = segment.speaker,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = formatDuration(segment.startMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = segment.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (recordingState.liveTranscript.isNotBlank()) {
                                item {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = "Live Transcription",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = recordingState.liveTranscript,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                lineHeight = 22.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Actions Bar: Discard, Pause/Resume, Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Discard Button (Trash)
                IconButton(
                    onClick = {
                        onPausePlayback()
                        val wasRecording = !recordingState.isPaused
                        wasRecordingBeforeDialog = wasRecording
                        if (wasRecording) {
                            onPause()
                        }
                        showDiscardConfirmDialog = true
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("discard_recording_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Discard Recording",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Pause / Resume Center Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (recordingState.isPaused) onResume() else onPause()
                        }
                        .testTag("pause_resume_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (recordingState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (recordingState.isPaused) "Resume" else "Pause",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Save Pill Button (Pixel style)
                Button(
                    onClick = {
                        onPausePlayback()
                        val wasRecording = !recordingState.isPaused
                        wasRecordingBeforeDialog = wasRecording
                        if (wasRecording) {
                            onPause()
                        }
                        showSaveNamingDialog = true
                    },
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("save_recording_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}
