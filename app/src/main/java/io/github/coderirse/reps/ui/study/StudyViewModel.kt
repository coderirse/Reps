package io.github.coderirse.reps.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.core.Grading
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.entity.AnswerActionType
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.ReciteMode
import io.github.coderirse.reps.data.db.entity.SessionStatus
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import io.github.coderirse.reps.data.prefs.SettingsRepository
import io.github.coderirse.reps.data.prefs.ThemeMode
import io.github.coderirse.reps.data.prefs.UserSettings
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Per-question state inside the current session (mirrors session_answers). */
data class QuestionUiState(
    val actionType: String? = null,
    val selectedAnswer: String? = null,
    val isCorrect: Boolean? = null,
    val revealed: Boolean = false,
) {
    val graded: Boolean get() = actionType == AnswerActionType.SELECTED
    val untouched: Boolean get() = actionType == null
}

data class StudyUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val subjectName: String = "",
    val practiceType: String = "",
    val questions: List<QuestionEntity> = emptyList(),
    val currentIndex: Int = 0,
    val reciteMode: String = ReciteMode.TEST,
    val perQuestion: Map<Long, QuestionUiState> = emptyMap(),
    /** Multi-choice toggles pending confirmation, keyed by question id. */
    val multiTemp: Map<Long, Set<String>> = emptyMap(),
    val remainingMs: Long? = null,
    val sessionCompleted: Boolean = false,
)

