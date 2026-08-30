package io.github.coderirse.reps.ui.import

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.csv.CsvParseResult
import io.github.coderirse.reps.data.repo.ImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportUiState {
    data object Loading : ImportUiState
    data class Ready(val result: CsvParseResult, val suggestedName: String) : ImportUiState
    data object Writing : ImportUiState
    data class Done(val subjectId: Long, val importedCount: Int) : ImportUiState
    data class Failed(val message: String) : ImportUiState
}

class ImportPreviewViewModel(
    private val uri: Uri,
    savedStateHandle: SavedStateHandle,
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
                _state.value = ImportUiState.Failed(t.message ?: "解析失败")
            }
        }
    }

    fun rename(name: String) {
        subjectName = name
    }

    fun confirm() {
        val result = parsed ?: return
        _state.value = ImportUiState.Writing
        viewModelScope.launch {
            try {
                val subjectId = importRepository.import(subjectName.ifBlank { "导入的题库" }, result)
                _state.value = ImportUiState.Done(subjectId, result.questions.size)
            } catch (t: Throwable) {
                _state.value = ImportUiState.Failed(t.message ?: "写入失败")
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
                    savedStateHandle = SavedStateHandle(),
                    importRepository = app.importRepository,
                )
            }
        }
    }
}
