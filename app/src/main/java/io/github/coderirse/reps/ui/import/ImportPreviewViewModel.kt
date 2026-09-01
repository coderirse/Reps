package io.github.coderirse.reps.ui.import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.R
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.csv.CsvParseResult
import io.github.coderirse.reps.data.repo.ImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Loading : ImportUiState
    data class Ready(val result: CsvParseResult, val suggestedName: String) : ImportUiState
    data object Writing : ImportUiState
    data class Done(val subjectId: Long, val importedCount: Int) : ImportUiState
    data class Failed(val message: String) : ImportUiState
}

class ImportPreviewViewModel(
    private val uri: Uri,
    private val appContext: Context,
    private val importRepository: ImportRepository,
) : ViewModel() {

    var subjectName: String = ""

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val state: StateFlow<ImportUiState> = _state

    private var parsed: CsvParseResult? = null

    init {
        viewModelScope.launch {
            try {
                val result = importRepository.readAndParse(uri)
                parsed = result
                val name = importRepository.suggestName(uri)
                subjectName = name
                _state.value = ImportUiState.Ready(result, name)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.value = ImportUiState.Failed(t.message ?: appContext.getString(R.string.import_parse_failed))
            }
        }
    }

    fun rename(name: String) {
        subjectName = name
    }

    fun confirm() {
        // Re-entry guard: a fast double tap would otherwise launch two import
        // coroutines and produce two duplicate libraries.
        if (_state.value is ImportUiState.Writing || _state.value is ImportUiState.Done) return
        val result = parsed ?: return
        _state.value = ImportUiState.Writing
        viewModelScope.launch {
            try {
                val subjectId = importRepository.import(subjectName.ifBlank { appContext.getString(R.string.import_default_subject_name) }, result)
                _state.value = ImportUiState.Done(subjectId, result.questions.size)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.value = ImportUiState.Failed(t.message ?: appContext.getString(R.string.import_write_failed))
            }
        }
    }

    companion object {
        const val KEY_URI = "import_uri"

        fun create(uri: Uri) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                ImportPreviewViewModel(
                    uri = uri,
                    appContext = app,
                    importRepository = app.importRepository,
                )
            }
        }
    }
}
