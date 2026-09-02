package io.github.coderirse.reps.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.coderirse.reps.R
import io.github.coderirse.reps.core.CustomOrder
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.ui.components.CardChip
import io.github.coderirse.reps.ui.import.typeLabel
import kotlinx.coroutines.launch

/** Secondary config page behind each practice-mode entry on the home sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeConfigScreen(
    practiceType: String,
    onBack: () -> Unit,
    onSessionStarted: (Long) -> Unit,
    viewModel: PracticeConfigViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(practiceModeLabel(practiceType))
                        if (state.subjectName.isNotBlank()) {
                            Text(
                                state.subjectName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (state.poolSelectable) {
                ConfigSection(stringResource(R.string.config_section_pool)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PoolChoice.entries.forEach { pool ->
                            CardChip(
                                label = poolLabel(pool),
                                selected = state.pool == pool,
                            ) { viewModel.setPool(pool) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            ConfigSection(stringResource(R.string.config_section_quota)) {
                QuotaStepper(
                    label = typeLabel(QuestionType.SINGLE),
                    value = state.single,
                    max = state.singleMax,
                    onChange = { viewModel.setQuota(QuestionType.SINGLE, it) },
                )
                QuotaStepper(
                    label = typeLabel(QuestionType.MULTI),
                    value = state.multi,
                    max = state.multiMax,
                    onChange = { viewModel.setQuota(QuestionType.MULTI, it) },
                )
                QuotaStepper(
                    label = typeLabel(QuestionType.JUDGE),
                    value = state.judge,
                    max = state.judgeMax,
                    onChange = { viewModel.setQuota(QuestionType.JUDGE, it) },
                )
                Text(
                    stringResource(R.string.quota_total, state.total),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            ConfigSection(stringResource(R.string.config_section_timer)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.config_timer_enable),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.timed, onCheckedChange = { viewModel.setTimed(it) })
                }
                if (state.timed) {
                    QuotaStepper(
                        label = stringResource(R.string.config_timer_minutes, state.minutes),
                        value = state.minutes,
                        max = 240,
                        step = 5,
                        showMax = false,
                        onChange = { viewModel.setMinutes(it) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            ConfigSection(stringResource(R.string.config_section_order)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    SegmentedButton(
                        selected = state.order == CustomOrder.SEQUENTIAL,
                        onClick = { viewModel.setOrder(CustomOrder.SEQUENTIAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.order_sequential)) }
                    SegmentedButton(
                        selected = state.order == CustomOrder.RANDOM,
                        onClick = { viewModel.setOrder(CustomOrder.RANDOM) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.order_random)) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        viewModel.start()?.let(onSessionStarted)
                    }
                },
                enabled = state.total > 0 && !state.starting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.total > 0) R.string.config_start else R.string.config_pool_empty,
                    ),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfigSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun QuotaStepper(
    label: String,
    value: Int,
    max: Int,
    step: Int = 1,
    showMax: Boolean = true,
    min: Int = 0,
    onChange: (Int) -> Unit,
) {
    val decreaseLabel = stringResource(R.string.practice_stepper_decrease)
    val increaseLabel = stringResource(R.string.practice_stepper_increase)
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (showMax) {
                Text(
                    stringResource(R.string.quota_available, max),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onChange((value - step).coerceAtLeast(if (step > 1) step else min)) },
                enabled = value > min,
            ) {
                Text(
                    "−",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { contentDescription = decreaseLabel },
                )
            }
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { editing = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { contentDescription = increaseLabel },
                )
            }
        }
    }
    if (editing) {
        NumberInputDialog(
            initial = value,
            min = min,
            max = max,
            onConfirm = {
                onChange(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

/** Numeric keyboard input for a stepper value, clamped into [min, max]. */
@Composable
private fun NumberInputDialog(
    initial: Int,
    min: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.config_quota_input_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(stringResource(R.string.config_quota_input_hint, max))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    text.toIntOrNull()?.let { onConfirm(it.coerceIn(min, max)) } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun poolLabel(pool: PoolChoice): String = stringResource(
    when (pool) {
        PoolChoice.ALL -> R.string.pool_all
        PoolChoice.UNPRACTICED -> R.string.pool_unpracticed
        PoolChoice.WRONG -> R.string.pool_wrong
        PoolChoice.FAVORITE -> R.string.pool_favorite
    },
)

@Composable
fun practiceModeLabel(practiceType: String): String = stringResource(
    when (practiceType) {
        PracticeType.RECITE -> R.string.practice_recite
        PracticeType.EXAM -> R.string.practice_exam
        PracticeType.WRONG_BOOK -> R.string.practice_wrong_book
        PracticeType.FAVORITE -> R.string.practice_favorite
        else -> R.string.practice_sequential
    },
)
