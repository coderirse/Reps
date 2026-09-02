package io.github.coderirse.reps.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showFontDialog by rememberSaveable { mutableStateOf(false) }
    var showQrDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clearedMessage = stringResource(R.string.settings_clear_done)
    val contactEmail = stringResource(R.string.settings_contact_email)

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

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
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
            Spacer(Modifier.height(8.dp))

            SectionLabel(stringResource(R.string.settings_section_appearance))
            SettingRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_theme_mode),
                subtitle = themeModeLabel(settings.themeMode),
                onClick = { showThemeDialog = true },
            )
            SettingRow(
                icon = Icons.Filled.TextFields,
                title = stringResource(R.string.settings_font_size),
                subtitle = fontScaleLabel(settings.fontScale),
                onClick = { showFontDialog = true },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_section_data))
            SettingRow(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.settings_backup_export),
                subtitle = stringResource(R.string.settings_backup_export_description),
                onClick = { if (!busy) exportLauncher.launch(suggestedBackupFileName()) },
            )
            SettingRow(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.settings_backup_import),
                subtitle = stringResource(R.string.settings_backup_import_description),
                onClick = {
                    if (!busy) {
                        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                    }
                },
            )
            SettingRow(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.settings_clear_data),
                subtitle = stringResource(R.string.settings_clear_data_description),
                destructive = true,
                onClick = { if (!busy) showClearDialog = true },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_section_about))
            SettingRow(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.settings_check_update),
                subtitle = stringResource(R.string.settings_check_update_desc),
                onClick = {
                    openUrl(context, context.getString(R.string.about_repo_url) + "/releases")
                },
                trailing = {
                    Text(
                        stringResource(R.string.settings_check_update_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            SettingRow(
                icon = Icons.Filled.MailOutline,
                title = stringResource(R.string.settings_contact_author),
                subtitle = contactEmail,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$contactEmail")),
                        )
                    }
                },
            )
            SettingRow(
                icon = Icons.Filled.QrCode2,
                title = stringResource(R.string.settings_feedback),
                subtitle = stringResource(R.string.settings_feedback_desc),
                onClick = { showQrDialog = true },
            )
            SettingRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about_reps),
                subtitle = stringResource(R.string.about_version, versionName),
                onClick = onOpenAbout,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text(stringResource(R.string.settings_feedback)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.qq_qr),
                        contentDescription = stringResource(R.string.settings_feedback_desc),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_feedback_qq),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme_dialog_title),
            options = ThemeMode.entries.map { it to themeModeLabel(it) },
            selected = settings.themeMode,
            onSelect = { viewModel.setThemeMode(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showFontDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_font_dialog_title),
            options = FontScale.entries.map { it to fontScaleLabel(it) },
            selected = settings.fontScale,
            onSelect = { viewModel.setFontScale(it); showFontDialog = false },
            onDismiss = { showFontDialog = false },
        )
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

/** Opens a URL in the system browser; the app itself has no INTERNET permission. */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** Card row in the reference style: icon box, title, small subtitle, trailing action. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (destructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
