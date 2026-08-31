package io.github.coderirse.reps.ui.wrongbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.db.RepsDatabase
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

    /** 重练本题: a one-question session, so the user can drill a single wrong. */
    suspend fun startSingleQuestionPractice(questionId: Long): Long? = withContext(Dispatchers.IO) {
        val question = db.questionDao().getById(questionId) ?: return@withContext null
        sessionRepository.createSession(
            subjectId = question.subjectId,
            practiceType = io.github.coderirse.reps.data.db.entity.PracticeType.WRONG_BOOK,
            reciteMode = io.github.coderirse.reps.data.db.entity.ReciteMode.TEST,
            baseQuestionIds = listOf(questionId),
        )
    }

    /**
     * Starts a wrong-book practice for one subject (its session needs a single
     * subject; the UI asks the user to pick a subject when multiple exist).
     * @return session id, or null when the subject has no unmastered wrongs
     */
    suspend fun startPractice(subjectId: Long): Long? = withContext(Dispatchers.IO) {
        val ids = db.wrongAnswerDao().getUnmasteredIdsForSubject(subjectId)
        if (ids.isEmpty()) return@withContext null
        sessionRepository.createSession(
            subjectId = subjectId,
            practiceType = "wrong_book",
            reciteMode = "mode_b_test",
            baseQuestionIds = ids,
        )
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
