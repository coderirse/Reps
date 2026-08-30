package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.WrongAnswerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WrongAnswerDao {

    @Upsert
    suspend fun upsert(wrongAnswer: WrongAnswerEntity)

    @Query("SELECT * FROM wrong_answers WHERE questionId = :questionId")
    suspend fun getByQuestion(questionId: Long): WrongAnswerEntity?

    @Query("SELECT * FROM wrong_answers WHERE mastered = 0 ORDER BY lastWrongAt DESC")
    fun observeUnmastered(): Flow<List<WrongAnswerEntity>>

    @Query("SELECT * FROM wrong_answers WHERE mastered = 1 ORDER BY lastWrongAt DESC")
    fun observeMastered(): Flow<List<WrongAnswerEntity>>

    @Query("UPDATE wrong_answers SET mastered = :mastered WHERE questionId = :questionId")
    suspend fun setMastered(questionId: Long, mastered: Boolean)
}
