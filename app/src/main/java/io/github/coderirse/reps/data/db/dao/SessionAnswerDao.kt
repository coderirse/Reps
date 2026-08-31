package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.SessionAnswerEntity
import kotlinx.coroutines.flow.Flow

/** Distinct questions a subject has been practiced on (across all sessions). */
data class SubjectPracticedCount(val subjectId: Long, val count: Int)

@Dao
interface SessionAnswerDao {

    @Upsert
    suspend fun upsert(answer: SessionAnswerEntity)

    @Query("SELECT * FROM session_answers")
    suspend fun getAll(): List<SessionAnswerEntity>

    @Upsert
    suspend fun upsertAll(answers: List<SessionAnswerEntity>)

    @Query("SELECT * FROM session_answers WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: Long): List<SessionAnswerEntity>

    /** Mode A -> B switch reads this to restore the previous pick, if any. */
    @Query("SELECT * FROM session_answers WHERE sessionId = :sessionId AND questionId = :questionId")
    suspend fun getForQuestion(sessionId: Long, questionId: Long): SessionAnswerEntity?

    @Query("DELETE FROM session_answers WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    /**
     * Per-subject count of distinct questions the user has touched, used by the
     * library cards for their "已练 x/y" progress. Emits as a Flow so a card
     * updates as soon as the session it belongs to records an answer.
     */
    @Query(
        "SELECT q.subjectId AS subjectId, COUNT(DISTINCT sa.questionId) AS count " +
            "FROM session_answers sa JOIN questions q ON q.id = sa.questionId " +
            "GROUP BY q.subjectId",
    )
    fun observePracticedCounts(): Flow<List<SubjectPracticedCount>>
}
