package io.github.coderirse.reps.data.repo

import androidx.room.withTransaction
import io.github.coderirse.reps.core.CustomOrder
import io.github.coderirse.reps.core.CustomQuota
import io.github.coderirse.reps.core.Grading
import io.github.coderirse.reps.core.PaperBuilder
import io.github.coderirse.reps.core.Shuffle
import io.github.coderirse.reps.core.WrongBookRules
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.dao.SessionAnswerDao
import io.github.coderirse.reps.data.db.entity.AnswerActionType
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.SessionAnswerEntity
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates and advances study sessions. Question order is snapshotted at
 * creation (shuffled with a persisted seed where applicable) so the queue
 * survives process death and mid-session wrong-book changes.
 */
class StudySessionRepository(private val db: RepsDatabase) {

    suspend fun createSession(
        subjectId: Long,
        practiceType: String,
        filterValue: String? = null,
        reciteMode: String,
        baseQuestionIds: List<Long>,
        shuffle: Boolean = false,
        deadlineMinutes: Int = 0,
    ): Long = withContext(Dispatchers.IO) {
        check(baseQuestionIds.isNotEmpty()) { "No questions to practice" }
        val seed = System.nanoTime()
        val orderedIds = if (shuffle) Shuffle.shuffled(baseQuestionIds, seed) else baseQuestionIds
        // Complete-then-insert must be atomic: a failure in between would leave
        // the old session completed with no new one, and concurrent callers
        // could both pass the check and create two ACTIVE sessions.
        db.withTransaction {
            db.studySessionDao().completeActiveForSubject(subjectId)
            db.studySessionDao().upsert(
                StudySessionEntity(
                    subjectId = subjectId,
                    practiceType = practiceType,
                    filterValue = filterValue,
                    reciteMode = reciteMode,
                    questionIds = orderedIds.joinToString(","),
                    currentIndex = 0,
                    selectedAnswer = null,
                    answerRevealed = false,
                    randomSeed = seed,
                    deadlineAt = if (deadlineMinutes > 0) {
                        System.currentTimeMillis() + deadlineMinutes * 60_000L
                    } else {
                        0L
                    },
                    startedAt = System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis(),
                    accumulatedMs = 0,
                    status = io.github.coderirse.reps.data.db.entity.SessionStatus.ACTIVE,
                ),
            )
        }
    }

    /** CUSTOM paper: per-type quotas, sequential take or seeded sampling. */
    suspend fun createCustomSession(
        subjectId: Long,
        quota: CustomQuota,
        order: CustomOrder,
        reciteMode: String,
        deadlineMinutes: Int,
    ): Long = withContext(Dispatchers.IO) {
        val pools = mapOf(
            QuestionType.SINGLE to db.questionDao().getIdsByType(subjectId, QuestionType.SINGLE),
            QuestionType.MULTI to db.questionDao().getIdsByType(subjectId, QuestionType.MULTI),
            QuestionType.JUDGE to db.questionDao().getIdsByType(subjectId, QuestionType.JUDGE),
        )
        val seed = System.nanoTime()
        val pickedIds = PaperBuilder.pickIds(pools, quota, order, seed)
        return@withContext db.withTransaction {
            db.studySessionDao().completeActiveForSubject(subjectId)
            db.studySessionDao().upsert(
                StudySessionEntity(
                    subjectId = subjectId,
                    practiceType = io.github.coderirse.reps.data.db.entity.PracticeType.CUSTOM,
                    filterValue = null,
                    reciteMode = reciteMode,
                    questionIds = pickedIds.joinToString(","),
                    currentIndex = 0,
                    selectedAnswer = null,
                    answerRevealed = false,
                    randomSeed = seed,
                    deadlineAt = if (deadlineMinutes > 0) {
                        System.currentTimeMillis() + deadlineMinutes * 60_000L
                    } else {
                        0L
                    },
                    startedAt = System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis(),
                    accumulatedMs = 0,
                    status = io.github.coderirse.reps.data.db.entity.SessionStatus.ACTIVE,
                ),
            )
        }
    }

    suspend fun getSession(id: Long): StudySessionEntity? =
        withContext(Dispatchers.IO) { db.studySessionDao().getById(id) }

    suspend fun getSubjectName(subjectId: Long): String? =
        withContext(Dispatchers.IO) { db.subjectDao().getById(subjectId)?.name }

