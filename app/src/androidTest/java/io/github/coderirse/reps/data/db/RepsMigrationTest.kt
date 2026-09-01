package io.github.coderirse.reps.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the real MIGRATION_1_2 / MIGRATION_2_3 paths land on the v3 schema
 * without losing the rows they were shipped with. Runs on device only
 * (instrumented); the exported schema JSONs reach the helper through the
 * `room.schemaLocation` instrumentation argument in app/build.gradle.kts.
 */
@RunWith(AndroidJUnit4::class)
class RepsMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RepsDatabase::class.java,
    )

    @Test
    fun migrate1To3KeepsRowsAndAddsV3Columns() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO subjects (id, name, questionCount, createdAt) VALUES (1, '旧题库', 1, 1000)",
            )
            execSQL(
                "INSERT INTO questions (id, subjectId, orderIndex, type, content, optionA, optionB, " +
                    "optionC, optionD, optionE, correctAnswer, explanation, category, chapter) " +
                    "VALUES (1, 1, 1, 'single', '旧题', 'A', 'B', 'C', 'D', 'E', 'A', '', '', '')",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            validateDroppedTables = true,
            RepsDatabase.MIGRATION_1_2,
            RepsDatabase.MIGRATION_2_3,
        ).apply {
            // v1 rows survive; the new v3 columns exist and default correctly.
            execSQL("INSERT INTO questions (id, subjectId, orderIndex, type, content, optionF, imageFile, correctAnswer) " +
                "VALUES (2, 1, 2, 'single', '新题', 'F', 'a/b.png', 'F')")
            query("SELECT COUNT(*) AS n FROM questions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("n")))
            }
            close()
        }
    }

    @Test
    fun migrate2To3AddsOptionFAndImageFile() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO subjects (id, name, questionCount, createdAt) VALUES (1, 'v2题库', 1, 1000)",
            )
            execSQL(
                "INSERT INTO questions (id, subjectId, orderIndex, type, content, optionA, optionB, " +
                    "optionC, optionD, optionE, correctAnswer, explanation, category, chapter) " +
                    "VALUES (1, 1, 1, 'single', 'v2题', 'A', 'B', 'C', 'D', 'E', 'A', '', '', '')",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            validateDroppedTables = true,
            RepsDatabase.MIGRATION_2_3,
        ).apply {
            // v3-only columns accept writes; the v2 row survives untouched.
            execSQL("INSERT INTO questions (id, subjectId, orderIndex, type, content, optionF, imageFile, correctAnswer) " +
                "VALUES (2, 1, 2, 'single', '新题', 'F', 'a/b.png', 'F')")
            query("SELECT COUNT(*) AS n FROM questions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("n")))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "reps-migration-test"
    }
}
