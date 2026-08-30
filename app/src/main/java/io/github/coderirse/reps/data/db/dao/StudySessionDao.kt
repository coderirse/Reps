package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {

    @Upsert
    suspend fun upsert(session: StudySessionEntity): Long

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    suspend fun getById(id: Long): StudySessionEntity?

    @Query("SELECT * FROM study_sessions WHERE status = 0 ORDER BY lastActiveAt DESC")
    fun observeActive(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE subjectId = :subjectId AND status = 0 LIMIT 1")
    suspend fun getActiveBySubject(subjectId: Long): StudySessionEntity?

    /** One active session per subject: completing the previous one on entry. */
    @Query("UPDATE study_sessions SET status = 1 WHERE subjectId = :subjectId AND status = 0")
    suspend fun completeActiveForSubject(subjectId: Long)

    @Query("UPDATE study_sessions SET status = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    /** 重新开始: same paper, fresh state. */
    @Query(
        "UPDATE study_sessions SET currentIndex = 0, selectedAnswer = NULL, answerRevealed = 0, " +
            "status = 0, startedAt = :now, lastActiveAt = :now, accumulatedMs = 0 WHERE id = :id",
    )
    suspend fun resetForRestart(id: Long, now: Long)

    /** Past the 7-day restore window -> mark EXPIRED so it never resurfaces. */
    @Query("UPDATE study_sessions SET status = 2 WHERE status = 0 AND lastActiveAt < :cutoffEpochMs")
    suspend fun expireInactiveBefore(cutoffEpochMs: Long)
}
