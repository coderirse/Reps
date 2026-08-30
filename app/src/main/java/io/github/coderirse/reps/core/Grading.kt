package io.github.coderirse.reps.core

import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType

/**
 * Grading helpers. Correct answers are normalized at import time; the user's
 * selection is normalized here before comparison.
 */
object Grading {

    /** Splits "a, c" / "A、C" / "AC" into normalized sorted letters, e.g. ["A", "C"]. */
    fun parseMultiLetters(raw: String): List<String> =
        raw.split(",", "，", "、", ";", "；", " ")
            .flatMap { token ->
                // Handles concatenated letters like "AC" as well as comma-separated ones.
                if (token.isBlank()) {
                    emptyList()
                } else {
                    token.uppercase().filter { it in 'A'..'F' }.map { it.toString() }
                }
            }
            .distinct()
            .sorted()

    fun normalizeSelected(question: QuestionEntity, raw: String): String = when (question.type) {
        QuestionType.MULTI -> parseMultiLetters(raw).joinToString(",")
        QuestionType.JUDGE -> normalizeJudge(raw)
        else -> raw.trim().uppercase()
    }

    /** 对/错 accept common aliases; everything is normalized to 对/错 at import. */
    fun normalizeJudge(raw: String): String = when (raw.trim().lowercase()) {
        "true", "t", "1", "y", "yes", "对", "正确", "√" -> "对"
        "false", "f", "0", "n", "no", "错", "错误", "×" -> "错"
        else -> raw.trim()
    }

    fun isCorrect(question: QuestionEntity, selectedRaw: String): Boolean {
        val selected = normalizeSelected(question, selectedRaw)
        return when (question.type) {
            QuestionType.MULTI ->
                parseMultiLetters(selected) == parseMultiLetters(question.correctAnswer)
            else -> selected == question.correctAnswer
        }
    }
}
