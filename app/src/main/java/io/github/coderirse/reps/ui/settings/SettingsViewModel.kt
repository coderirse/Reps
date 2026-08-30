package io.github.coderirse.reps.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.prefs.FontScale
import io.github.coderirse.reps.data.prefs.SettingsRepository
import io.github.coderirse.reps.data.prefs.ThemeMode
import io.github.coderirse.reps.data.prefs.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val database: RepsDatabase,
) : ViewModel() {

    val settings: Flow<UserSettings> = settingsRepository.settings

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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as RepsApplication
                SettingsViewModel(app.settingsRepository, app.database)
            }
        }
    }
}
