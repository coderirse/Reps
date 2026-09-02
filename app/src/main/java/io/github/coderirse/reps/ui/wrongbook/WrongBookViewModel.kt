package io.github.coderirse.reps.ui.wrongbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.ReciteMode
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WrongBookViewModel(
    private val db: RepsDatabase,
    private val sessionRepository: StudySessionRepository,
) : ViewModel() {

    val unmastered = db.wrongAnswerDao().observeUnmasteredRows()
    val mastered = db.wrongAnswerDao().observeMasteredRows()

    fun setMastered(questionId: Long, mastered: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { db.wrongAnswerDao().setMastered(questionId, mastered) }
    }

    suspend fun getUnmasteredCountsBySubject(): List<io.github.coderirse.reps.data.db.dao.SubjectWrongCount> =
        withContext(Dispatchers.IO) { db.wrongAnswerDao().getUnmasteredCountsBySubject() }

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

    /** Re-entry guard against double taps: a second call returns null. */
    private var starting = false

    /** 重练本题: a one-question session, so the user can drill a single wrong. */
    suspend fun startSingleQuestionPractice(questionId: Long): Long? {
        if (starting) return null
        starting = true
        return try {
            withContext(Dispatchers.IO) {
                val question = db.questionDao().getById(questionId) ?: return@withContext null
                sessionRepository.createSession(
                    subjectId = question.subjectId,
                    practiceType = PracticeType.WRONG_BOOK,
                    reciteMode = ReciteMode.TEST,
                    baseQuestionIds = listOf(questionId),
                )
            }
        } finally {
            starting = false
        }
    }

    suspend fun getSubjectName(subjectId: Long): String = withContext(Dispatchers.IO) {
        db.subjectDao().getById(subjectId)?.name.orEmpty()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                WrongBookViewModel(app.database, app.studySessionRepository)
            }
        }
    }
}
