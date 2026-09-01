package io.github.coderirse.reps.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Row counts of one backup operation, for user-facing summaries. */
data class BackupStats(
    val subjects: Int,
    val questions: Int,
    val sessions: Int,
    val answers: Int,
    val wrongs: Int,
    val favorites: Int,
    val notes: Int,
)

/**
 * Fully offline JSON backup: the whole Room database in one human-readable
 * file. Export keeps primary keys; import merges rows by id (upsert), so
 * restoring onto itself is idempotent. Question image assets are NOT part of
 * the backup — only the DB rows.
 */
class BackupRepository(
    private val context: Context,
    private val db: RepsDatabase,
    private val settingsRepository: SettingsRepository,
) {

    // encodeDefaults so fields that only carry their Room-default value
    // (optionF, imageFile, deadlineAt, mastered...) still round-trip.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(uri: Uri): BackupStats = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            subjects = db.subjectDao().getAll(),
            questions = db.questionDao().getAll(),
            studySessions = db.studySessionDao().getAll(),
            sessionAnswers = db.sessionAnswerDao().getAll(),
            wrongAnswers = db.wrongAnswerDao().getAll(),
            favorites = db.favoriteDao().getAll(),
            notes = db.noteDao().getAll(),
            builtinSubjectId = settingsRepository.settings.first().builtinSubjectId,
        )
        val text = json.encodeToString(BackupPayload.serializer(), payload)
        val stream = context.contentResolver.openOutputStream(uri, "w")
            ?: throw BackupFormatException("无法写入所选位置")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        statsOf(payload)
    }

    /**
     * Merges a backup into the current database, row by row matched on primary
     * keys, inside one transaction. Tables are applied parents-first so FK
     * REPLACE cascades never orphan rows mid-import. After the merge the
     * built-in bank flags in DataStore are realigned with the restored rows.
     */
    suspend fun import(uri: Uri): BackupStats = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw BackupFormatException("无法读取所选文件")
        if (text.isBlank()) throw BackupFormatException("文件内容为空")
        val payload = runCatching { json.decodeFromString(BackupPayload.serializer(), text) }
            .getOrElse { throw BackupFormatException("不是有效的 Reps 备份文件（JSON 解析失败）") }
        if (payload.version > BACKUP_VERSION) {
            throw BackupFormatException("备份来自更新版本（v${payload.version}），请先升级应用再导入")
        }
        if (payload.version < 1) {
            throw BackupFormatException("无法识别的备份版本 v${payload.version}")
        }
        db.withTransaction {
            db.subjectDao().upsertAll(payload.subjects)
            db.questionDao().upsertAll(payload.questions)
            db.studySessionDao().upsertAll(payload.studySessions)
            db.sessionAnswerDao().upsertAll(payload.sessionAnswers)
            db.wrongAnswerDao().upsertAll(payload.wrongAnswers)
            db.favoriteDao().upsertAll(payload.favorites)
            db.noteDao().upsertAll(payload.notes)
        }
        // The bundled bank must not be re-imported as a duplicate subject on
        // next launch if the restored data already contains it.
        val builtinId = payload.builtinSubjectId
        if (builtinId >= 0 && db.subjectDao().getById(builtinId) != null) {
            settingsRepository.setBuiltinSubjectId(builtinId)
            settingsRepository.setBuiltinImported(true)
        }
        statsOf(payload)
    }

    private fun statsOf(payload: BackupPayload) = BackupStats(
        subjects = payload.subjects.size,
        questions = payload.questions.size,
        sessions = payload.studySessions.size,
        answers = payload.sessionAnswers.size,
        wrongs = payload.wrongAnswers.size,
        favorites = payload.favorites.size,
        notes = payload.notes.size,
    )
}
