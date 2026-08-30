package io.github.coderirse.reps.data.repo

import io.github.coderirse.reps.core.CustomOrder
import io.github.coderirse.reps.core.CustomQuota
import io.github.coderirse.reps.core.Grading
import io.github.coderirse.reps.core.PaperBuilder
import io.github.coderirse.reps.core.Shuffle
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

    suspend fun getSession(id: Long): StudySessionEntity? =
        withContext(Dispatchers.IO) { db.studySessionDao().getById(id) }

    suspend fun getSubjectName(subjectId: Long): String? =
        withContext(Dispatchers.IO) { db.subjectDao().getById(subjectId)?.name }

    /** Questions in snapshot order (SQL IN is unordered; re-map here). */
    suspend fun getQuestions(session: StudySessionEntity): List<QuestionEntity> =
        withContext(Dispatchers.IO) {
            val ids = session.questionIds.split(',').mapNotNull { it.toLongOrNull() }
            val byId = db.questionDao().getByIds(ids).associateBy { it.id }
            ids.mapNotNull { byId[it] }
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
        db.sessionAnswerDao().upsert(
            SessionAnswerEntity(
                sessionId = session.id,
                questionId = question.id,
                actionType = AnswerActionType.SELECTED,
                selectedAnswer = normalized,
                isCorrect = correct,
                answeredAt = System.currentTimeMillis(),
                dwellMs = dwellMs,
            ),
        )
        correct
    }

    /** Mode A browse marker; never downgrades an existing SELECTED record. */
    suspend fun recordBrowsed(session: StudySessionEntity, questionId: Long) =
        withContext(Dispatchers.IO) {
            val existing = db.sessionAnswerDao().getForQuestion(session.id, questionId)
            if (existing == null || existing.actionType != AnswerActionType.SELECTED) {
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
            }
        }

    suspend fun savePosition(
        session: StudySessionEntity,
        currentIndex: Int,
        selectedAnswer: String?,
        answerRevealed: Boolean,
        reciteMode: String = session.reciteMode,
    ) = withContext(Dispatchers.IO) {
        db.studySessionDao().upsert(
            session.copy(
                currentIndex = currentIndex,
                selectedAnswer = selectedAnswer,
                answerRevealed = answerRevealed,
                reciteMode = reciteMode,
                lastActiveAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markCompleted(sessionId: Long) = withContext(Dispatchers.IO) {
        db.studySessionDao().markCompleted(sessionId)
    }
}
