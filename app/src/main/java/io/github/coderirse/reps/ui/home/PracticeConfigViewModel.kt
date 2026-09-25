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
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.ReciteMode
import io.github.coderirse.reps.data.prefs.SettingsRepository
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 抽题题池（仅背题/模拟考试可选；错题重练/收藏练习的池子固定）。 */
enum class PoolChoice { ALL, UNPRACTICED, WRONG, FAVORITE }

data class PracticeConfigUiState(
    val loading: Boolean = true,
    val subjectName: String = "",
    val singleMax: Int = 0,
    val multiMax: Int = 0,
    val judgeMax: Int = 0,
    val single: Int = 0,
    val multi: Int = 0,
    val judge: Int = 0,
    val timed: Boolean = false,
    val minutes: Int = 60,
    val order: CustomOrder = CustomOrder.SEQUENTIAL,
    val starting: Boolean = false,
    val pool: PoolChoice = PoolChoice.ALL,
    /** 背题/模拟考试可选题池；错题重练/收藏练习的池子由入口决定。 */
    val poolSelectable: Boolean = false,
) {
    val total: Int get() = single + multi + judge
    val poolTotal: Int get() = singleMax + multiMax + judgeMax
}

/** Last-used config persisted per (subject, mode); quotas re-coerced to the pool on load. */
@Serializable
private data class SavedPracticeConfig(
    val single: Int,
    val multi: Int,
    val judge: Int,
    val timed: Boolean,
    val minutes: Int,
    val order: String,
    val pool: String? = null,
)

/**
 * Secondary config page behind every practice-mode entry (背题/模拟考试/错题重练/
 * 收藏练习). Pool: the whole subject for 背题/模拟考试, the unmastered wrong book
 * or the favorites for the other two. The last confirmed config is remembered
 * per (subject, mode) so frequent users don't rebuild quotas every time.
 */
