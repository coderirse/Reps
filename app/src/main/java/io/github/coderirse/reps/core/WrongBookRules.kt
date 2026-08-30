package io.github.coderirse.reps.core

import io.github.coderirse.reps.data.db.entity.WrongAnswerEntity

/**
 * Wrong-book state machine (docs/PRODUCT.md section 7.5):
 *  - wrong answer -> enters/stays in the book, streak resets
 *  - correct in review -> streak +1; reaching [MASTERY_THRESHOLD] auto-masters
 *  - correct with no wrong history -> no-op
 */
object WrongBookRules {

    const val MASTERY_THRESHOLD = 2

    /** @return the new entry, or null when nothing needs to be written. */
    fun onAnswered(
        questionId: Long,
        current: WrongAnswerEntity?,
        correct: Boolean,
        now: Long,
    ): WrongAnswerEntity? = when {
        !correct -> WrongAnswerEntity(
            questionId = questionId,
            wrongCount = (current?.wrongCount ?: 0) + 1,
            reviewCorrectCount = 0,
            lastWrongAt = now,
            mastered = false,
        )
        current == null || current.mastered -> null
        else -> {
            val streak = current.reviewCorrectCount + 1
            current.copy(reviewCorrectCount = streak, mastered = streak >= MASTERY_THRESHOLD)
        }
    }
}
