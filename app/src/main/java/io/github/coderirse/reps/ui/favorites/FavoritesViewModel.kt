package io.github.coderirse.reps.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.dao.FavoriteRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

class FavoritesViewModel(
    private val db: RepsDatabase,
) : ViewModel() {

    // Filter lives in the VM so [rows] is a single Flow: creating the Flow in
    // composition made every recomposition cancel and restart the Room query.
    private val _subjectFilter = MutableStateFlow<Long?>(null)
    val subjectFilter: StateFlow<Long?> = _subjectFilter

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: Flow<List<FavoriteRow>> =
        _subjectFilter.flatMapLatest { db.favoriteDao().observeRows(it) }

    val subjectIds: Flow<List<Long>> = db.favoriteDao().observeSubjectIds()

    fun setSubjectFilter(subjectId: Long?) {
        _subjectFilter.value = subjectId
    }

    suspend fun getSubjectName(subjectId: Long): String = withContext(Dispatchers.IO) {
        db.subjectDao().getById(subjectId)?.name.orEmpty()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                FavoritesViewModel(app.database)
            }
        }
    }
}
