package io.github.coderirse.reps.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.core.CustomOrder
import io.github.coderirse.reps.core.CustomQuota
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.SubjectEntity
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import io.github.coderirse.reps.data.prefs.SettingsRepository
import io.github.coderirse.reps.data.repo.ImportRepository
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class HomeViewModel(
    private val db: RepsDatabase,
    private val sessionRepository: StudySessionRepository,
    private val importRepository: ImportRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val subjects: Flow<List<SubjectEntity>> = db.subjectDao().observeAll()

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

    fun deleteSubject(subjectId: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.subjectDao().deleteById(subjectId) }
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

    suspend fun countsByType(subjectId: Long): Map<String, Int> = withContext(Dispatchers.IO) {
        mapOf(
            QuestionType.SINGLE to db.questionDao().countByType(subjectId, QuestionType.SINGLE),
            QuestionType.MULTI to db.questionDao().countByType(subjectId, QuestionType.MULTI),
            QuestionType.JUDGE to db.questionDao().countByType(subjectId, QuestionType.JUDGE),
        )
    }

    suspend fun chapterCounts(subjectId: Long) = withContext(Dispatchers.IO) {
        db.questionDao().getChapterCounts(subjectId)
    }

    suspend fun categoryCounts(subjectId: Long) = withContext(Dispatchers.IO) {
        db.questionDao().getCategoryCounts(subjectId)
    }

    /**
     * Creates the session and returns its id. One ACTIVE session per subject:
     * the previous one is completed on entry.
     */
    suspend fun startPractice(
        subjectId: Long,
        practiceType: String,
        reciteMode: String,
        filterDimension: String? = null,
        filterValue: String? = null,
        shuffle: Boolean = false,
        customQuota: CustomQuota? = null,
        customOrder: CustomOrder = CustomOrder.SEQUENTIAL,
        deadlineMinutes: Int = 0,
    ): Long {
        if (customQuota != null) {
            return sessionRepository.createCustomSession(
                subjectId, customQuota, customOrder, reciteMode, deadlineMinutes,
            )
        }
        val baseIds = withContext(Dispatchers.IO) {
            when (practiceType) {
                PRACTICE_WRONG_BOOK -> db.wrongAnswerDao().getUnmasteredIdsForSubject(subjectId)
                PRACTICE_FAVORITE -> db.favoriteDao().getFavoriteIdsForSubject(subjectId)
                else -> when (filterDimension) {
                    FILTER_CHAPTER -> db.questionDao().getIdsByChapter(subjectId, filterValue.orEmpty())
                    FILTER_CATEGORY -> db.questionDao().getIdsByCategory(subjectId, filterValue.orEmpty())
                    else -> db.questionDao().getIdsBySubject(subjectId)
                }
            }
        }
        return sessionRepository.createSession(
            subjectId = subjectId,
            practiceType = practiceType,
            filterValue = filterValue,
            reciteMode = reciteMode,
            baseQuestionIds = baseIds,
            shuffle = shuffle,
            deadlineMinutes = deadlineMinutes,
        )
    }

    companion object {
        const val FILTER_CHAPTER = "chapter"
        const val FILTER_CATEGORY = "category"
        const val FILTER_TARGET = "__filter_target__"
        const val PRACTICE_WRONG_BOOK = "wrong_book"
        const val PRACTICE_FAVORITE = "favorite"
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
