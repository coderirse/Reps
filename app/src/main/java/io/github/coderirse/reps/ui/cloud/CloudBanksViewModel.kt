package io.github.coderirse.reps.ui.cloud

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.R
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.data.net.CloudBankDto
import io.github.coderirse.reps.data.repo.CloudBankRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 云端题库列表页。下载完成后把 URI 抛给导航层，交给既有的导入预览页
 * （ImportPreviewScreen）——入库始终是用户在预览页里确认之后才发生。
 */
class CloudBanksViewModel(
    private val appContext: Context,
    private val repository: CloudBankRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val banks: List<CloudBankDto> = emptyList(),
        val failed: Boolean = false,
        /** 正在下载的题库 id；非空时该项显示进度条。 */
        val downloadingId: String? = null,
        val progress: Float = 0f,
        /** 下载完成的本地 URI，导航到导入预览后置空。 */
        val previewUri: String? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun load() {
        if (_state.value.loading && _state.value.banks.isNotEmpty()) return
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { banks ->
                    _state.update { it.copy(loading = false, banks = banks, failed = false) }
                }
                .onFailure {
                    // 列表接口失败要明确报错并可重试，不能伪装成"云端没有题库"——
                    // 后者会让用户以为作者没上架，白白放弃。
                    _state.update { it.copy(loading = false, failed = true) }
                }
        }
    }

    fun download(bank: CloudBankDto) {
        if (_state.value.downloadingId != null) return
        _state.update { it.copy(downloadingId = bank.id, progress = 0f) }
        viewModelScope.launch {
            runCatching {
                repository.download(bank) { value ->
                    _state.update { it.copy(progress = value) }
                }
            }.onSuccess { uri ->
                _state.update {
                    it.copy(downloadingId = null, progress = 0f, previewUri = uri.toString())
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloadingId = null,
                        progress = 0f,
                        message = error.message?.takeIf { m -> m.isNotBlank() }
                            ?: appContext.getString(R.string.cloud_download_failed),
                    )
                }
            }
        }
    }

    fun consumePreview() = _state.update { it.copy(previewUri = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                CloudBanksViewModel(
                    appContext = app,
                    repository = app.cloudBankRepository,
                )
            }
        }
    }
}
