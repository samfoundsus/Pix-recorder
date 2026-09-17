package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.PlayerState
import com.example.data.model.RecordingEntity
import com.example.ui.RecordingShareHelper
import com.example.ui.components.PixelRecordFab
import com.example.ui.components.RecordingCard
import com.example.ui.theme.ThemeMode

@Composable
fun LibraryScreen(
    recordings: List<RecordingEntity>,
    playingRecordingId: Long? = null,
    playerState: PlayerState? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTag: String,
    onTagSelect: (String) -> Unit,
    availableTags: List<String>,
    isStorageConfigured: Boolean,
    onStorageFolderSelected: (Uri) -> Unit,
    onRecordClick: () -> Unit,
    onCardClick: (RecordingEntity) -> Unit,
    onPlayPause: (RecordingEntity) -> Unit,
    onPausePlayback: () -> Unit = {},
    onToggleFavorite: (RecordingEntity) -> Unit,
    onRename: (recordingId: Long, newTitle: String) -> Unit,
    onTagChange: (recordingId: Long, newTag: String) -> Unit,
    onDelete: (recordingId: Long) -> Unit,
    onBulkDelete: (recordingIds: Set<Long>) -> Unit = { ids -> ids.forEach { onDelete(it) } },
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var bulkShareMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        selectedIds = emptySet()
        isSelectionMode = false
    }

    // Permission state and launcher for RECORD_AUDIO
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showStoragePromptDialog by remember { mutableStateOf(false) }

    val storageFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onStorageFolderSelected(uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            showPermissionRationale = true
        }
    }

    val handleRecordTrigger: () -> Unit = {
        onPausePlayback()
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = isGranted
        if (!isGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (!isStorageConfigured) {
            showStoragePromptDialog = true
        } else {
            onRecordClick()
        }
    }

    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    var tagTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var selectedTagOption by remember { mutableStateOf("Notes") }

    var deleteTarget by remember { mutableStateOf<RecordingEntity?>(null) }

    // SAF Storage Location Prompt Dialog
    if (showStoragePromptDialog) {
        AlertDialog(
            onDismissRequest = { showStoragePromptDialog = false },
            title = {
                Text(
                    text = "Select recording folder",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Text(
                    text = "Pixel Recorder saves all voice recordings directly to a folder in your device's shared Internal Storage (such as Documents or Recordings) so your audio files are always user-accessible and easy to manage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStoragePromptDialog = false
                        storageFolderPickerLauncher.launch(null)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("choose_storage_folder_button")
                ) {
                    Text("Choose Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStoragePromptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Microphone permission required") },
            text = {
                Text(
                    "Pixel Recorder needs microphone access to capture clear audio and generate live transcripts. You can enable this permission here or in device Settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("grant_permission_confirm_button")
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showPermissionRationale = false
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    ) {
                        Text("App Settings")
                    }
                    TextButton(onClick = { showPermissionRationale = false }) {
                        Text("Not now")
                    }
                }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename recording") },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("library_rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            onRename(target.id, renameInputText)
                        }
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("confirm_library_rename")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    tagTarget?.let { target ->
        val tags = listOf("Notes", "Meetings", "Ideas", "Interviews", "Lectures")
        AlertDialog(
            onDismissRequest = { tagTarget = null },
            title = { Text("Select category") },
            text = {
                Column {
                    tags.forEach { tagOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTagOption = tagOption }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedTagOption == tagOption,
                                onClick = { selectedTagOption = tagOption },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = tagOption, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTagChange(target.id, selectedTagOption)
                        tagTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete recording?") },
            text = { Text("Delete \"${target.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.id)
                        deleteTarget = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBulkDeleteDialog) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete $count ${if (count == 1) "recording" else "recordings"}?") },
            text = { Text("Delete $count selected ${if (count == 1) "recording" else "recordings"}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBulkDelete(selectedIds)
                        selectedIds = emptySet()
                        isSelectionMode = false
                        showBulkDeleteDialog = false
                    },
                    modifier = Modifier.testTag("confirm_bulk_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                if (isSelectionMode) {
                    // Contextual Multi-Selection Top App Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    selectedIds = emptySet()
                                    isSelectionMode = false
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .testTag("cancel_selection_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel selection",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.testTag("selection_count_text")
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    if (selectedIds.size == recordings.size && recordings.isNotEmpty()) {
                                        selectedIds = emptySet()
                                    } else {
                                        selectedIds = recordings.map { it.id }.toSet()
                                    }
                                },
                                modifier = Modifier.testTag("select_all_button")
                            ) {
                                Text(
                                    text = if (selectedIds.size == recordings.size && recordings.isNotEmpty()) "Deselect all" else "Select all",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = {
                                        if (selectedIds.isNotEmpty()) {
                                            onPausePlayback()
                                            bulkShareMenuExpanded = true
                                        }
                                    },
                                    enabled = selectedIds.isNotEmpty(),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .testTag("bulk_share_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "Share selected",
                                        tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = bulkShareMenuExpanded,
                                    onDismissRequest = { bulkShareMenuExpanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Share selected audio") },
                                        onClick = {
                                            bulkShareMenuExpanded = false
                                            val selectedRecs = recordings.filter { it.id in selectedIds }
                                            RecordingShareHelper.shareMultipleAudio(context, selectedRecs)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        modifier = Modifier.testTag("bulk_share_audio_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share selected transcripts") },
                                        onClick = {
                                            bulkShareMenuExpanded = false
                                            val selectedRecs = recordings.filter { it.id in selectedIds }
                                            RecordingShareHelper.shareMultipleTranscripts(context, selectedRecs)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        modifier = Modifier.testTag("bulk_share_transcript_button")
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        onPausePlayback()
                                        showBulkDeleteDialog = true
                                    }
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .testTag("bulk_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete selected",
                                    tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    // App Bar Header: Recorder Title & Notes Count Pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Recorder",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Recording count pill
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.clip(CircleShape)
                            ) {
                                Text(
                                    text = "${recordings.size} voice notes",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Settings Button
                            IconButton(
                                onClick = {
                                    onPausePlayback()
                                    onSettingsClick()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (!isSelectionMode) {
                    val searchContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pixel Material 3 Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                "Search recordings & transcripts",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = searchContainerColor,
                            unfocusedContainerColor = searchContainerColor,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("library_search_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Horizontal Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableTags.forEach { tag ->
                            val isSelected = selectedTag == tag
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTagSelect(tag) },
                                label = {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("tag_chip_$tag")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Microphone Permission Banner if not granted
                if (!hasMicPermission && !isSelectionMode) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("mic_permission_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Microphone access needed",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Enable to record memos and transcribe speech.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("enable_microphone_button")
                            ) {
                                Text(
                                    text = "Enable",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }

                // List of Recordings or Empty State
                if (recordings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            val emptyIconBg = MaterialTheme.colorScheme.surfaceContainerHigh
                            Surface(
                                shape = CircleShape,
                                color = emptyIconBg,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching recordings" else "No recordings yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty())
                                    "Try a different keyword or clear your search."
                                else "Tap the microphone button below to start your first recording.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = if (isSelectionMode) 32.dp else 112.dp, top = 4.dp)
                    ) {
                        items(recordings, key = { it.id }) { recording ->
                            val isCurrentPlaying = playingRecordingId?.let { it == recording.id }
                                ?: (playerState?.currentRecordingId == recording.id && playerState.isPlaying)
                            val isSelected = recording.id in selectedIds
                            RecordingCard(
                                recording = recording,
                                isPlaying = isCurrentPlaying,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onCardClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) {
                                            selectedIds - recording.id
                                        } else {
                                            selectedIds + recording.id
                                        }
                                    } else {
                                        onCardClick(recording)
                                    }
                                },
                                onCardLongClick = {
                                    onPausePlayback()
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(recording.id)
                                    } else {
                                        selectedIds = if (isSelected) {
                                            selectedIds - recording.id
                                        } else {
                                            selectedIds + recording.id
                                        }
                                    }
                                },
                                onPlayPauseClick = { onPlayPause(recording) },
                                onPausePlayback = onPausePlayback,
                                onToggleFavorite = { onToggleFavorite(recording) },
                                onRename = {
                                    onPausePlayback()
                                    renameTarget = recording
                                    renameInputText = recording.title
                                },
                                onTagChange = {
                                    onPausePlayback()
                                    tagTarget = recording
                                    selectedTagOption = recording.tag
                                },
                                onShareAudio = {
                                    onPausePlayback()
                                    RecordingShareHelper.shareAudio(context, recording)
                                },
                                onShareTranscript = {
                                    onPausePlayback()
                                    RecordingShareHelper.shareTranscript(context, recording)
                                },
                                onDelete = {
                                    onPausePlayback()
                                    deleteTarget = recording
                                },
                                searchHighlight = searchQuery
                            )
                        }
                    }
                }
            }

            // Big Iconic Pixel Record Button at bottom center (hidden during selection mode)
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    PixelRecordFab(
                        onClick = { handleRecordTrigger() }
                    )
                }
            }
        }
    }
}
