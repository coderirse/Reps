package io.github.coderirse.reps.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.coderirse.reps.data.db.entity.AnswerActionType
import io.github.coderirse.reps.data.db.entity.FavoriteEntity
import io.github.coderirse.reps.data.db.entity.NoteEntity
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.ReciteMode
import io.github.coderirse.reps.data.db.entity.SessionAnswerEntity
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import io.github.coderirse.reps.data.db.entity.SubjectEntity
import io.github.coderirse.reps.data.db.entity.WrongAnswerEntity
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-memory smoke tests for the queries the review flagged as untested:
 * the practiced-progress flow, backup-style id-preserving merges and the
 * cascade/restore round trip behind "delete library + undo".
 */
@RunWith(AndroidJUnit4::class)
class RepsDaoTest {

    private lateinit var db: RepsDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RepsDatabase::class.java,
        ).build()
    }

    @After
    fun closeDb() = db.close()

    private suspend fun newSubject(name: String): Long =
        db.subjectDao().insert(SubjectEntity(name = name, questionCount = 2, createdAt = 1L))

    private suspend fun newQuestions(subjectId: Long, count: Int): List<Long> =
        db.questionDao().insertAll(
            (1..count).map { idx ->
                QuestionEntity(
                    subjectId = subjectId,
                    orderIndex = idx,
                    type = QuestionType.SINGLE,
                    content = "q$idx",
                    optionA = "a",
                    optionB = "b",
                    optionC = null,
                    optionD = null,
                    optionE = null,
                    correctAnswer = "A",
                    explanation = null,
                    category = null,
                    chapter = null,
                )
            },
        )

    private suspend fun newSession(subjectId: Long): Long =
        db.studySessionDao().upsert(
            StudySessionEntity(
                subjectId = subjectId,
                practiceType = PracticeType.SEQUENTIAL,
                filterValue = null,
                reciteMode = ReciteMode.TEST,
                questionIds = "",
                currentIndex = 0,
                selectedAnswer = null,
                answerRevealed = false,
                randomSeed = 0L,
                startedAt = 1L,
                lastActiveAt = 1L,
                accumulatedMs = 0L,
                status = 0,
            ),
        )

    private fun answer(sessionId: Long, questionId: Long) = SessionAnswerEntity(
        sessionId = sessionId,
        questionId = questionId,
        actionType = AnswerActionType.SELECTED,
        selectedAnswer = "A",
        isCorrect = true,
        answeredAt = 1L,
        dwellMs = null,
    )

    @Test
    fun practicedCountsAreDistinctPerSubject() = runBlocking {
        val s1 = newSubject("a")
        val s2 = newSubject("b")
        val qs1 = newQuestions(s1, 2)
        newQuestions(s2, 1)
        val sessionA = newSession(s1)
        val sessionB = newSession(s1)
        db.sessionAnswerDao().upsertAll(
            listOf(
                answer(sessionA, qs1[0]),
                answer(sessionB, qs1[0]), // same question, second session
                answer(sessionA, qs1[1]),
            ),
        )
        val counts = db.sessionAnswerDao().observePracticedCounts()
            .first()
            .associate { it.subjectId to it.count }
        assertEquals(2, counts[s1]) // distinct questions, not answer rows
        assertNull(counts[s2]) // untouched subjects emit no row
    }

    @Test
    fun upsertAllMergesByIdLikeBackupImport() = runBlocking {
        val s = newSubject("a")
        val q = newQuestions(s, 1)
        db.wrongAnswerDao().upsert(
            WrongAnswerEntity(questionId = q[0], wrongCount = 1, reviewCorrectCount = 0, lastWrongAt = 1L),
        )
        db.wrongAnswerDao().upsertAll(
            listOf(
                WrongAnswerEntity(questionId = q[0], wrongCount = 5, reviewCorrectCount = 1, lastWrongAt = 2L),
            ),
        )
        val row = db.wrongAnswerDao().getByQuestion(q[0])
        assertNotNull(row)
        assertEquals(5, row!!.wrongCount)
        assertEquals(1, db.wrongAnswerDao().getAll().size) // merged, not duplicated
    }

    @Test
    fun subjectDeleteCascadesAndSnapshotRestoreRoundTrips() = runBlocking {
        val s = newSubject("a")
        val qs = newQuestions(s, 2)
        val session = newSession(s)
        db.sessionAnswerDao().upsert(answer(session, qs[0]))
        db.wrongAnswerDao().upsert(
            WrongAnswerEntity(questionId = qs[0], wrongCount = 2, reviewCorrectCount = 0, lastWrongAt = 1L),
        )
        db.favoriteDao().add(FavoriteEntity(questionId = qs[1], createdAt = 1L))
        db.noteDao().upsert(NoteEntity(questionId = qs[1], content = "note", updatedAt = 1L))

        // Collect exactly what HomeViewModel.deleteSubject snapshots.
        val subject = db.subjectDao().getById(s)!!
        val questions = db.questionDao().getForSubject(s)
        val sessions = db.studySessionDao().getForSubject(s)
        val answers = db.sessionAnswerDao().getForSubject(s)
        val wrongs = db.wrongAnswerDao().getForSubject(s)
        val favorites = db.favoriteDao().getForSubject(s)
        val notes = db.noteDao().getForSubject(s)

        db.subjectDao().deleteById(s)
        assertEquals(0, db.questionDao().getAll().size)
        assertEquals(0, db.studySessionDao().getAll().size)
        assertEquals(0, db.wrongAnswerDao().getAll().size)
        assertEquals(0, db.favoriteDao().getAll().size)
        assertEquals(0, db.noteDao().getAll().size)

        // Undo: the parent-first restore order used by restoreSubject.
        db.subjectDao().upsertAll(listOf(subject))
        db.questionDao().upsertAll(questions)
        db.studySessionDao().upsertAll(sessions)
        db.sessionAnswerDao().upsertAll(answers)
        db.wrongAnswerDao().upsertAll(wrongs)
        db.favoriteDao().upsertAll(favorites)
        db.noteDao().upsertAll(notes)

        assertEquals(listOf(subject), db.subjectDao().getAll())
        assertEquals(2, db.questionDao().getAll().size)
        assertEquals(1, db.sessionAnswerDao().getAll().size)
        assertEquals(2, db.wrongAnswerDao().getByQuestion(qs[0])!!.wrongCount)
        assertEquals("note", db.noteDao().getByQuestion(qs[1])!!.content)
    }

    @Test
    fun unmasteredCountsGroupBySubject() = runBlocking {
        val s1 = newSubject("a")
        val s2 = newSubject("b")
        val q1 = newQuestions(s1, 2)
        val q2 = newQuestions(s2, 2)
        db.wrongAnswerDao().upsertAll(
            listOf(
                WrongAnswerEntity(questionId = q1[0], wrongCount = 1, reviewCorrectCount = 0, lastWrongAt = 1L),
                WrongAnswerEntity(questionId = q1[1], wrongCount = 1, reviewCorrectCount = 0, lastWrongAt = 1L),
                WrongAnswerEntity(questionId = q2[0], wrongCount = 1, reviewCorrectCount = 0, lastWrongAt = 1L, mastered = true),
            ),
        )
        val counts = db.wrongAnswerDao().getUnmasteredCountsBySubject()
            .associate { it.subjectId to it.count }
        assertEquals(2, counts[s1])
        assertNull(counts[s2]) // mastered rows stay out of the picker
    }

    @Test
    fun browsedThenGradedKeepsTheGradedAnswer() = runBlocking {
        // Regression: @Upsert conflicts fall back to UPDATE by primary key,
        // and rows insert with id = 0, so grading a previously browsed
        // question used to silently vanish behind the unique index.
        val s = newSubject("a")
        val q = newQuestions(s, 1)[0]
        val sessionId = newSession(s)
        val repo = StudySessionRepository(db)
        val session = db.studySessionDao().getById(sessionId)!!
        val question = db.questionDao().getByIds(listOf(q)).first()

        repo.recordBrowsed(session, q)
        repo.recordBrowsed(session, q) // re-browse must not duplicate the row
        repo.recordGradedAnswer(session, question, "A", dwellMs = 100)

        val rows = db.sessionAnswerDao().getBySession(sessionId)
        assertEquals(1, rows.size)
        assertEquals(AnswerActionType.SELECTED, rows[0].actionType)
        assertEquals("A", rows[0].selectedAnswer)
        assertEquals(true, rows[0].isCorrect)
    }
}
