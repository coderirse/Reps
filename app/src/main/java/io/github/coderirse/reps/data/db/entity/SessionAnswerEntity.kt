package io.github.coderirse.reps.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object AnswerActionType {
    /** Mode B: user picked an answer and got graded. */
    const val SELECTED = "selected"

    /** Mode A: user browsed the question with answer revealed. */
    const val BROWSED = "browsed"
}

/**
 * One row per (session, question): the per-question state inside a session.
 * Aggregating this table yields the answer-card states; Mode A/B switch
 * restores from here.
 */
@Entity(
    tableName = "session_answers",
    foreignKeys = [
        ForeignKey(
            entity = StudySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("questionId"), Index(value = ["sessionId", "questionId"], unique = true)],
)
data class SessionAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val questionId: Long,
    val actionType: String,
    val selectedAnswer: String?,
    /** Null for BROWSED records. */
    val isCorrect: Boolean?,
    val answeredAt: Long,
    val dwellMs: Long?,
)
