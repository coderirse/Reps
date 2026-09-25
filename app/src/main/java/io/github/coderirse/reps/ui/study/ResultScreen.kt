package io.github.coderirse.reps.ui.study

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.coderirse.reps.R
import io.github.coderirse.reps.core.TimeFormat
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.ui.theme.onSuccessContainerColor
import io.github.coderirse.reps.ui.theme.successColor
import io.github.coderirse.reps.ui.theme.successContainerColor

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
    ExpandableReviewCard(index = index, content = item.content, statusColor = MaterialTheme.colorScheme.error) {
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
        ReviewOptions(question = item.question, selected = item.yourAnswer)
    }
}

@Composable
private fun ReviewItemCard(index: Int, item: ResultReviewItem) {
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
    ExpandableReviewCard(index = index, content = item.content, statusColor = statusColor) {
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
        ReviewOptions(question = item.question, selected = item.yourAnswer)
    }
}

/** Tap to expand: full options with the key highlighted plus the explanation. */
@Composable
private fun ExpandableReviewCard(
    index: Int,
    content: String,
    statusColor: Color,
    body: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$index. $content",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.result_review_collapse else R.string.result_review_expand,
                ),
                tint = statusColor,
            )
        }
        Spacer(Modifier.height(4.dp))
        body()
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Option list with the correct key in the success container; the user's pick gets a ✓. */
@Composable
private fun ReviewOptions(question: QuestionEntity, selected: String?) {
    val successContainer = successContainerColor()
    val onSuccessContainer = onSuccessContainerColor()
    val correctLetters: Set<String> = when (question.type) {
        QuestionType.MULTI -> question.correctAnswer.split(",").toSet()
        else -> setOf(question.correctAnswer)
    }
    val selectedLetters: Set<String> = selected?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    val options: List<Pair<String?, String>> = when (question.type) {
        // Judge labels double as stored answer values (see judge_option_true).
        QuestionType.JUDGE -> listOf(
            null to stringResource(R.string.judge_option_true),
            null to stringResource(R.string.judge_option_false),
        )
        else -> listOfNotNull(
            question.optionA?.let { "A" to it },
            question.optionB?.let { "B" to it },
            question.optionC?.let { "C" to it },
            question.optionD?.let { "D" to it },
            question.optionE?.let { "E" to it },
            question.optionF?.let { "F" to it },
        )
    }
    options.forEach { (letter, text) ->
        val value = letter ?: text
        val isCorrect = value in correctLetters
        val isPicked = value in selectedLetters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isCorrect) successContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            letter?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCorrect) onSuccessContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isPicked) {
                Text(
                    "✓",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isCorrect) onSuccessContainer else MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    question.explanation?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(ms: Long): String = TimeFormat.duration(ms)
