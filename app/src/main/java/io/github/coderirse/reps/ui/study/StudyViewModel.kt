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
import io.github.coderirse.reps.data.db.entity.NoteEntity
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.ReciteMode
import io.github.coderirse.reps.data.db.entity.SessionStatus
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Any recorded pick, including unrevealed exam picks. */
    val answered: Boolean get() =
        actionType == AnswerActionType.SELECTED || actionType == AnswerActionType.EXAM_SELECTED
}

data class StudyUiState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
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
    /** 考试模式 (模拟考试/错题重练/收藏练习): 作答不揭示对错，交卷后统一出答案. */
    val examMode: Boolean = false,
    val favorites: Set<Long> = emptySet(),
    val notes: Map<Long, String> = emptyMap(),
)

class StudyViewModel(
    private val sessionId: Long,
    private val sessionRepository: StudySessionRepository,
    private val db: RepsDatabase,
    /** Survives ViewModel teardown; used for the ON_STOP fallback save. */
    private val externalScope: CoroutineScope,
) : ViewModel() {

    private var session: StudySessionEntity? = null
    private var questionShownAt: Long = 0L
    private var accumulatedMs: Long = 0L
    private var lastTickAt: Long = 0L
    private var ticker: Job? = null
    private var periodicSave: Job? = null

    /**
     * Questions with a grade write in flight. Checked and filled synchronously
     * on the main thread before the DB coroutine launches, so rapid double
     * taps cannot grade the same question twice (wrongCount would double).
     */
    private val gradingInFlight = mutableSetOf<Long>()

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = sessionRepository.getSession(sessionId)
        if (loaded == null) {
            _state.update { it.copy(loading = false, loadError = true) }
            return
        }
        if (loaded.status != SessionStatus.ACTIVE) {
            _state.update { it.copy(loading = false, sessionCompleted = true) }
            return
        }
        session = loaded
        // Legacy sessions restored with 直接看答案 mode keep their reveal behavior.
        val examMode = loaded.practiceType != PracticeType.RECITE && loaded.reciteMode == ReciteMode.TEST
        accumulatedMs = loaded.accumulatedMs
        lastTickAt = System.currentTimeMillis()
        val questions = sessionRepository.getQuestions(loaded)
        val answers = sessionRepository.getAnswers(loaded.id).associateBy { it.questionId }
        val subjectName = sessionRepository.getSubjectName(loaded.subjectId).orEmpty()
        val favoriteIds = db.favoriteDao().getFavoriteIds().toSet()
        // Chunked: a 1000+ question bank would exceed SQLite's host-parameter limit.
        val notes = questions.map { it.id }
            .chunked(500)
            .flatMap { db.noteDao().getForIds(it) }
            .associate { it.questionId to it.content }
        _state.update {
            it.copy(
                loading = false,
                subjectName = subjectName,
                practiceType = loaded.practiceType,
                questions = questions,
                currentIndex = loaded.currentIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0)),
                reciteMode = loaded.reciteMode,
                perQuestion = answers.mapValues { (_, a) ->
                    // Exam sessions never reveal, even for restored picks; the
                    // stored isCorrect stays hidden until the result page.
                    QuestionUiState(
                        actionType = a.actionType,
                        selectedAnswer = a.selectedAnswer,
                        isCorrect = if (examMode) null else a.isCorrect,
                        revealed = !examMode,
                    )
                },
                examMode = examMode,
                remainingMs = if (loaded.deadlineAt > 0) (loaded.deadlineAt - System.currentTimeMillis()).coerceAtLeast(0) else null,
                favorites = favoriteIds,
                notes = notes,
            )
        }
        // A timed session whose deadline passed while away: submit immediately.
        if (loaded.deadlineAt > 0 && System.currentTimeMillis() >= loaded.deadlineAt) {
            submit()
            return
        }
        startTicker()
        startPeriodicSave()
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

    /**
     * Periodic silent save (docs section 5.2): refreshes lastActiveAt, accrues
     * study time and flushes the current position every 5 seconds. Only runs
     * while the screen is RESUMED; ON_STOP cancels it via [saveNow] and
     * ON_RESUME restarts it via [onResume], so background time never accrues.
     */
    private fun startPeriodicSave() {
        periodicSave?.cancel()
        periodicSave = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val s = session ?: break
                val now = System.currentTimeMillis()
                val delta = now - lastTickAt
                lastTickAt = now
                accumulatedMs += delta
                persistCurrentState(accumulatedMs = accumulatedMs, scope = externalScope)
            }
        }
    }

    /** ON_STOP: final flush, then stop both loops so nothing ticks in background. */
    fun saveNow() {
        val s = session ?: return
        periodicSave?.cancel()
        periodicSave = null
        ticker?.cancel()
        ticker = null
        val now = System.currentTimeMillis()
        accumulatedMs += now - lastTickAt
        lastTickAt = now
        persistCurrentState(accumulatedMs = accumulatedMs, scope = externalScope)
    }

    /** ON_RESUME: restart accrual; a deadline passed while away submits at once. */
    fun onResume() {
        val s = session ?: return
        if (_state.value.sessionCompleted) return
        lastTickAt = System.currentTimeMillis()
        if (s.deadlineAt > 0 && System.currentTimeMillis() >= s.deadlineAt) {
            submit()
            return
        }
        startTicker()
        startPeriodicSave()
    }

    private fun persistCurrentState(accumulatedMs: Long, scope: CoroutineScope) {
        val s = session ?: return
        val current = _state.value
        val oldState = current.questions.getOrNull(current.currentIndex)?.let { current.perQuestion[it.id] }
        scope.launch {
            sessionRepository.savePosition(
                session = s,
                currentIndex = current.currentIndex,
                selectedAnswer = oldState?.takeIf { it.graded }?.selectedAnswer,
                answerRevealed = oldState?.revealed ?: false,
                accumulatedMs = accumulatedMs,
            )
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

    /** Swipe / button / answer-card navigation. */
    fun onIndexChange(newIndex: Int) {
        val s = session ?: return
        val current = _state.value
        if (newIndex == current.currentIndex) return
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
        if (current.examMode) {
            recordExamPick(question, raw)
            return
        }
        if (current.perQuestion[question.id]?.graded == true) return
        // Synchronous re-entry guard: the graded flag above only flips after
        // the DB write completes, so two fast taps would both pass it.
        if (!gradingInFlight.add(question.id)) return
        val dwellMs = (System.currentTimeMillis() - questionShownAt).coerceAtLeast(0)
        viewModelScope.launch {
            val correct = runCatching {
                sessionRepository.recordGradedAnswer(s, question, raw, dwellMs)
            }.getOrNull()
            if (correct == null) {
                gradingInFlight.remove(question.id)
                return@launch
            }
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
            gradingInFlight.remove(question.id)
            // 背题·答题子模式: 答对短暂停留后自动下一题，答错留下手动翻页。
            // VM 侧延迟并在跳前校验用户没有手动翻走，避免与 pager 动画竞争。
            if (correct && current.currentIndex < current.questions.size - 1) {
                val answeredIndex = current.currentIndex
                delay(600)
                if (_state.value.currentIndex == answeredIndex) {
                    onIndexChange(answeredIndex + 1)
                }
            }
        }
    }

    /** Exam pick: recorded without reveal; re-picking overwrites the answer. */
    private fun recordExamPick(question: QuestionEntity, raw: String) {
        val s = session ?: return
        if (!gradingInFlight.add(question.id)) return
        val dwellMs = (System.currentTimeMillis() - questionShownAt).coerceAtLeast(0)
        viewModelScope.launch {
            val ok = runCatching {
                sessionRepository.recordExamAnswer(s, question, raw, dwellMs)
            }.isSuccess
            if (ok) {
                val normalized = Grading.normalizeSelected(question, raw)
                _state.update { st ->
                    st.copy(
                        perQuestion = st.perQuestion + (
                            question.id to QuestionUiState(
                                actionType = AnswerActionType.EXAM_SELECTED,
                                selectedAnswer = normalized,
                                isCorrect = null,
                                revealed = false,
                            )
                            ),
                        multiTemp = st.multiTemp - question.id,
                    )
                }
            }
            gradingInFlight.remove(question.id)
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
        // The 背题/答题 toggle exists only inside recite sessions.
        if (s.practiceType != PracticeType.RECITE) return
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
                accumulatedMs = accumulatedMs,
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

    fun toggleFavorite(questionId: Long) {
        val current = _state.value
        val nowFavorite = questionId !in current.favorites
        // Optimistic per-branch update with rollback, so the heart reacts
        // instantly and a failed DB write doesn't desync UI and database.
        _state.update {
            it.copy(
                favorites = if (nowFavorite) it.favorites + questionId else it.favorites - questionId,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                if (nowFavorite) {
                    db.favoriteDao().add(
                        io.github.coderirse.reps.data.db.entity.FavoriteEntity(
                            questionId = questionId,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    db.favoriteDao().remove(questionId)
                }
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        favorites = if (nowFavorite) it.favorites - questionId else it.favorites + questionId,
                    )
                }
            }
        }
    }

    fun saveNote(questionId: Long, content: String) {
        _state.update { st ->
            val notes = if (content.isBlank()) st.notes - questionId else st.notes + (questionId to content)
            st.copy(notes = notes)
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (content.isBlank()) {
                // Delete instead of upserting an empty row: empty note rows
                // would keep the note icon highlighted forever.
                db.noteDao().delete(questionId)
            } else {
                db.noteDao().upsert(NoteEntity(questionId = questionId, content = content, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun submit() {
        val s = session ?: return
        // Check-and-set synchronously: the deadline ticker and the submit
        // button can both fire before the coroutine below runs.
        if (_state.value.sessionCompleted) return
        _state.update { it.copy(sessionCompleted = true) }
        viewModelScope.launch {
            persistCurrentStateSync()
            // gradeExamSession only touches EXAM_SELECTED rows, so recite and
            // legacy sessions pass through it untouched (idempotent).
            sessionRepository.gradeExamSession(s.id)
        }
    }

    private suspend fun persistCurrentStateSync() {
        val s = session ?: return
        val current = _state.value
        val now = System.currentTimeMillis()
        accumulatedMs += now - lastTickAt
        lastTickAt = now
        val oldState = current.questions.getOrNull(current.currentIndex)?.let { current.perQuestion[it.id] }
        withContext(Dispatchers.IO) {
            sessionRepository.savePosition(
                session = s,
                currentIndex = current.currentIndex,
                selectedAnswer = oldState?.takeIf { it.graded }?.selectedAnswer,
                answerRevealed = oldState?.revealed ?: false,
                accumulatedMs = accumulatedMs,
            )
        }
    }

    companion object {
        fun create(sessionId: Long) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                StudyViewModel(
                    sessionId = sessionId,
                    sessionRepository = app.studySessionRepository,
                    db = app.database,
                    externalScope = app.applicationScope,
                )
            }
        }
    }
}
