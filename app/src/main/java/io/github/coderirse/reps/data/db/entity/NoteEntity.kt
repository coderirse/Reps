package io.github.coderirse.reps.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Free-text note attached to a question (Mode B feature, P1). */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
@Serializable
data class NoteEntity(
    @PrimaryKey val questionId: Long,
    val content: String,
    val updatedAt: Long,
)
