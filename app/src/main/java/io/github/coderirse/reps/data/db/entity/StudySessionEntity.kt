package io.github.coderirse.reps.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object PracticeType {
    const val SEQUENTIAL = "sequential"
    const val RANDOM = "random"
    const val CATEGORY = "category"
    const val WRONG_BOOK = "wrong_book"
    const val FAVORITE = "favorite"
}

object ReciteMode {
    /** Mode A: answer + explanation shown on load, browse only. */
    const val BROWSE = "mode_a_browse"

    /** Mode B: answer hidden until the user picks an option. */
    const val TEST = "mode_b_test"
}

object SessionStatus {
    const val ACTIVE = 0
    const val COMPLETED = 1
    const val EXPIRED = 2
}

@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("subjectId"), Index("status")],
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val practiceType: String,
    /** Filter value for CATEGORY practice (chapter or category). */
    val filterValue: String?,
    val reciteMode: String,
    /**
     * Snapshot of question ids for this session, ordered as the session queue
     * (shuffled for RANDOM at creation time using [randomSeed]). JSON array.
     * Snapshotting keeps the queue stable even if the wrong-book/favorites
     * change mid-session.
     */
    val questionIds: String,
    /** Index into [questionIds]. */
    val currentIndex: Int,
    val selectedAnswer: String?,
    val answerRevealed: Boolean,
    val randomSeed: Long,
    val startedAt: Long,
    val lastActiveAt: Long,
    val accumulatedMs: Long,
    val status: Int,
)
