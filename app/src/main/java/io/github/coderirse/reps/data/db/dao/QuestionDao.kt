package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Insert
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Query("SELECT * FROM questions WHERE subjectId = :subjectId ORDER BY orderIndex ASC")
    fun observeBySubject(subjectId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: Long): QuestionEntity?

    @Query("SELECT COUNT(*) FROM questions WHERE subjectId = :subjectId")
    suspend fun countBySubject(subjectId: Long): Int

    @Query("DELETE FROM questions WHERE subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: Long)

    /** Import everything for one subject atomically. */
    @Transaction
    suspend fun importForSubject(questions: List<QuestionEntity>) {
        insertAll(questions)
    }
}
