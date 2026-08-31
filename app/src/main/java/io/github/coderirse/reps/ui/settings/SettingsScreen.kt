package io.github.coderirse.reps.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.prefs.FontScale
import io.github.coderirse.reps.data.prefs.ThemeMode
import io.github.coderirse.reps.data.prefs.UserSettings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)
    val busy by viewModel.busy.collectAsStateWithLifecycle(initialValue = false)
    val backupEvent by viewModel.backupEvent.collectAsStateWithLifecycle(initialValue = null)
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clearedMessage = stringResource(R.string.settings_clear_done)

    // SAF keeps the backup flow file-based and offline: the app never touches
    // network storage APIs directly, the user picks the destination each time.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    LaunchedEffect(backupEvent) {
        when (val event = backupEvent) {
            null -> Unit
            is BackupEvent.Done -> {
                val templateId = if (event.imported) {
                    R.string.settings_backup_import_done
                } else {
                    R.string.settings_backup_export_done
                }
                snackbarHostState.showSnackbar(
                    context.getString(templateId, event.stats.subjects, event.stats.questions),
                )
                viewModel.consumeBackupEvent()
            }
            is BackupEvent.Failed -> {
                if (event.message.isNotEmpty()) snackbarHostState.showSnackbar(event.message)
                viewModel.consumeBackupEvent()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (busy) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.settings_section_appearance))
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_theme_mode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                            ) {
                                Text(themeModeLabel(mode))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_font_size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        FontScale.entries.forEachIndexed { index, scale ->
                            SegmentedButton(
                                selected = settings.fontScale == scale,
                                onClick = { viewModel.setFontScale(scale) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = FontScale.entries.size,
                                ),
                            ) {
                                Text(fontScaleLabel(scale))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_section_data))
            SettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_backup_export)) },
                    supportingContent = { Text(stringResource(R.string.settings_backup_export_description)) },
                    modifier = Modifier.clickable(enabled = !busy) {
                        exportLauncher.launch(suggestedBackupFileName())
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_backup_import)) },
                    supportingContent = { Text(stringResource(R.string.settings_backup_import_description)) },
                    modifier = Modifier.clickable(enabled = !busy) {
                        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.settings_clear_data),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_clear_data_description),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable(enabled = !busy) { showClearDialog = true },
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsCard {
                val versionName = remember {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "—"
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_about_reps)) },
                    supportingContent = { Text(stringResource(R.string.about_version, versionName)) },
                    modifier = Modifier.clickable(onClick = onOpenAbout),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_clear_title)) },
            text = { Text(stringResource(R.string.dialog_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllData {
                            scope.launch { snackbarHostState.showSnackbar(clearedMessage) }
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.dialog_clear_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

/** reps-backup-2026-08-31-1430.json style suggestion for the SAF sheet. */
private fun suggestedBackupFileName(): String =
    "reps-backup-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".json"

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun fontScaleLabel(scale: FontScale): String = stringResource(
    when (scale) {
        FontScale.SMALL -> R.string.font_small
        FontScale.STANDARD -> R.string.font_standard
        FontScale.LARGE -> R.string.font_large
        FontScale.EXTRA_LARGE -> R.string.font_extra_large
    },
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        content()
    }
}
