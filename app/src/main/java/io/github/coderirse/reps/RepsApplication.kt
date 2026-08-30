package io.github.coderirse.reps

import android.app.Application
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. Kept deliberately simple for the single-module
 * MVP; revisit (e.g. Hilt) if the project ever gets split into modules.
 */
class RepsApplication : Application() {

    /**
     * Application-scoped IO scope for work that must survive ViewModel/Activity
     * teardown, e.g. session saves triggered on ON_STOP.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: RepsDatabase by lazy { RepsDatabase.build(this) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
