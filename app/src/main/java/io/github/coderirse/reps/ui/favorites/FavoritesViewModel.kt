package io.github.coderirse.reps.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.repo.StudySessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FavoritesViewModel(
    private val db: RepsDatabase,
    private val sessionRepository: StudySessionRepository,
) : ViewModel() {

    /** @param subjectId null = all subjects. */
    fun observeRows(subjectId: Long?): Flow<List<io.github.coderirse.reps.data.db.dao.FavoriteRow>> =
        db.favoriteDao().observeRows(subjectId)

    fun observeSubjectIds(): Flow<List<Long>> = db.favoriteDao().observeSubjectIds()

    suspend fun getSubjectName(subjectId: Long): String = withContext(Dispatchers.IO) {
        db.subjectDao().getById(subjectId)?.name.orEmpty()
    }

    /** @return session id, or null when the subject has no favorites. */
    suspend fun startPractice(subjectId: Long): Long? = withContext(Dispatchers.IO) {
        val ids = db.favoriteDao().getFavoriteIdsForSubject(subjectId)
        if (ids.isEmpty()) return@withContext null
        sessionRepository.createSession(
            subjectId = subjectId,
            practiceType = "favorite",
            reciteMode = "mode_b_test",
            baseQuestionIds = ids,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                FavoritesViewModel(app.database, app.studySessionRepository)
            }
        }
    }
}
