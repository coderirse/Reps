package io.github.coderirse.reps.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.db.entity.AnswerActionType
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.SessionStatus
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultWrongItem(
    val content: String,
    val yourAnswer: String,
    val correctAnswer: String,
)

data class ResultUiState(
    val loading: Boolean = true,
    val answered: Int = 0,
    val correct: Int = 0,
    val total: Int = 0,
    val durationMs: Long = 0,
    val wrongItems: List<ResultWrongItem> = emptyList(),
)

class ResultViewModel(
    private val sessionId: Long,
    private val sessionRepository: StudySessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state

    init {
        viewModelScope.launch {
            val session = sessionRepository.getSession(sessionId)
            if (session == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            if (session.status == SessionStatus.ACTIVE) {
                // Defensive: a result page must never leave an ACTIVE session behind.
                sessionRepository.markCompleted(session.id)
            }
            val answers = sessionRepository.getAnswers(sessionId)
                .filter { it.actionType == AnswerActionType.SELECTED }
            val questions = sessionRepository.getQuestions(session).associateBy { it.id }
            val graded = answers.filter { it.isCorrect != null }
            val wrongItems = graded.filter { it.isCorrect == false }.mapNotNull { answer ->
                questions[answer.questionId]?.let { q -> answer.toWrongItem(q) }
            }
            _state.update {
                it.copy(
                    loading = false,
                    answered = graded.size,
                    correct = graded.count { a -> a.isCorrect == true },
                    total = session.questionIds.split(",").count { s -> s.isNotBlank() },
                    durationMs = (session.lastActiveAt - session.startedAt).coerceAtLeast(0),
                    wrongItems = wrongItems,
                )
            }
        }
    }

    private fun io.github.coderirse.reps.data.db.entity.SessionAnswerEntity.toWrongItem(q: QuestionEntity) =
        ResultWrongItem(
            content = q.content,
            yourAnswer = selectedAnswer ?: "—",
            correctAnswer = q.correctAnswer,
        )

    companion object {
        fun create(sessionId: Long) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                ResultViewModel(sessionId, app.studySessionRepository)
            }
        }
    }
}
