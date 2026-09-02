package io.github.coderirse.reps.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.entity.FavoriteEntity
import io.github.coderirse.reps.data.db.entity.NoteEntity
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.SessionAnswerEntity
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import io.github.coderirse.reps.data.db.entity.SubjectEntity
import io.github.coderirse.reps.data.db.entity.WrongAnswerEntity
import io.github.coderirse.reps.data.prefs.SettingsRepository
import io.github.coderirse.reps.data.repo.ImportRepository
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything an FK-cascading subject delete takes with it, kept in memory so
 * the undo snackbar can put every row back with its original id.
 */
data class SubjectSnapshot(
    val subject: SubjectEntity,
    val questions: List<QuestionEntity>,
    val sessions: List<StudySessionEntity>,
    val answers: List<SessionAnswerEntity>,
    val wrongs: List<WrongAnswerEntity>,
    val favorites: List<FavoriteEntity>,
    val notes: List<NoteEntity>,
)

class HomeViewModel(
    private val db: RepsDatabase,
    private val sessionRepository: StudySessionRepository,
    private val importRepository: ImportRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val subjects: Flow<List<SubjectEntity>> = db.subjectDao().observeAll()

    /** subjectId -> distinct questions practiced; drives the card progress. */
    val practicedCounts: Flow<Map<Long, Int>> = db.sessionAnswerDao().observePracticedCounts()
        .map { rows -> rows.associate { it.subjectId to it.count } }

    /** All unfinished sessions; drives the startup restore dialog. */
    val activeSessions: Flow<List<StudySessionEntity>> = db.studySessionDao().observeActive()

    val settings: Flow<io.github.coderirse.reps.data.prefs.UserSettings> = settingsRepository.settings

    init {
        // Built-in bank: import once on first launch (and again after clear-data).
        viewModelScope.launch {
            val settings = runCatching { settingsRepository.settings.first() }.getOrNull() ?: return@launch
            when {
                !settings.builtinImported -> {
                    runCatching {
                        importRepository.importBuiltinBank(ImportRepository.BUILTIN_SUBJECT_NAME)
                    }.onSuccess { subjectId ->
                        settingsRepository.setBuiltinImported(true)
                        settingsRepository.setBuiltinSubjectId(subjectId)
                    }.onFailure { /* retry on next launch; flag stays unset */ }
                }
                // State self-heal for installs that imported before the id was recorded.
                settings.builtinSubjectId == -1L -> {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            db.subjectDao().observeAll().first().firstOrNull { it.name == ImportRepository.BUILTIN_SUBJECT_NAME }?.id
                        }
                    }.getOrNull()?.let { settingsRepository.setBuiltinSubjectId(it) }
                }
            }
        }
    }

    /**
     * Deleting a library is destructive: FK cascade also wipes its practice
     * history, wrong-book rows, favorites and notes. Capture the whole
     * subtree first, then delete — the caller can hand the snapshot to
     * [restoreSubject] from the undo snackbar.
     * @return the snapshot, or null if the subject was already gone.
     */
    suspend fun deleteSubject(subjectId: Long): SubjectSnapshot? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val subject = db.subjectDao().getById(subjectId) ?: return@withTransaction null
            val snapshot = SubjectSnapshot(
                subject = subject,
                questions = db.questionDao().getForSubject(subjectId),
                sessions = db.studySessionDao().getForSubject(subjectId),
                answers = db.sessionAnswerDao().getForSubject(subjectId),
                wrongs = db.wrongAnswerDao().getForSubject(subjectId),
                favorites = db.favoriteDao().getForSubject(subjectId),
                notes = db.noteDao().getForSubject(subjectId),
            )
            db.subjectDao().deleteById(subjectId)
            snapshot
        }
    }

    /** Undo: re-insert every row parent-first, ids unchanged, in one transaction. */
    fun restoreSubject(snapshot: SubjectSnapshot) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                db.subjectDao().upsertAll(listOf(snapshot.subject))
                db.questionDao().upsertAll(snapshot.questions)
                db.studySessionDao().upsertAll(snapshot.sessions)
                db.sessionAnswerDao().upsertAll(snapshot.answers)
                db.wrongAnswerDao().upsertAll(snapshot.wrongs)
                db.favoriteDao().upsertAll(snapshot.favorites)
                db.noteDao().upsertAll(snapshot.notes)
            }
        }
    }

    /** Sessions idle beyond the 7-day window never resurface. */
    fun expireOldSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            db.studySessionDao().expireInactiveBefore(System.currentTimeMillis() - RESTORE_WINDOW_MS)
        }
    }

    suspend fun getActiveSession(subjectId: Long): StudySessionEntity? = withContext(Dispatchers.IO) {
        db.studySessionDao().getActiveBySubject(subjectId)
    }

    suspend fun getSubjectName(subjectId: Long): String = withContext(Dispatchers.IO) {
        db.subjectDao().getById(subjectId)?.name.orEmpty()
    }

    suspend fun restartSession(sessionId: Long) {
        sessionRepository.restartSession(sessionId)
    }

    fun setAskRestore(ask: Boolean) {
        viewModelScope.launch { settingsRepository.setAskRestoreSession(ask) }
    }

    companion object {
        const val RESTORE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                HomeViewModel(
                    db = app.database,
                    sessionRepository = app.studySessionRepository,
                    importRepository = app.importRepository,
                    settingsRepository = app.settingsRepository,
                )
            }
        }
    }
}
