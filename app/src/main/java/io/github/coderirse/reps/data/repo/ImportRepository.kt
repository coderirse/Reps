package io.github.coderirse.reps.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import io.github.coderirse.reps.data.csv.CsvQuestionParser
import io.github.coderirse.reps.data.csv.CsvParseResult
import io.github.coderirse.reps.data.csv.EncodingDetector
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.SubjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a CSV via SAF, detects encoding, parses/validates, imports atomically. */
class ImportRepository(
    private val context: Context,
    private val db: RepsDatabase,
) {

    private val parser = CsvQuestionParser(maxPreview = PREVIEW_COUNT)

    suspend fun readAndParse(uri: Uri): CsvParseResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext CsvParseResult(headerError = "无法读取所选文件")
        val charset = EncodingDetector.detect(bytes, EncodingDetector::icuCrossCheck)
            ?: return@withContext CsvParseResult(
                headerError = "无法识别文件编码，请将文件另存为 UTF-8 后重试",
            )
        val text = String(bytes, charset)
        parser.parse(text)
    }

    /** Suggests a subject name from the file's display name (minus extension). */
    suspend fun suggestName(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull() ?: "导入的题库"
    }

    /** All-or-nothing: subject + every question in one transaction. */
    suspend fun import(name: String, result: CsvParseResult): Long = withContext(Dispatchers.IO) {
        check(result.canImport) { "Nothing to import" }
        val questions = result.questions
        db.withTransaction {
            val subjectId = db.subjectDao().insert(
                SubjectEntity(name = name.trim(), questionCount = questions.size, createdAt = System.currentTimeMillis()),
            )
            db.questionDao().insertAll(
                questions.map { q ->
                    QuestionEntity(
                        subjectId = subjectId,
                        orderIndex = q.orderIndex,
                        type = q.type,
                        content = q.content,
                        optionA = q.optionA,
                        optionB = q.optionB,
                        optionC = q.optionC,
                        optionD = q.optionD,
                        optionE = q.optionE,
                        correctAnswer = q.correctAnswer,
                        explanation = q.explanation,
                        category = q.category,
                        chapter = q.chapter,
                    )
                },
            )
            subjectId
        }
    }

    private companion object {
        const val PREVIEW_COUNT = 50
    }
}
