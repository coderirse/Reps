package io.github.coderirse.reps.ui.cloud

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.net.CloudBankDto
import io.github.coderirse.reps.ui.components.EmptyState
import io.github.coderirse.reps.ui.update.formatSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云端题库：作者在后台上架 CSV 后，这里会立刻出现（服务端热加载，不需要更新 App）。
 *
 * 点击某一项只做「下载到本地缓存」，随后跳到既有的导入预览页——入库仍然要用户在
 * 预览页里确认，与本地 CSV 导入是同一条路径、同一套校验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBanksScreen(
    onBack: () -> Unit,
    onOpenImportPreview: (Uri) -> Unit,
    viewModel: CloudBanksViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 下载完成 -> 交给导入预览页；先消费掉事件，避免返回时重复跳转
    LaunchedEffect(state.previewUri) {
        state.previewUri?.let { raw ->
            viewModel.consumePreview()
            onOpenImportPreview(Uri.parse(raw))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_banks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.cloud_banks_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.failed -> EmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.cloud_banks_failed),
                    description = stringResource(R.string.cloud_banks_failed_desc),
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        Button(onClick = { viewModel.load() }) {
                            Text(stringResource(R.string.cloud_banks_retry))
                        }
                    },
                )

                state.banks.isEmpty() -> EmptyState(
                    icon = Icons.Filled.CloudDownload,
                    title = stringResource(R.string.cloud_banks_empty_title),
                    description = stringResource(R.string.cloud_banks_empty_desc),
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> BankList(state = state, onDownload = viewModel::download)
            }
        }
    }
}

@Composable
private fun BankList(state: CloudBanksViewModel.State, onDownload: (CloudBankDto) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.banks, key = { it.id }) { bank ->
            BankCard(
                bank = bank,
                dateText = if (bank.updatedAt > 0) dateFormat.format(Date(bank.updatedAt)) else "",
                downloading = state.downloadingId == bank.id,
                progress = if (state.downloadingId == bank.id) state.progress else 0f,
                // 同时只下一个：并发下载在弱网下只会互相拖慢，还容易让人误以为卡死
                enabled = state.downloadingId == null,
                onClick = { onDownload(bank) },
            )
        }
        item {
            Text(
                stringResource(R.string.cloud_banks_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun BankCard(
    bank: CloudBankDto,
    dateText: String,
    downloading: Boolean,
    progress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(bank.name, style = MaterialTheme.typography.titleMedium)
                if (bank.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        bank.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    // 题数是清单里的声明值，允许作者不填；缺失时只显示体积
                    if (bank.questionCount > 0) {
                        stringResource(
                            R.string.cloud_bank_meta,
                            bank.questionCount,
                            formatSize(bank.sizeBytes),
                        )
                    } else {
                        stringResource(R.string.cloud_bank_size_only, formatSize(bank.sizeBytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dateText.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.cloud_bank_updated, dateText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(10.dp))
                    if (progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.cloud_downloading, (progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        // 总大小未知（chunked 响应）：不确定进度，不显示假百分比
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.cloud_downloading_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            if (downloading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
