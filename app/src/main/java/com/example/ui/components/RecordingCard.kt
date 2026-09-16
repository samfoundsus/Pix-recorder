package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RecordingEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingCard(
    recording: RecordingEntity,
    isPlaying: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onCardClick: () -> Unit,
    onCardLongClick: (() -> Unit)? = null,
    onPlayPauseClick: () -> Unit,
    onPausePlayback: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onTagChange: () -> Unit,
    onShareAudio: () -> Unit,
    onShareTranscript: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    searchHighlight: String = ""
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val dateStr = remember(recording.createdAt) { dateFormat.format(Date(recording.createdAt)) }
    val durationStr = remember(recording.durationMs) { formatDuration(recording.durationMs) }
    val transcriptSnippet = remember(recording.transcriptJson) {
        val text = recording.getFullTranscriptText()
        if (text.isNotBlank()) text else "No transcript available"
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val baseCardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val unselectedCardColor = if (isDark) lerp(baseCardColor, Color.Black, 0.40f) else baseCardColor
    val basePillColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val pillBgColor = if (isDark) lerp(basePillColor, Color.Black, 0.40f) else basePillColor
    val menuBgColor = if (isDark) lerp(MaterialTheme.colorScheme.surfaceContainerHigh, Color.Black, 0.40f) else MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onCardClick() },
                onLongClick = {
                    onPausePlayback()
                    onCardLongClick?.invoke()
                }
            )
            .testTag("recording_item_card_${recording.id}"),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else unselectedCardColor,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = if (isSelected) 4.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title and Star / Overflow OR Selection Checkmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.1.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$dateStr • $durationStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Category Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(pillBgColor)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = recording.tag,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .testTag("selection_indicator_${recording.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(36.dp).testTag("favorite_button_${recording.id}")
                        ) {
                            Icon(
                                imageVector = if (recording.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (recording.isFavorite) "Starred" else "Not starred",
                                tint = if (recording.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = {
                                    onPausePlayback()
                                    menuExpanded = true
                                },
                                modifier = Modifier.size(36.dp).testTag("more_button_${recording.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(menuBgColor)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        menuExpanded = false
                                        onRename()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Category") },
                                    onClick = {
                                        menuExpanded = false
                                        onTagChange()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share audio") },
                                    onClick = {
                                        menuExpanded = false
                                        onShareAudio()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    modifier = Modifier.testTag("menu_share_audio_${recording.id}")
                                )
                                DropdownMenuItem(
                                    text = { Text("Share transcript") },
                                    onClick = {
                                        menuExpanded = false
                                        onShareTranscript()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    modifier = Modifier.testTag("menu_share_transcript_${recording.id}")
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle: Mini Waveform & Play Action Row
            val amplitudes = remember(recording.id, recording.amplitudesJson) {
                recording.getAmplitudes()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Mini Waveform
                MiniWaveformPreview(
                    amplitudes = amplitudes,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .padding(end = 12.dp),
                    barColor = if (isPlaying && !isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    isPlaying = isPlaying && !isSelectionMode
                )

                // Circular Play Button
                Surface(
                    shape = CircleShape,
                    color = if (isSelectionMode) MaterialTheme.colorScheme.surfaceContainerHighest
                            else if (isPlaying) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isSelectionMode) {
                                onCardClick()
                            } else {
                                onPlayPauseClick()
                            }
                        }
                        .testTag("play_pause_button_${recording.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying && !isSelectionMode) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying && !isSelectionMode) "Pause" else "Play",
                            tint = if (isPlaying && !isSelectionMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom: Transcript snippet preview
            Text(
                text = transcriptSnippet,
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun formatTimer(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
