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
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val db: RepsDatabase,
    private val sessionRepository: StudySessionRepository,
) : ViewModel() {

    val subjects: Flow<List<SubjectEntity>> = db.subjectDao().observeAll()

    fun deleteSubject(subjectId: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.subjectDao().deleteById(subjectId) }
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
            when (filterDimension) {
                FILTER_CHAPTER -> db.questionDao().getIdsByChapter(subjectId, filterValue.orEmpty())
                FILTER_CATEGORY -> db.questionDao().getIdsByCategory(subjectId, filterValue.orEmpty())
                else -> db.questionDao().getIdsBySubject(subjectId)
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

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                HomeViewModel(app.database, app.studySessionRepository)
            }
        }
    }
}
