package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.NoteEntity

@Dao
interface NoteDao {

    @Upsert
    suspend fun upsert(note: NoteEntity)

    /** Backup import: matched by questionId primary key. */
    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("SELECT * FROM notes WHERE questionId = :questionId")
    suspend fun getByQuestion(questionId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE questionId IN (:questionIds)")
    suspend fun getForIds(questionIds: List<Long>): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    /** Blank notes are removed instead of upserted as empty rows. */
    @Query("DELETE FROM notes WHERE questionId = :questionId")
    suspend fun delete(questionId: Long)
}
