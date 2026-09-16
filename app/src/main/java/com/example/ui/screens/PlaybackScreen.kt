package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import com.example.transcription.TranscriptionStage
import com.example.transcription.TranscriptionState
import com.example.ui.RecordingShareHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PlayerState
import com.example.data.model.RecordingEntity
import com.example.data.model.TranscriptSegment
import com.example.ui.components.PlaybackWaveform
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlaybackScreen(
    recording: RecordingEntity,
    playerState: PlayerState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPausePlayback: () -> Unit = {},
    onSeek: (Long) -> Unit,
    onReplay10: () -> Unit,
    onForward30: () -> Unit,
    onSpeedCycle: () -> Unit,
    onSetSpeed: (Float) -> Unit = {},
    onToggleFavorite: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateTag: (String) -> Unit,
    onDelete: () -> Unit,
    onSegmentClick: (TranscriptSegment) -> Unit,
    transcriptionState: TranscriptionState = TranscriptionState.Idle,
    onTranscribe: () -> Unit = {},
    configuredLanguageDisplayName: String = "System Default",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(recording.title) { mutableStateOf(recording.title) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var transcriptSearchQuery by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm a", Locale.getDefault()) }
    val fullDateStr = remember(recording.createdAt) { dateFormat.format(Date(recording.createdAt)) }

    val segments = remember(recording.transcriptJson) { recording.getTranscriptSegments() }
    val filteredSegments = remember(segments, transcriptSearchQuery) {
        if (transcriptSearchQuery.isBlank()) segments
        else segments.filter { it.text.contains(transcriptSearchQuery, ignoreCase = true) }
    }
    val recordingAmplitudes = remember(recording.id, recording.amplitudesJson) {
        recording.getAmplitudes()
    }
    val availableSpeeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }

    DisposableEffect(Unit) {
        onDispose {
            onPausePlayback()
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename recording") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameDialog = false
                        onUpdateTitle(renameText)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete recording?") },
            text = { Text("Are you sure you want to permanently delete \"${recording.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete_playback_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Bar: Back, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onPausePlayback()
                        onBack()
                    },
                    modifier = Modifier.testTag("playback_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("playback_star_button")
                    ) {
                        Icon(
                            imageVector = if (recording.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (recording.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(
                            onClick = {
                                onPausePlayback()
                                shareMenuExpanded = true
                            },
                            modifier = Modifier.testTag("playback_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = shareMenuExpanded,
                            onDismissRequest = { shareMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share audio") },
                                onClick = {
                                    shareMenuExpanded = false
                                    RecordingShareHelper.shareAudio(context, recording)
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier.testTag("playback_share_audio_option")
                            )
                            DropdownMenuItem(
                                text = { Text("Share transcript") },
                                onClick = {
                                    shareMenuExpanded = false
                                    RecordingShareHelper.shareTranscript(context, recording)
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier.testTag("playback_share_transcript_option")
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            onPausePlayback()
                            showDeleteConfirmDialog = true
                        },
                        modifier = Modifier.testTag("playback_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title & Timestamp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPausePlayback()
                        showRenameDialog = true
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fullDateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = recording.tag,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

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
                            .clickable {
                                onPausePlayback()
                                onTabSelected(0)
                            }
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .testTag("playback_tab_audio")
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
                            .clickable {
                                onPausePlayback()
                                onTabSelected(1)
                            }
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .testTag("playback_tab_transcript")
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

            Spacer(modifier = Modifier.height(20.dp))

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedTab == 0) {
                    // Audio Playback Tab: Large Waveform & Scrubber
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            PlaybackWaveform(
                                amplitudes = recordingAmplitudes,
                                currentPositionMs = playerState.currentPositionMs,
                                totalDurationMs = playerState.totalDurationMs.coerceAtLeast(recording.durationMs),
                                onSeek = onSeek,
                                isPlaying = playerState.isPlaying,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("playback_waveform")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Duration indicators
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(playerState.currentPositionMs),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Text(
                                text = formatDuration(playerState.totalDurationMs.coerceAtLeast(recording.durationMs)),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Transcript Tab: On-device ML Kit Speech Recognition Transcripts
                    Column(modifier = Modifier.fillMaxSize()) {
                        when (transcriptionState) {
                            is TranscriptionState.Transcribing -> {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(24.dp)),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(48.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 4.dp
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Text(
                                            text = "Transcribing…",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val stageLabel = when (transcriptionState.stage) {
                                            TranscriptionStage.PREPARING_AUDIO -> "Preparing audio format locally…"
                                            TranscriptionStage.CHECKING_MODEL -> "Checking on-device speech model…"
                                            TranscriptionStage.DOWNLOADING_MODEL -> "Downloading speech model…"
                                            TranscriptionStage.RECOGNIZING -> "Processing speech recognition on device…"
                                            TranscriptionStage.FINALIZING -> "Finalizing transcript…"
                                        }
                                        Text(
                                            text = stageLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        if (transcriptionState.progress > 0f) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            LinearProgressIndicator(
                                                progress = { transcriptionState.progress },
                                                modifier = Modifier
                                                    .fillMaxWidth(0.8f)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                            )
                                        }
                                        if (transcriptionState.partialText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text(
                                                        text = "LIVE SPEECH",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = transcriptionState.partialText,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is TranscriptionState.Error -> {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(24.dp)),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Transcription Failed",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = transcriptionState.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(
                                            onClick = onTranscribe,
                                            modifier = Modifier.testTag("retry_transcription_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Retry transcription")
                                        }
                                    }
                                }
                            }
                            else -> {
                                if (segments.isEmpty()) {
                                    // Empty state: Allow manual transcription from recording detail screen
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(24.dp)),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "No transcript available",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Transcribe this audio locally on your device with ML Kit Speech Recognition.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Language: $configuredLanguageDisplayName",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Spacer(modifier = Modifier.height(24.dp))
                                            Button(
                                                onClick = onTranscribe,
                                                modifier = Modifier.testTag("start_transcription_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Transcribe audio")
                                            }
                                        }
                                    }
                                } else {
                                    // Completed Transcript: search, copy, share, and interactive segment list
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = transcriptSearchQuery,
                                            onValueChange = { transcriptSearchQuery = it },
                                            placeholder = { Text("Search transcript...") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            shape = RoundedCornerShape(100.dp),
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .testTag("transcript_search_input")
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        IconButton(
                                            onClick = {
                                                onPausePlayback()
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Transcript", recording.getFullTranscriptText())
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Transcript copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.testTag("copy_transcript_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Transcript",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                onPausePlayback()
                                                RecordingShareHelper.shareTranscript(context, recording)
                                            },
                                            modifier = Modifier.testTag("share_transcript_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Share,
                                                contentDescription = "Share Transcript",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(24.dp)),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp)
                                        ) {
                                            if (filteredSegments.isEmpty()) {
                                                item {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 40.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (transcriptSearchQuery.isBlank())
                                                                "No transcript available for this recording."
                                                            else "No matches found in transcript.",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }

                                            items(filteredSegments, key = { "${it.startMs}_${it.endMs}_${it.text.hashCode()}" }) { segment ->
                                                val isActive = playerState.currentPositionMs >= segment.startMs &&
                                                        playerState.currentPositionMs <= segment.endMs

                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { onSegmentClick(segment) }
                                                        .background(
                                                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                            else Color.Transparent
                                                        )
                                                        .padding(8.dp)
                                                        .testTag("transcript_segment_${segment.startMs}")
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (segment.speaker.isNotBlank()) {
                                                            Text(
                                                                text = segment.speaker,
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                        }

                                                        Text(
                                                            text = formatDuration(segment.startMs),
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontFamily = FontFamily.Monospace
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    Text(
                                                        text = segment.text,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            lineHeight = 22.sp,
                                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                                        ),
                                                        color = if (isActive) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Audio Player Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback Speed Selector Pill with Options Menu
                var speedMenuExpanded by remember { mutableStateOf(false) }
                val currentSpeedText = if (playerState.speed % 1f == 0f) "${playerState.speed.toInt()}x" else "${playerState.speed}x"

                Box {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable {
                                onPausePlayback()
                                speedMenuExpanded = true
                            }
                            .testTag("playback_speed_button")
                    ) {
                        Text(
                            text = currentSpeedText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { speedMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        availableSpeeds.forEach { speedVal ->
                            val label = if (speedVal % 1f == 0f) "${speedVal.toInt()}x" else "${speedVal}x"
                            val isSelected = kotlin.math.abs(playerState.speed - speedVal) < 0.05f
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    speedMenuExpanded = false
                                    onSetSpeed(speedVal)
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("speed_option_$label")
                            )
                        }
                    }
                }

                // Replay 10s
                IconButton(
                    onClick = onReplay10,
                    modifier = Modifier.size(48.dp).testTag("replay_10_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Replay 10 seconds",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Big Main Play/Pause Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .clickable { onPlayPause() }
                        .testTag("main_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Forward 30s
                IconButton(
                    onClick = onForward30,
                    modifier = Modifier.size(48.dp).testTag("forward_30_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward30,
                        contentDescription = "Forward 30 seconds",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Placeholder for symmetry
                Spacer(modifier = Modifier.width(36.dp))
            }
        }
    }
}
