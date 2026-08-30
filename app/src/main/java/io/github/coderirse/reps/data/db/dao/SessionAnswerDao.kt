package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.SessionAnswerEntity

@Dao
interface SessionAnswerDao {

    @Upsert
    suspend fun upsert(answer: SessionAnswerEntity)

    @Query("SELECT * FROM session_answers WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: Long): List<SessionAnswerEntity>

    /** Mode A -> B switch reads this to restore the previous pick, if any. */
    @Query("SELECT * FROM session_answers WHERE sessionId = :sessionId AND questionId = :questionId")
    suspend fun getForQuestion(sessionId: Long, questionId: Long): SessionAnswerEntity?

    @Query("DELETE FROM session_answers WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