class PracticeConfigViewModel(
    private val subjectId: Long,
    val practiceType: String,
    private val db: RepsDatabase,
    private val sessionRepository: StudySessionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private var poolIds: List<Long>? = null

    /** Serializes pool loads: rapid pool switches would race two IO queries. */
    private var poolJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(PracticeConfigUiState())
    val state: StateFlow<PracticeConfigUiState> = _state

    private val configKey = "cfg_${subjectId}_$practiceType"

    init {
        poolJob = viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { loadSavedConfig() }
            // The pool choice must be applied before loadPool queries counts.
            saved?.pool?.let { savedPool ->
                runCatching { PoolChoice.valueOf(savedPool) }.getOrNull()?.let { pool ->
                    _state.update { it.copy(pool = pool) }
                }
            }
            loadPool(applySaved = saved)
        }
    }

    fun setPool(pool: PoolChoice) {
        if (pool == _state.value.pool) return
        _state.update { it.copy(pool = pool, loading = true) }
        poolJob?.cancel()
        poolJob = viewModelScope.launch { loadPool() }
    }

    private suspend fun loadSavedConfig(): SavedPracticeConfig? =
        settingsRepository.practiceConfig(configKey)
            ?.let { runCatching { json.decodeFromString(SavedPracticeConfig.serializer(), it) }.getOrNull() }

    private suspend fun loadPool(applySaved: SavedPracticeConfig? = null) {
        val (name, counts) = withContext(Dispatchers.IO) {
            val subjectName = db.subjectDao().getById(subjectId)?.name.orEmpty()
            val counts = when (practiceType) {
                PracticeType.WRONG_BOOK -> {
                    poolIds = db.wrongAnswerDao().getUnmasteredIdsForSubject(subjectId)
                    countsOf(poolIds.orEmpty())
                }
                PracticeType.FAVORITE -> {
                    poolIds = db.favoriteDao().getFavoriteIdsForSubject(subjectId)
                    countsOf(poolIds.orEmpty())
                }
                else -> when (_state.value.pool) {
                    PoolChoice.ALL -> {
                        poolIds = null
                        mapOf(
                            QuestionType.SINGLE to db.questionDao().countByType(subjectId, QuestionType.SINGLE),
                            QuestionType.MULTI to db.questionDao().countByType(subjectId, QuestionType.MULTI),
                            QuestionType.JUDGE to db.questionDao().countByType(subjectId, QuestionType.JUDGE),
                        )
                    }
                    PoolChoice.UNPRACTICED -> {
                        val practiced = db.sessionAnswerDao().getPracticedIdsForSubject(subjectId).toSet()
                        poolIds = db.questionDao().getIdsBySubject(subjectId).filter { it !in practiced }
                        countsOf(poolIds.orEmpty())
                    }
                    PoolChoice.WRONG -> {
                        poolIds = db.wrongAnswerDao().getUnmasteredIdsForSubject(subjectId)
                        countsOf(poolIds.orEmpty())
                    }
                    PoolChoice.FAVORITE -> {
                        poolIds = db.favoriteDao().getFavoriteIdsForSubject(subjectId)
                        countsOf(poolIds.orEmpty())
                    }
                }
            }
            subjectName to counts
        }
        val singleMax = counts[QuestionType.SINGLE] ?: 0
        val multiMax = counts[QuestionType.MULTI] ?: 0
        val judgeMax = counts[QuestionType.JUDGE] ?: 0
        // 模拟考试默认小试卷: capped quotas, timer on, random order. Everything
        // else practices the whole pool. A saved config overrides the defaults
        // (re-coerced into what the current pool can actually provide).
        val defaultSingle = if (practiceType == PracticeType.EXAM) minOf(40, singleMax) else singleMax
        val defaultMulti = if (practiceType == PracticeType.EXAM) minOf(10, multiMax) else multiMax
        val defaultJudge = if (practiceType == PracticeType.EXAM) minOf(10, judgeMax) else judgeMax
        val savedSingle = applySaved?.single?.coerceIn(0, singleMax) ?: defaultSingle
        val savedMulti = applySaved?.multi?.coerceIn(0, multiMax) ?: defaultMulti
        val savedJudge = applySaved?.judge?.coerceIn(0, judgeMax) ?: defaultJudge
        _state.update {
            it.copy(
                loading = false,
                subjectName = name,
                singleMax = singleMax, multiMax = multiMax, judgeMax = judgeMax,
                single = savedSingle, multi = savedMulti, judge = savedJudge,
                timed = applySaved?.timed ?: (practiceType == PracticeType.EXAM),
                minutes = applySaved?.minutes?.coerceIn(5, 240) ?: 60,
                order = applySaved
                    ?.let { c -> runCatching { CustomOrder.valueOf(c.order) }.getOrNull() }
                    ?: (if (practiceType == PracticeType.EXAM) CustomOrder.RANDOM else CustomOrder.SEQUENTIAL),
                poolSelectable = practiceType == PracticeType.RECITE || practiceType == PracticeType.EXAM,
            )
        }
    }

    private suspend fun countsOf(ids: List<Long>): Map<String, Int> =
        sessionRepository.getQuestionsByIds(ids)
            .groupingBy { it.type }
            .eachCount()

    fun setQuota(type: String, value: Int) = _state.update {
        when (type) {
            QuestionType.SINGLE -> it.copy(single = value.coerceIn(0, it.singleMax))
            QuestionType.MULTI -> it.copy(multi = value.coerceIn(0, it.multiMax))
            else -> it.copy(judge = value.coerceIn(0, it.judgeMax))
        }
    }

    fun setTimed(timed: Boolean) = _state.update { it.copy(timed = timed) }
    fun setMinutes(minutes: Int) = _state.update { it.copy(minutes = minutes.coerceIn(5, 240)) }
    fun setOrder(order: CustomOrder) = _state.update { it.copy(order = order) }

    /** @return new session id, or null when the pool/quota is empty or creation failed. */
    suspend fun start(): Long? {
        val current = _state.value
        if (current.starting || current.total <= 0) return null
        _state.update { it.copy(starting = true) }
        return try {
            val sessionId = runCatching {
                sessionRepository.createConfiguredSession(
                    subjectId = subjectId,
                    practiceType = practiceType,
                    quota = CustomQuota(
                        single = current.single,
                        multi = current.multi,
                        judge = current.judge,
                    ),
                    order = current.order,
                    // 背题默认进「答题」子模式（先作答后看答案），页内可切到「看答案」；
                    // 其余模式一律考试式：作答不揭示，交卷后才出答案。
                    reciteMode = ReciteMode.TEST,
                    deadlineMinutes = if (current.timed) current.minutes else 0,
                    poolIds = poolIds,
                )
            }.getOrNull()
            // Only a confirmed start persists the config, so a failed attempt
            // never overwrites the last known-good setup.
            if (sessionId != null) saveConfig(current)
            sessionId
        } finally {
            _state.update { it.copy(starting = false) }
        }
    }

    private suspend fun saveConfig(current: PracticeConfigUiState) {
        val saved = SavedPracticeConfig(
            single = current.single,
            multi = current.multi,
            judge = current.judge,
            timed = current.timed,
            minutes = current.minutes,
            order = current.order.name,
            pool = if (current.poolSelectable) current.pool.name else null,
        )
        runCatching {
            settingsRepository.setPracticeConfig(configKey, json.encodeToString(SavedPracticeConfig.serializer(), saved))
        }
    }

    companion object {
        fun create(subjectId: Long, practiceType: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                PracticeConfigViewModel(
                    subjectId = subjectId,
                    practiceType = practiceType,
                    db = app.database,
                    sessionRepository = app.studySessionRepository,
                    settingsRepository = app.settingsRepository,
                )
            }
        }
    }
}
