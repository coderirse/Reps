package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.NoteEntity

@Dao
interface NoteDao {

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE questionId = :questionId")
    suspend fun getByQuestion(questionId: Long): NoteEntity?
}
