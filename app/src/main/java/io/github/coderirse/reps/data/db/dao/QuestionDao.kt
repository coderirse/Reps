package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Insert
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Query("SELECT * FROM questions")
    suspend fun getAll(): List<QuestionEntity>

    /** Backup import: matched by primary key id. */
    @Upsert
    suspend fun upsertAll(questions: List<QuestionEntity>)

    @Query("SELECT * FROM questions WHERE subjectId = :subjectId ORDER BY orderIndex ASC")
    fun observeBySubject(subjectId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE subjectId = :subjectId ORDER BY orderIndex ASC")
    suspend fun getForSubject(subjectId: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: Long): QuestionEntity?

    /** Preserves snapshot order on the Kotlin side; SQL IN has no order guarantee. */
    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<QuestionEntity>

    @Query("SELECT id FROM questions WHERE subjectId = :subjectId AND type = :type ORDER BY orderIndex ASC")
    suspend fun getIdsByType(subjectId: Long, type: String): List<Long>

    @Query("SELECT id FROM questions WHERE subjectId = :subjectId ORDER BY orderIndex ASC")
    suspend fun getIdsBySubject(subjectId: Long): List<Long>

    @Query("SELECT id FROM questions WHERE subjectId = :subjectId AND chapter = :chapter ORDER BY orderIndex ASC")
    suspend fun getIdsByChapter(subjectId: Long, chapter: String): List<Long>

    @Query("SELECT id FROM questions WHERE subjectId = :subjectId AND category = :category ORDER BY orderIndex ASC")
    suspend fun getIdsByCategory(subjectId: Long, category: String): List<Long>

    @Query("SELECT chapter AS value, COUNT(*) AS count FROM questions WHERE subjectId = :subjectId AND chapter IS NOT NULL AND chapter != '' GROUP BY chapter ORDER BY chapter")
    suspend fun getChapterCounts(subjectId: Long): List<GroupCount>

    @Query("SELECT category AS value, COUNT(*) AS count FROM questions WHERE subjectId = :subjectId AND category IS NOT NULL AND category != '' GROUP BY category ORDER BY category")
    suspend fun getCategoryCounts(subjectId: Long): List<GroupCount>

    @Query("SELECT COUNT(*) FROM questions WHERE subjectId = :subjectId AND type = :type")
    suspend fun countByType(subjectId: Long, type: String): Int

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

data class GroupCount(val value: String, val count: Int)
