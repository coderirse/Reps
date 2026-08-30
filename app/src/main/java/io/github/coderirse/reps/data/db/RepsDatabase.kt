package io.github.coderirse.reps.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.coderirse.reps.data.db.dao.FavoriteDao
import io.github.coderirse.reps.data.db.dao.NoteDao
import io.github.coderirse.reps.data.db.dao.QuestionDao
import io.github.coderirse.reps.data.db.dao.SessionAnswerDao
import io.github.coderirse.reps.data.db.dao.StudySessionDao
import io.github.coderirse.reps.data.db.dao.SubjectDao
import io.github.coderirse.reps.data.db.dao.WrongAnswerDao
import io.github.coderirse.reps.data.db.entity.FavoriteEntity
import io.github.coderirse.reps.data.db.entity.NoteEntity
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.SessionAnswerEntity
import io.github.coderirse.reps.data.db.entity.StudySessionEntity
import io.github.coderirse.reps.data.db.entity.SubjectEntity
import io.github.coderirse.reps.data.db.entity.WrongAnswerEntity

@Database(
    entities = [
        SubjectEntity::class,
        QuestionEntity::class,
        StudySessionEntity::class,
        SessionAnswerEntity::class,
        WrongAnswerEntity::class,
        FavoriteEntity::class,
        NoteEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class RepsDatabase : RoomDatabase() {

    abstract fun subjectDao(): SubjectDao
    abstract fun questionDao(): QuestionDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun sessionAnswerDao(): SessionAnswerDao
    abstract fun wrongAnswerDao(): WrongAnswerDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun noteDao(): NoteDao

    companion object {
        private const val DB_NAME = "reps.db"

        /** v2: timed CUSTOM sessions need a persisted wall-clock deadline. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE study_sessions ADD COLUMN deadlineAt INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** v3: sixth option (builtin bank cg_m_17) + question image assets. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN optionF TEXT")
                db.execSQL("ALTER TABLE questions ADD COLUMN imageFile TEXT")
            }
        }

        fun build(context: Context): RepsDatabase =
            Room.databaseBuilder(context, RepsDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