    /** Questions in snapshot order (SQL IN is unordered; re-map here). */
    suspend fun getQuestions(session: StudySessionEntity): List<QuestionEntity> =
        withContext(Dispatchers.IO) {
            val ids = session.questionIds.split(',').mapNotNull { it.toLongOrNull() }
            val byId = getQuestionsByIds(ids).associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }

    /**
     * Chunked id lookup: SQLite's host-parameter limit is 999 on older
     * Android (minSdk 26), so a 1000+ question bank would crash a plain
     * `WHERE id IN (:ids)`. Batches of 500 stay safely under the limit.
     */
    suspend fun getQuestionsByIds(ids: List<Long>): List<QuestionEntity> =
        withContext(Dispatchers.IO) {
            ids.chunked(SQL_IN_CHUNK).flatMap { chunk -> db.questionDao().getByIds(chunk) }
        }

    suspend fun getAnswers(sessionId: Long): List<SessionAnswerEntity> =
        withContext(Dispatchers.IO) { db.sessionAnswerDao().getBySession(sessionId) }

    suspend fun recordGradedAnswer(
        session: StudySessionEntity,
        question: QuestionEntity,
        selectedRaw: String,
        dwellMs: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val normalized = Grading.normalizeSelected(question, selectedRaw)
        val correct = Grading.isCorrect(question, selectedRaw)
        val now = System.currentTimeMillis()
        db.withTransaction {
            // @Upsert falls back to UPDATE by primary key on conflict; our rows
            // always insert with id = 0, so a conflicting (sessionId, questionId)
            // row would make the update match nothing and silently drop the
            // answer. Pre-read and carry the existing id instead.
            val existing = db.sessionAnswerDao().getForQuestion(session.id, question.id)
            db.sessionAnswerDao().upsert(
                SessionAnswerEntity(
                    id = existing?.id ?: 0,
                    sessionId = session.id,
                    questionId = question.id,
                    actionType = AnswerActionType.SELECTED,
                    selectedAnswer = normalized,
                    isCorrect = correct,
                    answeredAt = now,
                    dwellMs = dwellMs,
                ),
            )
            // Wrong-book linkage in the same transaction (docs section 5.3).
            val updated = WrongBookRules.onAnswered(
                questionId = question.id,
                current = db.wrongAnswerDao().getByQuestion(question.id),
                correct = correct,
                now = now,
            )
            updated?.let { db.wrongAnswerDao().upsert(it) }
        }
        correct
    }

    /** Mode A browse marker; never downgrades an existing SELECTED record. */
    suspend fun recordBrowsed(session: StudySessionEntity, questionId: Long) =
        withContext(Dispatchers.IO) {
            val existing = db.sessionAnswerDao().getForQuestion(session.id, questionId)
            if (existing == null) {
                db.sessionAnswerDao().upsert(
                    SessionAnswerEntity(
                        sessionId = session.id,
                        questionId = questionId,
                        actionType = AnswerActionType.BROWSED,
                        selectedAnswer = null,
                        isCorrect = null,
                        answeredAt = System.currentTimeMillis(),
                        dwellMs = null,
                    ),
                )
            } else if (existing.actionType != AnswerActionType.SELECTED) {
                db.sessionAnswerDao().upsert(
                    existing.copy(answeredAt = System.currentTimeMillis()),
                )
            }
        }

    suspend fun savePosition(
        session: StudySessionEntity,
        currentIndex: Int,
        selectedAnswer: String?,
        answerRevealed: Boolean,
        reciteMode: String = session.reciteMode,
        accumulatedMs: Long? = null,
    ) = withContext(Dispatchers.IO) {
        db.studySessionDao().upsert(
            session.copy(
                currentIndex = currentIndex,
                selectedAnswer = selectedAnswer,
                answerRevealed = answerRevealed,
                reciteMode = reciteMode,
                accumulatedMs = accumulatedMs ?: session.accumulatedMs,
                lastActiveAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markCompleted(sessionId: Long) = withContext(Dispatchers.IO) {
        db.studySessionDao().markCompleted(sessionId)
    }

    /** 重新开始: same paper, cleared answers, fresh timers. */
    suspend fun restartSession(sessionId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.sessionAnswerDao().deleteForSession(sessionId)
            db.studySessionDao().resetForRestart(sessionId, now)
        }
    }

    companion object {
        private const val SQL_IN_CHUNK = 500
    }
}
