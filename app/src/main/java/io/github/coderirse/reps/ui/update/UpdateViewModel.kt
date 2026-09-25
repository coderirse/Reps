package io.github.coderirse.reps.ui.update

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.coderirse.reps.BuildConfig
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.net.AppVersionDto
import io.github.coderirse.reps.data.net.Downloads
import io.github.coderirse.reps.data.net.RepsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 应用内更新：检查服务器上的最新版本，下载 APK 并交给系统安装器。
 *
 * 之前「检查更新」只是跳到 GitHub Releases——国内访问慢，而且在浏览器里下载完
 * 还要用户自己找文件。现在改为直接调后端 `/api/reps/app/latest`。
 *
 * 这是 Reps 仅有的两处联网之一（另一处是云端题库），且只读取版本号与下载 APK，
 * 不上报任何用户数据。
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    data class State(
        val checking: Boolean = false,
        /** 非空表示"发现了更高版本"，UI 据此弹更新框。 */
        val available: AppVersionDto? = null,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        /** 一次性提示（Snackbar），消费后置空。 */
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /**
     * 检查更新。[silent] = true 用于启动时自动检查：只有「确实有新版本」才弹框，
     * 没新版本或没网都不打扰用户；手动检查则必须有明确反馈。
     */
    fun check(silent: Boolean = false) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            runCatching { RepsApi.appLatest() }
                .onSuccess { info ->
                    _state.update { it.copy(checking = false) }
                    if (info.versionCode > BuildConfig.VERSION_CODE && info.url.isNotBlank()) {
                        _state.update { it.copy(available = info) }
                    } else if (!silent) {
                        _state.update {
                            it.copy(message = context.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
                        }
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            checking = false,
                            message = if (silent) null else context.getString(R.string.update_check_failed),
                        )
                    }
                }
        }
    }

    fun downloadAndInstall() {
        val info = _state.value.available ?: return
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, progress = 0f) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            runCatching {
                Downloads.toCache(
                    context = context,
                    url = info.url,
                    fileName = "reps-${info.versionName.ifBlank { "update" }}.apk",
                    // 明文 HTTP 下服务端摘要就是唯一的完整性保障
                    sha256 = info.apkSha256,
                ) { progress -> _state.update { it.copy(progress = progress) } }
            }.onSuccess { file ->
                _state.update { it.copy(downloading = false) }
                if (!Downloads.installApk(context, file)) {
                    _state.update { it.copy(message = context.getString(R.string.update_need_install_permission)) }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        downloading = false,
                        message = error.message?.takeIf { m -> m.isNotBlank() }
                            ?: context.getString(R.string.update_download_failed),
                    )
                }
            }
        }
    }

    /** 强制更新时不允许关闭（与 showwe 的更新弹窗语义一致）。 */
    fun dismiss() {
        if (_state.value.downloading) return
        if (_state.value.available?.force == true) return
        _state.update { it.copy(available = null) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** 1024 进制，够用即可（更新包只有几 MB）。 */
internal fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val info = state.available ?: return
    val force = info.force
    val dismissable = !force && !state.downloading

    AlertDialog(
        onDismissRequest = { viewModel.dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
        ),
        title = {
            Text(
                stringResource(R.string.update_dialog_title, info.versionName),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                if (info.changelog.isNotBlank()) {
                    Text(
                        info.changelog,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.size > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.update_size, formatSize(info.size)),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                if (state.downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.update_downloading, (state.progress * 100).toInt()),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.downloadAndInstall() },
                enabled = !state.downloading,
            ) {
                Text(
                    if (state.downloading) {
                        stringResource(R.string.update_action_downloading)
                    } else {
                        stringResource(R.string.update_action_install)
                    }
                )
            }
        },
        dismissButton = {
            if (!force) {
                TextButton(onClick = { viewModel.dismiss() }, enabled = !state.downloading) {
                    Text(stringResource(R.string.update_action_later))
                }
            }
        },
    )
}
