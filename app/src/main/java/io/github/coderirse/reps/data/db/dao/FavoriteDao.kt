package io.github.coderirse.reps.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import io.github.coderirse.reps.data.db.entity.FavoriteEntity
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

data class FavoriteRow(
    @Embedded val question: QuestionEntity,
    val createdAt: Long,
)

@Dao
interface FavoriteDao {

    @Upsert
    suspend fun add(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>

    /** Backup import: matched by questionId primary key. */
    @Upsert
    suspend fun upsertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE questionId = :questionId")
    suspend fun remove(questionId: Long)

    @Query("SELECT questionId FROM favorites ORDER BY createdAt DESC")
    suspend fun getFavoriteIds(): List<Long>

    @Query(
        "SELECT f.questionId FROM favorites f JOIN questions q ON q.id = f.questionId " +
            "WHERE q.subjectId = :subjectId ORDER BY f.createdAt DESC",
    )
    suspend fun getFavoriteIdsForSubject(subjectId: Long): List<Long>

    @Query("SELECT DISTINCT q.subjectId FROM favorites f JOIN questions q ON q.id = f.questionId")
    fun observeSubjectIds(): Flow<List<Long>>

    @Query(
        "SELECT q.*, f.createdAt AS createdAt FROM favorites f " +
            "JOIN questions q ON q.id = f.questionId " +
            "WHERE (:subjectId IS NULL OR q.subjectId = :subjectId) " +
            "ORDER BY f.createdAt DESC",
    )
    fun observeRows(subjectId: Long?): Flow<List<FavoriteRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE questionId = :questionId)")
    fun observeIsFavorite(questionId: Long): Flow<Boolean>
}
