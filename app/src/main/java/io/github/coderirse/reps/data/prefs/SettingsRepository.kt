package io.github.coderirse.reps.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Relative multiplier applied on top of the system font scale. */
enum class FontScale(val multiplier: Float) {
    SMALL(0.85f),
    STANDARD(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.STANDARD,
    /** Suppresses the session-restore dialog on launch ("下次不再询问"). */
    val askRestoreSession: Boolean = true,
    /** Built-in bank already imported into Room (re-run after clear-all-data). */
    val builtinImported: Boolean = false,
    /** Subject id of the built-in bank (-1 = none); the UI hides delete for it. */
    val builtinSubjectId: Long = -1L,
) {
    companion object {
        val DEFAULT = UserSettings()
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val ASK_RESTORE_SESSION = booleanPreferencesKey("ask_restore_session")
        val BUILTIN_IMPORTED = booleanPreferencesKey("builtin_imported")
        val BUILTIN_SUBJECT_ID = longPreferencesKey("builtin_subject_id")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            fontScale = prefs[Keys.FONT_SCALE]
                ?.let { runCatching { FontScale.valueOf(it) }.getOrNull() }
                ?: FontScale.STANDARD,
            askRestoreSession = prefs[Keys.ASK_RESTORE_SESSION] ?: true,
            builtinImported = prefs[Keys.BUILTIN_IMPORTED] ?: false,
            builtinSubjectId = prefs[Keys.BUILTIN_SUBJECT_ID] ?: -1L,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setFontScale(scale: FontScale) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale.name }
    }

    suspend fun setAskRestoreSession(ask: Boolean) {
        context.dataStore.edit { it[Keys.ASK_RESTORE_SESSION] = ask }
    }

    suspend fun setBuiltinImported(imported: Boolean) {
        context.dataStore.edit { it[Keys.BUILTIN_IMPORTED] = imported }
    }

    suspend fun setBuiltinSubjectId(subjectId: Long) {
        context.dataStore.edit { it[Keys.BUILTIN_SUBJECT_ID] = subjectId }
    }
}
