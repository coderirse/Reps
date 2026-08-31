package io.github.coderirse.reps.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.backup.BackupFormatException
import io.github.coderirse.reps.data.backup.BackupRepository
import io.github.coderirse.reps.data.backup.BackupStats
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.prefs.FontScale
import io.github.coderirse.reps.data.prefs.SettingsRepository
import io.github.coderirse.reps.data.prefs.ThemeMode
import io.github.coderirse.reps.data.prefs.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-shot result of a backup action; the screen shows it and clears it. */
sealed interface BackupEvent {
    data class Done(val stats: BackupStats, val imported: Boolean) : BackupEvent
    data class Failed(val message: String) : BackupEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val database: RepsDatabase,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val settings: Flow<UserSettings> = settingsRepository.settings

    /** Guards against double-tapping the backup entries. */
    private val _busy = MutableStateFlow(false)
    val busy: Flow<Boolean> = _busy.asStateFlow()

    private val _backupEvent = MutableStateFlow<BackupEvent?>(null)
    val backupEvent: Flow<BackupEvent?> = _backupEvent.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setFontScale(scale: FontScale) {
        viewModelScope.launch { settingsRepository.setFontScale(scale) }
    }

    /**
     * Wipes every table and resets the built-in bank flag so the bundled
     * bank re-imports on next launch. Runs on IO because
     * [RepsDatabase.clearAllTables] must not be called from the main thread;
     * [onDone] fires on the main thread.
     */
    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            settingsRepository.setBuiltinImported(false)
            onDone()
        }
    }

    fun exportBackup(uri: Uri) = runBackup(imported = false) { backupRepository.export(uri) }

    fun importBackup(uri: Uri) = runBackup(imported = true) { backupRepository.import(uri) }

    fun consumeBackupEvent() {
        _backupEvent.update { null }
    }

    private fun runBackup(imported: Boolean, block: suspend () -> BackupStats) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { stats -> _backupEvent.update { BackupEvent.Done(stats, imported) } }
                .onFailure { error ->
                    val message = if (error is BackupFormatException) {
                        error.message ?: "备份文件无法处理"
                    } else {
                        "操作失败：${error.message ?: error.javaClass.simpleName}"
                    }
                    _backupEvent.update { BackupEvent.Failed(message) }
                }
            _busy.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as RepsApplication
                SettingsViewModel(app.settingsRepository, app.database, app.backupRepository)
            }
        }
    }
}