class StudyViewModel(
    private val sessionId: Long,
    private val sessionRepository: StudySessionRepository,
    private val settingsRepository: SettingsRepository,
    private val db: RepsDatabase,
) : ViewModel() {

    private var session: StudySessionEntity? = null
    private var questionShownAt: Long = 0L
    private var ticker: Job? = null

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state

    val settings: Flow<UserSettings> = settingsRepository.settings

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = sessionRepository.getSession(sessionId)
        if (loaded == null) {
            _state.update { it.copy(loading = false, loadError = "会话不存在") }
            return
        }
        if (loaded.status != SessionStatus.ACTIVE) {
            _state.update { it.copy(loading = false, sessionCompleted = true) }
            return
        }
        session = loaded
        val questions = sessionRepository.getQuestions(loaded)
        val answers = sessionRepository.getAnswers(loaded.id).associateBy { it.questionId }
        val subjectName = sessionRepository.getSubjectName(loaded.subjectId).orEmpty()
        _state.update {
            it.copy(
                loading = false,
                subjectName = subjectName,
                practiceType = loaded.practiceType,
                questions = questions,
                currentIndex = loaded.currentIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0)),
                reciteMode = loaded.reciteMode,
                perQuestion = answers.mapValues { (_, a) ->
                    QuestionUiState(
                        actionType = a.actionType,
                        selectedAnswer = a.selectedAnswer,
                        isCorrect = a.isCorrect,
                        revealed = true,
                    )
                },
                remainingMs = if (loaded.deadlineAt > 0) (loaded.deadlineAt - System.currentTimeMillis()).coerceAtLeast(0) else null,
            )
        }
        // A timed session whose deadline passed while away: submit immediately.
        if (loaded.deadlineAt > 0 && System.currentTimeMillis() >= loaded.deadlineAt) {
            submit()
            return
        }
        startTicker()
        enterQuestion(_state.value.currentIndex)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val s = session ?: break
                if (s.deadlineAt <= 0) continue
                val remaining = s.deadlineAt - System.currentTimeMillis()
                if (remaining <= 0) {
                    _state.update { it.copy(remainingMs = 0) }
                    submit()
                    break
                }
                _state.update { it.copy(remainingMs = remaining) }
            }
        }
    }

    private fun enterQuestion(index: Int) {
        questionShownAt = System.currentTimeMillis()
        val s = session ?: return
        val question = _state.value.questions.getOrNull(index) ?: return
        if (s.reciteMode == ReciteMode.BROWSE) {
            markBrowsed(question)
        }
    }

    /** Swipe / button / answer-card navigation. Persists the position we leave. */
    fun onIndexChange(newIndex: Int) {
        val s = session ?: return
        val current = _state.value
        if (newIndex == current.currentIndex) return
        val oldQuestion = current.questions.getOrNull(current.currentIndex)
        val oldState = oldQuestion?.let { current.perQuestion[it.id] }
        viewModelScope.launch {
            sessionRepository.savePosition(
                session = s.copy(currentIndex = current.currentIndex),
                currentIndex = current.currentIndex,
                selectedAnswer = oldState?.takeIf { it.graded }?.selectedAnswer,
                answerRevealed = oldState?.revealed ?: false,
            )
        }
        _state.update { it.copy(currentIndex = newIndex) }
        enterQuestion(newIndex)
    }

    fun onOptionTap(question: QuestionEntity, value: String) {
        if (_state.value.reciteMode != ReciteMode.TEST) return
        when (question.type) {
            QuestionType.MULTI -> _state.update { current ->
                val temp = current.multiTemp[question.id].orEmpty()
                val next = if (value in temp) temp - value else temp + value
                current.copy(multiTemp = current.multiTemp + (question.id to next))
            }
            else -> grade(question, value)
        }
    }

    fun onConfirmMulti(question: QuestionEntity) {
        val raw = _state.value.multiTemp[question.id]?.sorted()?.joinToString(",") ?: return
        if (raw.isEmpty()) return
        grade(question, raw)
    }

    private fun grade(question: QuestionEntity, raw: String) {
        val s = session ?: return
        val current = _state.value
        if (current.perQuestion[question.id]?.graded == true) return
        val dwellMs = (System.currentTimeMillis() - questionShownAt).coerceAtLeast(0)
        viewModelScope.launch {
            val correct = sessionRepository.recordGradedAnswer(s, question, raw, dwellMs)
            val normalized = Grading.normalizeSelected(question, raw)
            _state.update { st ->
                st.copy(
                    perQuestion = st.perQuestion + (
                        question.id to QuestionUiState(
                            actionType = AnswerActionType.SELECTED,
                            selectedAnswer = normalized,
                            isCorrect = correct,
                            revealed = true,
                        )
                        ),
                    multiTemp = st.multiTemp - question.id,
                )
            }
            sessionRepository.savePosition(
                session = s.copy(currentIndex = current.currentIndex),
                currentIndex = current.currentIndex,
                selectedAnswer = normalized,
                answerRevealed = true,
            )
        }
    }

    private fun markBrowsed(question: QuestionEntity) {
        val s = session ?: return
        viewModelScope.launch {
            sessionRepository.recordBrowsed(s, question.id)
            _state.update { st ->
                val existing = st.perQuestion[question.id]
                if (existing?.actionType == AnswerActionType.SELECTED) {
                    st
                } else {
                    st.copy(
                        perQuestion = st.perQuestion + (
                            question.id to (existing ?: QuestionUiState()).copy(
                                actionType = AnswerActionType.BROWSED,
                                revealed = true,
                            )
                            ),
                    )
                }
            }
        }
    }

    fun onReciteModeChange(newMode: String) {
        val s = session ?: return
        val current = _state.value
        if (newMode == current.reciteMode) return
        val currentQuestion = current.questions.getOrNull(current.currentIndex)
        session = s.copy(reciteMode = newMode)
        viewModelScope.launch {
            val oldState = currentQuestion?.let { current.perQuestion[it.id] }
            sessionRepository.savePosition(
                session = session!!,
                currentIndex = current.currentIndex,
                selectedAnswer = oldState?.takeIf { it.graded }?.selectedAnswer,
                answerRevealed = oldState?.revealed ?: false,
                reciteMode = newMode,
            )
        }
        _state.update { st ->
            st.copy(
                reciteMode = newMode,
                perQuestion = currentQuestion?.let { q ->
                    val existing = st.perQuestion[q.id] ?: QuestionUiState()
                    when {
                        // Mode A: reveal everything for the visible question.
                        newMode == ReciteMode.BROWSE -> st.perQuestion + (
                            q.id to existing.copy(revealed = true)
                            )
                        // Mode B: keep graded picks; reset untouched questions.
                        existing.graded -> st.perQuestion
                        else -> st.perQuestion + (q.id to QuestionUiState())
                    }
                } ?: st.perQuestion,
            )
        }
        if (newMode == ReciteMode.BROWSE) {
            currentQuestion?.let { markBrowsed(it) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun submit() {
        val s = session ?: return
        val current = _state.value
        viewModelScope.launch {
            val oldState = current.questions.getOrNull(current.currentIndex)?.let { current.perQuestion[it.id] }
            sessionRepository.savePosition(
                session = s.copy(currentIndex = current.currentIndex),
                currentIndex = current.currentIndex,
                selectedAnswer = oldState?.takeIf { it.graded }?.selectedAnswer,
                answerRevealed = oldState?.revealed ?: false,
            )
            sessionRepository.markCompleted(s.id)
            _state.update { it.copy(sessionCompleted = true) }
        }
    }

    /** Switch practice type mid-session: starts a fresh session, old one completes. */
    suspend fun recreateSession(practiceType: String, shuffle: Boolean): Long? {
        val s = session ?: return null
        val baseIds = withContext(Dispatchers.IO) { db.questionDao().getIdsBySubject(s.subjectId) }
        return sessionRepository.createSession(
            subjectId = s.subjectId,
            practiceType = practiceType,
            reciteMode = s.reciteMode,
            baseQuestionIds = baseIds,
            shuffle = shuffle,
        )
    }

    companion object {
        fun create(sessionId: Long) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                StudyViewModel(
                    sessionId = sessionId,
                    sessionRepository = app.studySessionRepository,
                    settingsRepository = app.settingsRepository,
                    db = app.database,
                )
            }
        }
    }
}
