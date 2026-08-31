package io.github.coderirse.reps.data.csv

import io.github.coderirse.reps.core.Grading
import io.github.coderirse.reps.data.db.entity.QuestionType

/** A question parsed and validated from one CSV row, ready for import. */
data class ParsedQuestion(
    val orderIndex: Int,
    val type: String,
    val content: String,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val optionD: String?,
    val optionE: String?,
    val optionF: String?,
    val correctAnswer: String,
    val explanation: String?,
    val category: String?,
    val chapter: String?,
    /** Raw image reference from the CSV; caller decides the storage mapping. */
    val image: String?,
)

data class RowError(val lineNo: Int, val reason: String)

data class CsvParseResult(
    val headerError: String? = null,
    val questions: List<ParsedQuestion> = emptyList(),
    val preview: List<ParsedQuestion> = emptyList(),
    val totalRows: Int = 0,
    val skippedUnsupported: Int = 0,
    /** Rows that carry an image reference (user imports cannot display images yet). */
    val withImage: Int = 0,
    val errors: List<RowError> = emptyList(),
) {
    val canImport: Boolean get() = headerError == null && questions.isNotEmpty()
}

/**
 * Parses CSV text into validated questions. Column names are case-insensitive
 * and trimmed; required: content/type/correct_answer.
 *
 * Uses an in-house RFC 4180 tokenizer (quotes, embedded commas/newlines, CRLF)
 * instead of kotlin-csv: ragged rows must surface as per-row errors with row
 * numbers, while kotlin-csv aborts the whole file without row information.
 * Rows shorter than the header simply miss trailing optional columns.
 */
class CsvQuestionParser(private val maxPreview: Int = 50) {

    fun parse(text: String): CsvParseResult {
        val rows = parseCsvRows(text)

        if (rows.isEmpty()) {
            return CsvParseResult(headerError = "文件为空")
        }

        val header = rows[0].map { it.trim().lowercase() }
        val index = header.withIndex().associate { (i, name) -> name to i }
        val missing = REQUIRED_COLUMNS.filter { it !in index }
        if (missing.isNotEmpty()) {
            return CsvParseResult(headerError = "缺少必需列：${missing.joinToString("、")}")
        }

        val questions = mutableListOf<ParsedQuestion>()
        val errors = mutableListOf<RowError>()
        val seenIds = mutableSetOf<Int>()
        var skippedUnsupported = 0
        val dataRows = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }

