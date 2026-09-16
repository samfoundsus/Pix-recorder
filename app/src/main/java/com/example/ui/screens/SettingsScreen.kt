package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.settings.AudioFormatOption
import com.example.data.settings.AudioQuality
import com.example.data.settings.AudioSourceOption
import com.example.data.settings.BitrateOption
import com.example.data.settings.SampleRateOption
import com.example.data.settings.SettingsManager
import com.example.data.settings.TranscriptionLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onPausePlayback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // SAF Folder Picker launcher for changing storage location
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val success = settingsManager.setStorageTreeUri(uri)
            if (success) {
                Toast.makeText(context, "Storage location updated to ${settingsManager.formatTreeUriName(uri)}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to set storage location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Settings StateFlows
    val audioQuality by settingsManager.audioQuality.collectAsStateWithLifecycle()
    val audioFormat by settingsManager.audioFormat.collectAsStateWithLifecycle()
    val sampleRate by settingsManager.sampleRate.collectAsStateWithLifecycle()
    val bitrate by settingsManager.bitrate.collectAsStateWithLifecycle()
    val audioSource by settingsManager.audioSource.collectAsStateWithLifecycle()
    val transcriptionLanguage by settingsManager.transcriptionLanguage.collectAsStateWithLifecycle()
    val speakerLabelsEnabled by settingsManager.speakerLabelsEnabled.collectAsStateWithLifecycle()
    val autoTranscriptionEnabled by settingsManager.autoTranscriptionEnabled.collectAsStateWithLifecycle()
    val skipForwardSec by settingsManager.skipForwardSec.collectAsStateWithLifecycle()
    val skipBackwardSec by settingsManager.skipBackwardSec.collectAsStateWithLifecycle()
    val themeMode by settingsManager.themeMode.collectAsStateWithLifecycle()
    val storageStats by settingsManager.storageStats.collectAsStateWithLifecycle()

    // Dialog state
    var showQualityDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showSampleRateDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSkipForwardDialog by remember { mutableStateOf(false) }
    var showSkipBackwardDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showStorageLocationDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsManager.refreshStorageStats()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onPausePlayback()
                            onBack()
                        },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Library"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Recording Section
            item {
                SettingsSectionHeader(title = "Recording")
            }
            item {
                SettingsCardGroup {
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.Tune,
                        title = "Audio quality",
                        subtitle = "${audioQuality.displayName} • ${audioQuality.subtitle}",
                        testTag = "setting_audio_quality",
                        onClick = { showQualityDialog = true }
                    )
                    SettingsDivider()
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.Audiotrack,
                        title = "Audio format",
                        subtitle = "${audioFormat.displayName} • ${audioFormat.subtitle}",
                        testTag = "setting_audio_format",
                        onClick = { showFormatDialog = true }
                    )
                    SettingsDivider()
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.GraphicEq,
                        title = "Sample rate",
                        subtitle = sampleRate.displayName,
                        testTag = "setting_sample_rate",
                        onClick = { showSampleRateDialog = true }
                    )
                    SettingsDivider()
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.Radio,
                        title = "Bitrate",
                        subtitle = bitrate.displayName,
                        testTag = "setting_bitrate",
                        onClick = { showBitrateDialog = true }
                    )
                    SettingsDivider()
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.Mic,
                        title = "Audio source",
                        subtitle = "${audioSource.displayName} (${audioSource.subtitle})",
                        testTag = "setting_audio_source",
                        onClick = { showAudioSourceDialog = true }
                    )
                }
            }

            // 2. Transcription Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader(title = "Transcription")
            }
            item {
                SettingsCardGroup {
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.Language,
                        title = "Transcription language",
                        subtitle = transcriptionLanguage.displayName,
                        testTag = "setting_transcription_language",
                        onClick = { showLanguageDialog = true }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Outlined.People,
                        title = "Speaker labels",
                        subtitle = "Identify and label distinct speakers (Speaker 1, Speaker 2)",
                        checked = speakerLabelsEnabled,
                        onCheckedChange = { settingsManager.setSpeakerLabelsEnabled(it) },
                        testTag = "setting_speaker_labels_switch"
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Outlined.RecordVoiceOver,
                        title = "Automatic transcription",
                        subtitle = "Transcribe speech automatically during recordings",
                        checked = autoTranscriptionEnabled,
                        onCheckedChange = { settingsManager.setAutoTranscriptionEnabled(it) },
                        testTag = "setting_auto_transcription_switch"
                    )
                }
            }

            // 3. Playback Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader(title = "Playback")
            }
            item {
                SettingsCardGroup {
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.FastForward,
                        title = "Skip forward",
                        subtitle = "${skipForwardSec} seconds",
                        testTag = "setting_skip_forward",
                        onClick = { showSkipForwardDialog = true }
                    )
                    SettingsDivider()
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.FastRewind,
                        title = "Skip backward",
                        subtitle = "${skipBackwardSec} seconds",
                        testTag = "setting_skip_backward",
                        onClick = { showSkipBackwardDialog = true }
                    )
                }
            }

            // 4. Appearance Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader(title = "Appearance")
            }
            item {
                SettingsCardGroup {
                    SettingsPreferenceRow(
                        icon = when (themeMode) {
                            ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Outlined.LightMode
                            ThemeMode.DARK -> Icons.Outlined.DarkMode
                        },
                        title = "Theme",
                        subtitle = when (themeMode) {
                            ThemeMode.SYSTEM -> "System default"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        testTag = "setting_theme_mode",
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            // 5. Storage Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader(title = "Storage")
            }
            item {
                SettingsCardGroup {
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.Folder,
                        title = "Recording storage location",
                        subtitle = storageStats.storagePath,
                        testTag = "setting_storage_location",
                        onClick = { showStorageLocationDialog = true }
                    )
                    SettingsDivider()
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.FolderOpen,
                        title = "Change storage location",
                        subtitle = "Select a folder in shared Internal Storage",
                        testTag = "setting_change_storage_location",
                        onClick = { folderPickerLauncher.launch(null) }
                    )
                    SettingsDivider()
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.Storage,
                        title = "Storage used",
                        subtitle = "${storageStats.formatTotalSize()} (${storageStats.recordingCount} audio files)",
                        testTag = "setting_storage_used",
                        onClick = {
                            settingsManager.refreshStorageStats()
                            Toast.makeText(context, "Storage refreshed", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingsDivider()
                    SettingsPreferenceRow(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Clear temporary files",
                        subtitle = "Free temporary caches (${storageStats.formatTempSize()})",
                        testTag = "setting_clear_cache",
                        onClick = { showClearCacheConfirmDialog = true }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Pixel Recorder • High Fidelity Voice & Transcripts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // --- Dialogs ---

    // Quality Dialog
    if (showQualityDialog) {
        SingleChoiceDialog(
            title = "Audio quality",
            options = AudioQuality.values().toList(),
            selectedOption = audioQuality,
            optionLabel = { "${it.displayName} (${it.subtitle})" },
            onSelect = {
                settingsManager.setAudioQuality(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    // Format Dialog
    if (showFormatDialog) {
        val formats = AudioFormatOption.values().toList()
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Audio format") },
            text = {
                Column {
                    formats.forEach { format ->
                        val enabled = format.isSupported
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled) {
                                    settingsManager.setAudioFormat(format)
                                    showFormatDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = audioFormat == format,
                                onClick = if (enabled) {
                                    {
                                        settingsManager.setAudioFormat(format)
                                        showFormatDialog = false
                                    }
                                } else null,
                                enabled = enabled,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = format.displayName + if (!enabled) " (Unavailable on device)" else "",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (audioFormat == format) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = format.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.8f else 0.4f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormatDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Sample Rate Dialog
    if (showSampleRateDialog) {
        SingleChoiceDialog(
            title = "Sample rate",
            options = SampleRateOption.values().toList(),
            selectedOption = sampleRate,
            optionLabel = { it.displayName },
            onSelect = {
                settingsManager.setSampleRate(it)
                showSampleRateDialog = false
            },
            onDismiss = { showSampleRateDialog = false }
        )
    }

    // Bitrate Dialog
    if (showBitrateDialog) {
        SingleChoiceDialog(
            title = "Bitrate",
            options = BitrateOption.values().toList(),
            selectedOption = bitrate,
            optionLabel = { it.displayName },
            onSelect = {
                settingsManager.setBitrate(it)
                showBitrateDialog = false
            },
            onDismiss = { showBitrateDialog = false }
        )
    }

    // Audio Source Dialog
    if (showAudioSourceDialog) {
        SingleChoiceDialog(
            title = "Audio source",
            options = AudioSourceOption.values().toList(),
            selectedOption = audioSource,
            optionLabel = { "${it.displayName} • ${it.subtitle}" },
            onSelect = {
                settingsManager.setAudioSource(it)
                showAudioSourceDialog = false
            },
            onDismiss = { showAudioSourceDialog = false }
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        SingleChoiceDialog(
            title = "Transcription language",
            options = TranscriptionLanguage.values().toList(),
            selectedOption = transcriptionLanguage,
            optionLabel = { it.displayName },
            onSelect = {
                settingsManager.setTranscriptionLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // Skip Forward Dialog
    if (showSkipForwardDialog) {
        val options = listOf(5, 10, 15, 30, 60)
        SingleChoiceDialog(
            title = "Skip forward duration",
            options = options,
            selectedOption = skipForwardSec,
            optionLabel = { "$it seconds" },
            onSelect = {
                settingsManager.setSkipForwardSec(it)
                showSkipForwardDialog = false
            },
            onDismiss = { showSkipForwardDialog = false }
        )
    }

    // Skip Backward Dialog
    if (showSkipBackwardDialog) {
        val options = listOf(5, 10, 15, 30)
        SingleChoiceDialog(
            title = "Skip backward duration",
            options = options,
            selectedOption = skipBackwardSec,
            optionLabel = { "$it seconds" },
            onSelect = {
                settingsManager.setSkipBackwardSec(it)
                showSkipBackwardDialog = false
            },
            onDismiss = { showSkipBackwardDialog = false }
        )
    }

    // Theme Dialog
    if (showThemeDialog) {
        SingleChoiceDialog(
            title = "Theme",
            options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
            selectedOption = themeMode,
            optionLabel = {
                when (it) {
                    ThemeMode.SYSTEM -> "System default"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
            },
            onSelect = {
                settingsManager.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // Storage Location Dialog
    if (showStorageLocationDialog) {
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showStorageLocationDialog = false },
            title = { Text("Recording storage location") },
            text = {
                Column {
                    Text(
                        text = "Voice recordings are saved directly to your selected folder in shared Internal Storage using Android's Storage Access Framework, keeping your audio files fully user-accessible:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = storageStats.storagePath,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            showStorageLocationDialog = false
                            folderPickerLauncher.launch(null)
                        }
                    ) {
                        Text("Change Folder")
                    }
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(storageStats.storagePath))
                            Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                            showStorageLocationDialog = false
                        }
                    ) {
                        Text("Copy Path")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageLocationDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Cache Confirmation Dialog
    if (showClearCacheConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmDialog = false },
            title = { Text("Clear temporary files?") },
            text = {
                Text(
                    text = "This will remove cached waveform previews and temporary audio buffers (${storageStats.formatTempSize()}). Your saved voice recordings will NOT be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirmDialog = false
                        coroutineScope.launch {
                            val freed = settingsManager.clearTemporaryFiles()
                            val msg = if (freed > 0) {
                                val mb = freed / (1024.0 * 1024.0)
                                "Cleared %.2f MB of temporary files".format(mb)
                            } else {
                                "Temporary cache is already clean"
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsPreferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    val iconBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = iconBgColor,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val iconBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = iconBgColor,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
