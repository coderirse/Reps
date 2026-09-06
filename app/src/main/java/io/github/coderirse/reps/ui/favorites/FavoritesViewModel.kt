package io.github.coderirse.reps.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.dao.FavoriteRow
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.ReciteMode
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoritesViewModel(
    private val db: RepsDatabase,
    private val sessionRepository: StudySessionRepository,
) : ViewModel() {

    // Filter lives in the VM so [rows] is a single Flow: creating the Flow in
    // composition made every recomposition cancel and restart the Room query.
    private val _subjectFilter = MutableStateFlow<Long?>(null)
    val subjectFilter: StateFlow<Long?> = _subjectFilter

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: Flow<List<FavoriteRow>> =
        _subjectFilter.flatMapLatest { db.favoriteDao().observeRows(it) }

    val subjectIds: Flow<List<Long>> = db.favoriteDao().observeSubjectIds()

    fun setSubjectFilter(subjectId: Long?) {
        _subjectFilter.value = subjectId
    }

    suspend fun getSubjectName(subjectId: Long): String = withContext(Dispatchers.IO) {
        db.subjectDao().getById(subjectId)?.name.orEmpty()
    }

    suspend fun getQuestion(questionId: Long) = withContext(Dispatchers.IO) {
        db.questionDao().getById(questionId)
    }

    suspend fun getNote(questionId: Long): String? = withContext(Dispatchers.IO) {
        db.noteDao().getByQuestion(questionId)?.content
    }

    /** Blank content deletes the row so empty notes never linger (review M3). */
    fun saveNote(questionId: Long, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (content.isBlank()) {
                db.noteDao().delete(questionId)
            } else {
                db.noteDao().upsert(
                    io.github.coderirse.reps.data.db.entity.NoteEntity(
                        questionId = questionId,
                        content = content,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun removeFavorite(questionId: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.favoriteDao().remove(questionId) }
    }

    /** Re-entry guard against double taps: a second call returns null. */
    private var starting = false

    /** 重练本题: a one-question session, mirroring the wrong-book drill. */
    suspend fun startSingleQuestionPractice(questionId: Long): Long? {
        if (starting) return null
        starting = true
        return try {
            withContext(Dispatchers.IO) {
                val question = db.questionDao().getById(questionId) ?: return@withContext null
                sessionRepository.createSession(
                    subjectId = question.subjectId,
                    practiceType = PracticeType.FAVORITE,
                    reciteMode = ReciteMode.TEST,
                    baseQuestionIds = listOf(questionId),
                )
            }
        } finally {
            starting = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                FavoritesViewModel(app.database, app.studySessionRepository)
            }
        }
    }
}
