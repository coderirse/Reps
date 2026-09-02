package io.github.coderirse.reps.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object PracticeType {
    /** 背题模式: two sub-modes (直接看答案 / 先作答) switchable mid-session. */
    const val RECITE = "recite"

    /** 模拟考试: answers and scores are revealed only after submitting. */
    const val EXAM = "exam"
    const val WRONG_BOOK = "wrong_book"
    const val FAVORITE = "favorite"

    // Legacy types kept so sessions recorded by older versions still restore
    // and display; the picker no longer creates them.
    const val SEQUENTIAL = "sequential"
    const val RANDOM = "random"
    const val CATEGORY = "category"
    const val CUSTOM = "custom"
}

object ReciteMode {
    /** 背题·直接看答案: answer + explanation shown on load, browse only. */
    const val BROWSE = "mode_a_browse"

    /** 背题·先作答: answer hidden until the user picks an option. */
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
@Serializable
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val practiceType: String,
    /** Filter value for CATEGORY practice (chapter or category). */
    val filterValue: String?,
    val reciteMode: String,
    /**
     * Snapshot of question ids for this session, ordered as the session queue
     * (shuffled for RANDOM at creation time using [randomSeed]). Stored as a
     * comma-separated id string ("1,2,3") — NOT a JSON array. Snapshotting
     * keeps the queue stable even if the wrong-book/favorites change
     * mid-session.
     */
    val questionIds: String,
    /** Index into [questionIds]. */
    val currentIndex: Int,
    val selectedAnswer: String?,
    val answerRevealed: Boolean,
    val randomSeed: Long,
    /**
     * Countdown deadline (epoch ms) for timed CUSTOM sessions; 0 = untimed.
     * Persisted as wall clock so resuming a session continues the remaining
     * time instead of resetting it.
     */
    val deadlineAt: Long = 0,
    val startedAt: Long,
    val lastActiveAt: Long,
    val accumulatedMs: Long,
    val status: Int,
)
