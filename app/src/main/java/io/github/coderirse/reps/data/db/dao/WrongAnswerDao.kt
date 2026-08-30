package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.WrongAnswerEntity
import kotlinx.coroutines.flow.Flow

data class WrongBookRow(
    @Embedded val question: QuestionEntity,
    val wrongCount: Int,
    val reviewCorrectCount: Int,
    val lastWrongAt: Long,
    val mastered: Boolean,
)

@Dao
interface WrongAnswerDao {

    @Upsert
    suspend fun upsert(wrongAnswer: WrongAnswerEntity)

    @Query("SELECT * FROM wrong_answers WHERE questionId = :questionId")
    suspend fun getByQuestion(questionId: Long): WrongAnswerEntity?

    @Query("SELECT questionId FROM wrong_answers WHERE mastered = 0 ORDER BY lastWrongAt DESC")
    suspend fun getUnmasteredIds(): List<Long>

    @Query(
        "SELECT w.questionId FROM wrong_answers w JOIN questions q ON q.id = w.questionId " +
            "WHERE w.mastered = 0 AND q.subjectId = :subjectId ORDER BY w.lastWrongAt DESC",
    )
    suspend fun getUnmasteredIdsForSubject(subjectId: Long): List<Long>

    @Query(
        "SELECT q.*, w.wrongCount AS wrongCount, w.reviewCorrectCount AS reviewCorrectCount, " +
            "w.lastWrongAt AS lastWrongAt, w.mastered AS mastered " +
            "FROM wrong_answers w JOIN questions q ON q.id = w.questionId " +
            "WHERE w.mastered = 0 ORDER BY w.lastWrongAt DESC",
    )
    fun observeUnmasteredRows(): Flow<List<WrongBookRow>>

    @Query(
        "SELECT q.*, w.wrongCount AS wrongCount, w.reviewCorrectCount AS reviewCorrectCount, " +
            "w.lastWrongAt AS lastWrongAt, w.mastered AS mastered " +
            "FROM wrong_answers w JOIN questions q ON q.id = w.questionId " +
            "WHERE w.mastered = 1 ORDER BY w.lastWrongAt DESC",
    )
    fun observeMasteredRows(): Flow<List<WrongBookRow>>

    @Query("UPDATE wrong_answers SET mastered = :mastered WHERE questionId = :questionId")
    suspend fun setMastered(questionId: Long, mastered: Boolean)
}
