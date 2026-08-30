package io.github.coderirse.reps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.coderirse.reps.data.prefs.UserSettings
import io.github.coderirse.reps.ui.RepsApp
import io.github.coderirse.reps.ui.theme.RepsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = applicationContext as RepsApplication
            val settings by app.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)
            RepsTheme(
                themeMode = settings.themeMode,
                fontScaleMultiplier = settings.fontScale.multiplier,
            ) {
                RepsApp()
            }
        }
    }
}
