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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.coderirse.reps.BuildConfig
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.net.AppVersionDto
import io.github.coderirse.reps.data.net.Downloads
import io.github.coderirse.reps.data.net.RepsApi
import io.github.coderirse.reps.ui.components.userFacingMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
        /** 0..1 的进度；服务端没给总大小时为 [Downloads.PROGRESS_UNKNOWN]。 */
        val progress: Float = 0f,
        /**
         * 更新框内的一次性错误（下载/安装失败）。显示在框里而不是 Snackbar——
         * 框可能在任意页面弹出（启动时静默检查），那页未必有 SnackbarHost。
         */
        val dialogError: String? = null,
        /** 一次性提示（Snackbar），消费后置空。手动检查的反馈走这里。 */
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** 授权设置页返回后续装用的 APK；null 表示没有待安装的下载产物。 */
    private var pendingInstall: File? = null

    /**
     * 检查更新。[silent] = true 用于应用启动时自动检查：只有「确实有新版本」才弹框，
     * 没新版本或没网都不打扰用户；手动检查则必须有明确反馈。
     */
    fun check(silent: Boolean = false) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val info = RepsApi.appLatest()
                _state.update { it.copy(checking = false) }
                if (info.versionCode > BuildConfig.VERSION_CODE && info.url.isNotBlank()) {
                    _state.update { it.copy(available = info) }
                } else if (!silent) {
                    _state.update {
                        it.copy(message = context.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        checking = false,
                        message = if (silent) {
                            null
                        } else {
                            userFacingMessage(context, e, R.string.update_check_failed)
                        },
                    )
                }
            }
        }
    }

    fun downloadAndInstall() {
        val info = _state.value.available ?: return
        if (_state.value.downloading) return
        pendingInstall = null
        _state.update { it.copy(downloading = true, progress = 0f, dialogError = null) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                // 上一轮更新留在缓存里的 APK 在这里清掉
                Downloads.clearCachedApks(context)
                val file = Downloads.toCache(
                    context = context,
                    url = info.url,
                    fileName = "reps-${info.versionName.ifBlank { "update" }}.apk",
                    // 明文 HTTP 下摘要只能防传输损坏，防不了中间人（见 Downloads.toCache 注释）
                    sha256 = info.apkSha256,
                    expectedBytes = info.size,
                ) { progress, _, _ -> _state.update { it.copy(progress = progress) } }
                _state.update { it.copy(downloading = false) }
                if (!Downloads.installApk(context, file)) {
                    // 用户去开了「安装未知应用」授权；返回后 onHostResumed 自动续装
                    pendingInstall = file
                    _state.update {
                        it.copy(dialogError = context.getString(R.string.update_need_install_permission))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        downloading = false,
                        dialogError = userFacingMessage(context, e, R.string.update_download_failed),
                    )
                }
            }
        }
    }

    /**
     * 宿主 ON_RESUME：用户从「安装未知应用」设置页授权返回时自动继续安装，
     * 不需要再手动点一次「立即更新」。
     */
    fun onHostResumed() {
        val file = pendingInstall ?: return
        val context = getApplication<Application>()
        if (context.packageManager.canRequestPackageInstalls()) {
            pendingInstall = null
            Downloads.installApk(context, file)
            _state.update { it.copy(dialogError = null) }
        }
    }

    /** 强制更新时不允许关闭（与 showwe 的更新弹窗语义一致）。 */
    fun dismiss() {
        if (_state.value.downloading) return
        if (_state.value.available?.force == true) return
        pendingInstall = null
        _state.update { it.copy(available = null, dialogError = null) }
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

    // 从授权设置页返回时自动续装（见 UpdateViewModel.onHostResumed）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onHostResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    if (state.progress >= 0f) {
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
                    } else {
                        // 服务端没给总大小：显示不确定进度，不假装知道百分比
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.update_downloading_unknown),
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (state.dialogError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.dialogError!!,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.error,
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
