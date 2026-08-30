package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.coderirse.reps.data.db.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Insert
    suspend fun insert(subject: SubjectEntity): Long

    @Query("SELECT * FROM subjects ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int

    /** Cascades to questions, sessions, wrong-book, favorites and notes via FKs. */
    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteById(id: Long)
}
