package io.github.coderirse.reps.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.coderirse.reps.R
import io.github.coderirse.reps.core.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    sessionId: Long,
    onDone: () -> Unit,
    viewModel: ResultViewModel = viewModel(factory = ResultViewModel.create(sessionId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.result_answered), "${state.answered}", Modifier.weight(1f))
                StatCard(stringResource(R.string.result_correct), "${state.correct}", Modifier.weight(1f))
                StatCard(
                    stringResource(R.string.result_accuracy),
                    if (state.answered == 0) "—" else "${state.correct * 100 / state.answered}%",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.result_duration, formatDuration(state.durationMs)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (state.wrongItems.isEmpty()) {
                Text(
                    stringResource(R.string.result_no_wrong),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    stringResource(R.string.result_wrong_list, state.wrongItems.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(state.wrongItems.size) { index ->
                    val item = state.wrongItems[index]
                    WrongItemCard(index + 1, item)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                // Post-submit answer review: every question with 你的/正确答案.
                if (state.reviewItems.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.result_review_all),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(state.reviewItems.size) { index ->
                        ReviewItemCard(index + 1, state.reviewItems[index])
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.result_done))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WrongItemCard(index: Int, item: ResultWrongItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "$index. ${item.content}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${stringResource(R.string.result_your_answer)}：${item.yourAnswer}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            "${stringResource(R.string.result_correct_answer)}：${item.correctAnswer}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReviewItemCard(index: Int, item: ResultReviewItem) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "$index. ${item.content}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        val statusRes = when (item.isCorrect) {
            true -> R.string.result_review_correct
            false -> R.string.result_review_wrong
            null -> R.string.result_review_unanswered
        }
        val statusColor = when (item.isCorrect) {
            true -> io.github.coderirse.reps.ui.theme.successColor()
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            stringResource(statusRes) + (item.yourAnswer?.let { "：$it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
        )
        Text(
            "${stringResource(R.string.result_correct_answer)}：${item.correctAnswer}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatDuration(ms: Long): String = TimeFormat.duration(ms)