        dataRows.forEachIndexed { rowIndex, cells ->
            val lineNo = rowIndex + 2 // 1-based, header is row 1
            fun cell(name: String): String? =
                index[name]?.let { cells.getOrNull(it) }?.trim()?.ifEmpty { null }

            fun fail(reason: String) { errors += RowError(lineNo, reason) }

            val type = cell(COL_TYPE)?.lowercase() ?: ""
            if (type !in SUPPORTED_TYPES) {
                if (type.isNotEmpty()) skippedUnsupported++
                return@forEachIndexed
            }
            val content = cell(COL_CONTENT)
            if (content.isNullOrBlank()) {
                fail("第 $lineNo 行：题干为空")
                return@forEachIndexed
            }

            val options = listOf(
                cell(COL_OPTION_A), cell(COL_OPTION_B), cell(COL_OPTION_C),
                cell(COL_OPTION_D), cell(COL_OPTION_E), cell(COL_OPTION_F),
            )
            fun optionOf(letter: Char): String? = options.getOrNull(letter - 'A')

            val rawAnswer = cell(COL_CORRECT_ANSWER)
            val correctAnswer = when (type) {
                QuestionType.JUDGE -> {
                    val normalized = rawAnswer?.let { Grading.normalizeJudge(it) }
                    if (normalized != "对" && normalized != "错") {
                        fail("第 $lineNo 行：判断题答案必须是 对/错（收到「$rawAnswer」）")
                        return@forEachIndexed
                    }
                    normalized
                }
                QuestionType.SINGLE -> {
                    val letter = rawAnswer?.uppercase()?.singleOrNull()
                    if (letter == null || letter !in 'A'..'F') {
                        fail("第 $lineNo 行：单选答案必须是 A-F 之一（收到「$rawAnswer」）")
                        return@forEachIndexed
                    }
                    if (optionOf(letter).isNullOrBlank()) {
                        fail("第 $lineNo 行：答案 $letter 对应的选项为空")
                        return@forEachIndexed
                    }
                    letter.toString()
                }
                QuestionType.MULTI -> {
                    val upper = rawAnswer?.uppercase().orEmpty()
                    // Surface invalid letters explicitly instead of silently
                    // filtering them out ("A,G" used to become "A" and then
                    // fail with a confusing "needs two options" message).
                    val invalidLetters = upper.filter { it.isLetter() && it !in 'A'..'F' }.distinct()
                    if (invalidLetters.isNotEmpty()) {
                        fail("第 $lineNo 行：答案包含无效选项字母 ${invalidLetters.joinToString("、")}（收到「$rawAnswer」）")
                        return@forEachIndexed
                    }
                    val distinct = upper.filter { it in 'A'..'F' }.distinct().map { it.toString() }
                    if (distinct.size < 2) {
                        fail("第 $lineNo 行：多选答案至少需要两个不同选项（收到「$rawAnswer」）")
                        return@forEachIndexed
                    }
                    val sorted = distinct.sorted()
                    val missingOption = distinct.firstOrNull { optionOf(it[0]).isNullOrBlank() }
                    if (missingOption != null) {
                        fail("第 $lineNo 行：答案 $missingOption 对应的选项为空")
                        return@forEachIndexed
                    }
                    sorted.joinToString(",")
                }
                else -> {
                    skippedUnsupported++
                    return@forEachIndexed
                }
            }

            // Explicit id must be a positive integer; fall back to the row
            // number only when the column is empty.
            val rawId = cell(COL_ID)
            val orderIndex = if (rawId != null) {
                val parsed = rawId.toIntOrNull()
                if (parsed == null || parsed < 1) {
                    fail("第 $lineNo 行：题号必须是正整数（收到「$rawId」）")
                    return@forEachIndexed
                }
                parsed
            } else {
                rowIndex + 1
            }
            if (!seenIds.add(orderIndex)) {
                fail("第 $lineNo 行：题号 $orderIndex 重复")
                return@forEachIndexed
            }

            questions += ParsedQuestion(
                orderIndex = orderIndex,
                type = type,
                content = content,
                optionA = options[0],
                optionB = options[1],
                optionC = options[2],
                optionD = options[3],
                optionE = options[4],
                optionF = options.getOrNull(5),
                correctAnswer = correctAnswer,
                explanation = cell(COL_EXPLANATION),
                category = cell(COL_CATEGORY),
                chapter = cell(COL_CHAPTER),
                image = cell(COL_IMAGE),
            )
        }

        return CsvParseResult(
            questions = questions,
            preview = questions.take(maxPreview),
            totalRows = dataRows.size,
            skippedUnsupported = skippedUnsupported,
            withImage = questions.count { it.image != null },
            errors = errors,
        )
    }

    internal fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < n && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row += field.toString(); field.setLength(0) }
                c == '\r' -> {
                    if (i + 1 < n && text[i + 1] == '\n') i++
                    row += field.toString(); field.setLength(0)
                    rows += row.toList(); row.clear()
                }
                c == '\n' -> {
                    row += field.toString(); field.setLength(0)
                    rows += row.toList(); row.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row.toList()
        }
        return rows
    }

    private companion object {
        const val COL_ID = "id"
        const val COL_CONTENT = "content"
        const val COL_TYPE = "type"
        const val COL_OPTION_A = "option_a"
        const val COL_OPTION_B = "option_b"
        const val COL_OPTION_C = "option_c"
        const val COL_OPTION_D = "option_d"
        const val COL_OPTION_E = "option_e"
        const val COL_OPTION_F = "option_f"
        const val COL_CORRECT_ANSWER = "correct_answer"
        const val COL_EXPLANATION = "explanation"
        const val COL_CATEGORY = "category"
        const val COL_CHAPTER = "chapter"
        const val COL_IMAGE = "image"
        val REQUIRED_COLUMNS = listOf(COL_CONTENT, COL_TYPE, COL_CORRECT_ANSWER)
        val SUPPORTED_TYPES = setOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE)
    }
}
