package io.github.coderirse.reps.core

import io.github.coderirse.reps.data.db.entity.WrongAnswerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrongBookRulesTest {

    private val now = 1_000_000L

    @Test
    fun `first wrong answer enters the book`() {
        val entry = WrongBookRules.onAnswered(7, current = null, correct = false, now = now)!!
        assertEquals(7, entry.questionId)
        assertEquals(1, entry.wrongCount)
        assertEquals(0, entry.reviewCorrectCount)
        assertFalse(entry.mastered)
    }

    @Test
    fun `wrong again increments count and resets streak`() {
        val current = WrongAnswerEntity(7, wrongCount = 2, reviewCorrectCount = 1, lastWrongAt = 0, mastered = false)
        val entry = WrongBookRules.onAnswered(7, current, correct = false, now = now)!!
        assertEquals(3, entry.wrongCount)
        assertEquals(0, entry.reviewCorrectCount)
        assertFalse(entry.mastered)
    }

    @Test
    fun `two correct reviews auto-master`() {
        val current = WrongAnswerEntity(7, wrongCount = 1, reviewCorrectCount = 0, lastWrongAt = 0, mastered = false)
        val afterFirst = WrongBookRules.onAnswered(7, current, correct = true, now = now)!!
        assertFalse(afterFirst.mastered)
        assertEquals(1, afterFirst.reviewCorrectCount)
        val afterSecond = WrongBookRules.onAnswered(7, afterFirst, correct = true, now = now)!!
        assertTrue(afterSecond.mastered)
        assertEquals(2, afterSecond.reviewCorrectCount)
    }

    @Test
    fun `correct with no wrong history is a no-op`() {
        assertNull(WrongBookRules.onAnswered(7, current = null, correct = true, now = now))
    }

    @Test
    fun `wrong answer on a mastered question sends it back to the book`() {
        val mastered = WrongAnswerEntity(7, wrongCount = 1, reviewCorrectCount = 2, lastWrongAt = 0, mastered = true)
        val entry = WrongBookRules.onAnswered(7, mastered, correct = false, now = now)!!
        assertFalse(entry.mastered)
        assertEquals(2, entry.wrongCount)
        assertEquals(0, entry.reviewCorrectCount)
    }

    @Test
    fun `correct on an already mastered question is a no-op`() {
        val mastered = WrongAnswerEntity(7, wrongCount = 1, reviewCorrectCount = 2, lastWrongAt = 0, mastered = true)
        assertNull(WrongBookRules.onAnswered(7, mastered, correct = true, now = now))
    }
}
