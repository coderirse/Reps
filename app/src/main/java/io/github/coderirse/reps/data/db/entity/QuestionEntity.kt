package io.github.coderirse.reps.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Question types. MVP supports [TYPE_SINGLE] and [TYPE_JUDGE]; MULTI/BLANK are reserved. */
object QuestionType {
    const val SINGLE = "single"
    const val JUDGE = "judge"
    const val MULTI = "multi"
    const val BLANK = "blank"
}

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subjectId", "orderIndex"], unique = true),
        Index(value = ["subjectId", "category"]),
        Index(value = ["subjectId", "chapter"]),
    ],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    /** Question number inside the subject; sequential practice order. */
    val orderIndex: Int,
    val type: String,
    val content: String,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val optionD: String?,
    val optionE: String?,
    val optionF: String? = null,
    /** Normalized correct answer: "A" / "对" / "A,C". */
    val correctAnswer: String,
    val explanation: String?,
    val category: String?,
    val chapter: String?,
    /** Asset-relative image path (built-in bank), e.g. builtin_bank/images/fig_01.jpeg. */
    val imageFile: String? = null,
)
