package io.github.coderirse.reps.core

import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradingTest {

    private fun question(type: String, correct: String) = QuestionEntity(
        id = 1,
        subjectId = 1,
        orderIndex = 1,
        type = type,
        content = "content",
        optionA = "A", optionB = "B", optionC = "C", optionD = "D", optionE = null,
        correctAnswer = correct,
        explanation = null, category = null, chapter = null,
    )

    @Test
    fun `single choice strict match`() {
        val q = question(QuestionType.SINGLE, "B")
        assertTrue(Grading.isCorrect(q, "B"))
        assertTrue(Grading.isCorrect(q, "b"))
        assertFalse(Grading.isCorrect(q, "A"))
    }

    @Test
    fun `judge aliases normalize to correct`() {
        val q = question(QuestionType.JUDGE, "对")
        assertTrue(Grading.isCorrect(q, "true"))
        assertTrue(Grading.isCorrect(q, "T"))
        assertTrue(Grading.isCorrect(q, "对"))
        assertTrue(Grading.isCorrect(q, "正确"))
        assertFalse(Grading.isCorrect(q, "false"))
        assertFalse(Grading.isCorrect(q, "错"))
    }

    @Test
    fun `multi choice graded by set equality`() {
        val q = question(QuestionType.MULTI, "A,C")
        assertTrue(Grading.isCorrect(q, "A,C"))
        assertTrue(Grading.isCorrect(q, "c,a"))
        assertTrue(Grading.isCorrect(q, "AC"))
        assertTrue(Grading.isCorrect(q, "a、c"))
        assertFalse(Grading.isCorrect(q, "A"))
        assertFalse(Grading.isCorrect(q, "A,B,C"))
        assertFalse(Grading.isCorrect(q, "A,D"))
    }

    @Test
    fun `normalizeSelected canonicalizes multi answers`() {
        val q = question(QuestionType.MULTI, "A,C")
        assertEquals("A,C", Grading.normalizeSelected(q, "c, a"))
        assertEquals("A,C", Grading.normalizeSelected(q, "CA"))
    }
}
