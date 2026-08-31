package io.github.coderirse.reps.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Globally persisted wrong-book entry; survives across sessions. */
@Entity(
    tableName = "wrong_answers",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mastered")],
)
@Serializable
data class WrongAnswerEntity(
    @PrimaryKey val questionId: Long,
    val wrongCount: Int,
    /** Correct streak inside wrong-book practice; >= 2 auto-masters. */
    val reviewCorrectCount: Int,
    val lastWrongAt: Long,
    val mastered: Boolean = false,
)
