package io.github.coderirse.reps.data.backup

import kotlinx.serialization.Serializable

/** Bump when the JSON shape changes; importers reject higher versions. */
const val BACKUP_VERSION = 1

/**
 * Full offline snapshot of everything the app stores locally. Entities are
 * serialized directly (all fields are primitives or nullable primitives) so
 * the exported file keeps the exact primary keys, which is what makes the
 * "覆盖合并" import possible: rows are matched by id instead of re-created.
 */
@Serializable
data class BackupPayload(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val subjects: List<io.github.coderirse.reps.data.db.entity.SubjectEntity> = emptyList(),
    val questions: List<io.github.coderirse.reps.data.db.entity.QuestionEntity> = emptyList(),
    val studySessions: List<io.github.coderirse.reps.data.db.entity.StudySessionEntity> = emptyList(),
    val sessionAnswers: List<io.github.coderirse.reps.data.db.entity.SessionAnswerEntity> = emptyList(),
    val wrongAnswers: List<io.github.coderirse.reps.data.db.entity.WrongAnswerEntity> = emptyList(),
    val favorites: List<io.github.coderirse.reps.data.db.entity.FavoriteEntity> = emptyList(),
    val notes: List<io.github.coderirse.reps.data.db.entity.NoteEntity> = emptyList(),
    /**
     * Built-in bank subject id as recorded on the exporting device (-1 = none).
     * Import restores the DataStore builtin flags from it, otherwise the app
     * would re-import the bundled bank and end up with a duplicate.
     */
    val builtinSubjectId: Long = -1L,
) {
    val questionCount: Int get() = questions.size
    val subjectCount: Int get() = subjects.size
}

class BackupFormatException(message: String) : IllegalArgumentException(message)
