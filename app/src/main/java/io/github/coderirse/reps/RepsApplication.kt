package io.github.coderirse.reps

import android.app.Application
import io.github.coderirse.reps.data.backup.BackupRepository
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.prefs.SettingsRepository
import io.github.coderirse.reps.data.repo.ImportRepository
import io.github.coderirse.reps.data.repo.StudySessionRepository
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

    val importRepository: ImportRepository by lazy { ImportRepository(this, database) }

    val studySessionRepository: StudySessionRepository by lazy { StudySessionRepository(database) }

    val backupRepository: BackupRepository by lazy { BackupRepository(this, database, settingsRepository) }
}
