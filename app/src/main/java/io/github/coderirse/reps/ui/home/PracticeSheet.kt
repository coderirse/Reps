package io.github.coderirse.reps.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.coderirse.reps.R
import io.github.coderirse.reps.core.CustomOrder
import io.github.coderirse.reps.core.CustomQuota
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.ReciteMode

/** What the sheet hands back when the user taps 开始. */
data class PracticeStartRequest(
    val practiceType: String,
    val reciteMode: String,
    val filterDimension: String? = null,
    val filterValue: String? = null,
    val shuffle: Boolean = false,
    val customQuota: CustomQuota? = null,
    val customOrder: CustomOrder = CustomOrder.SEQUENTIAL,
    val deadlineMinutes: Int = 0,
)

private sealed interface SheetStep {
    data object SelectType : SheetStep
    data class ChooseMode(val practiceType: String, val filterDimension: String?, val filterValue: String?) : SheetStep
    data object ChooseFilter : SheetStep
    data object CustomConfig : SheetStep
}

/**
 * Practice picker shown for a tapped subject: type list -> (chapter/category
 * list | custom paper config) -> Mode A/B -> start.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PracticeSheet(
    subjectName: String,
    chapterCounts: List<Pair<String, Int>>,
    categoryCounts: List<Pair<String, Int>>,
    countsByType: Map<String, Int>,
    onStart: (PracticeStartRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf<SheetStep>(SheetStep.SelectType) }
    var shuffleForCategory by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                subjectName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            when (val current = step) {
                SheetStep.SelectType -> TypeList(
                    onStartSimple = { type ->
                        step = when (type) {
                            PracticeType.CATEGORY -> SheetStep.ChooseFilter
                            PracticeType.CUSTOM -> SheetStep.CustomConfig
                            else -> SheetStep.ChooseMode(type, null, null)
                        }
                    },
                )
                SheetStep.ChooseFilter -> FilterList(
                    chapterCounts = chapterCounts,
                    categoryCounts = categoryCounts,
                    onPick = { dimension, value ->
                        shuffleForCategory = false
                        step = SheetStep.ChooseMode(PracticeType.CATEGORY, dimension, value)
                    },
                    onBack = { step = SheetStep.SelectType },
                )
                is SheetStep.ChooseMode -> ModeChooser(
                    contextLabel = contextLabel(current, chapterCounts, categoryCounts),
                    showOrderToggle = current.practiceType == PracticeType.CATEGORY,
                    initialShuffle = shuffleForCategory,
                    onShuffleChange = { shuffleForCategory = it },
                    onStart = { mode ->
                        onStart(
                            PracticeStartRequest(
                                practiceType = current.practiceType,
                                reciteMode = mode,
                                filterDimension = current.filterDimension,
                                filterValue = current.filterValue,
                                shuffle = if (current.practiceType == PracticeType.CATEGORY) shuffleForCategory else current.practiceType == PracticeType.RANDOM,
                            ),
                        )
                    },
                    onBack = { step = SheetStep.SelectType },
                )
                SheetStep.CustomConfig -> CustomConfigForm(
                    countsByType = countsByType,
                    onStart = { request -> onStart(request) },
                    onBack = { step = SheetStep.SelectType },
                )
            }
        }
    }
}

private fun contextLabel(
    step: SheetStep.ChooseMode,
    chapterCounts: List<Pair<String, Int>>,
    categoryCounts: List<Pair<String, Int>>,
): String? {
    val value = step.filterValue ?: return null
    val source = when (step.filterDimension) {
        HomeViewModel.FILTER_CHAPTER -> chapterCounts
        HomeViewModel.FILTER_CATEGORY -> categoryCounts
        else -> null
    } ?: return null
    val count = source.firstOrNull { it.first == value }?.second ?: return null
    return "$value · $count 题"
}

@Composable
private fun TypeList(onStartSimple: (String) -> Unit) {
    val items = listOf(
        Triple(PracticeType.SEQUENTIAL, R.string.practice_sequential, true),
        Triple(PracticeType.RANDOM, R.string.practice_random, true),
        Triple(PracticeType.CATEGORY, R.string.practice_category, true),
        Triple(PracticeType.CUSTOM, R.string.practice_custom, true),
        Triple(PracticeType.WRONG_BOOK, R.string.practice_wrong_book, false),
        Triple(PracticeType.FAVORITE, R.string.practice_favorite, false),
    )
    items.forEach { (type, labelRes, enabled) ->
        ListItem(
            headlineContent = { Text(stringResource(labelRes)) },
            supportingContent = if (!enabled) {
                { Text(stringResource(R.string.practice_phase3_hint)) }
            } else {
                null
            },
            modifier = Modifier.clickable(enabled = enabled) { onStartSimple(type) },
        )
    }
}

@Composable
private fun FilterList(
    chapterCounts: List<Pair<String, Int>>,
    categoryCounts: List<Pair<String, Int>>,
    onPick: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    Text(stringResource(R.string.practice_pick_filter), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    if (chapterCounts.isNotEmpty()) {
        Text(
            stringResource(R.string.filter_chapters),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        chapterCounts.forEach { (value, count) ->
            ListItem(
                headlineContent = { Text(value) },
                trailingContent = { Text("$count 题") },
                modifier = Modifier.clickable { onPick(HomeViewModel.FILTER_CHAPTER, value) },
            )
        }
    }
    if (categoryCounts.isNotEmpty()) {
        Text(
            stringResource(R.string.filter_categories),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        categoryCounts.forEach { (value, count) ->
            ListItem(
                headlineContent = { Text(value) },
                trailingContent = { Text("$count 题") },
                modifier = Modifier.clickable { onPick(HomeViewModel.FILTER_CATEGORY, value) },
            )
        }
    }
    if (chapterCounts.isEmpty() && categoryCounts.isEmpty()) {
        Text(
            stringResource(R.string.filter_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
    TextButtonBack(onBack)
}

@Composable
private fun ModeChooser(
    contextLabel: String?,
    showOrderToggle: Boolean,
    initialShuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    onStart: (String) -> Unit,
    onBack: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(ReciteMode.TEST) }
    if (contextLabel != null) {
        Text(contextLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showOrderToggle) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.question_order), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            SegmentedButton(
                selected = !initialShuffle,
                onClick = { onShuffleChange(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.order_sequential)) }
            SegmentedButton(
                selected = initialShuffle,
                onClick = { onShuffleChange(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.order_random)) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.recite_mode), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    ModeSegmented(mode) { mode = it }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onStart(mode) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_start))
    }
    Spacer(Modifier.height(8.dp))
    TextButtonBack(onBack)
}

@Composable
private fun ModeSegmented(mode: String, onChange: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == ReciteMode.BROWSE,
            onClick = { onChange(ReciteMode.BROWSE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.mode_a_label)) }
        SegmentedButton(
            selected = mode == ReciteMode.TEST,
            onClick = { onChange(ReciteMode.TEST) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.mode_b_label)) }
    }
}

@Composable
private fun CustomConfigForm(
    countsByType: Map<String, Int>,
    onStart: (PracticeStartRequest) -> Unit,
    onBack: () -> Unit,
) {
    var single by rememberSaveable { mutableStateOf(0) }
    var multi by rememberSaveable { mutableStateOf(0) }
    var judge by rememberSaveable { mutableStateOf(0) }
    var order by rememberSaveable { mutableStateOf(CustomOrder.SEQUENTIAL) }
    var minutes by rememberSaveable { mutableStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(ReciteMode.TEST) }

    val singleMax = countsByType[QuestionType.SINGLE] ?: 0
    val multiMax = countsByType[QuestionType.MULTI] ?: 0
    val judgeMax = countsByType[QuestionType.JUDGE] ?: 0
    val total = single + multi + judge
    val timed = minutes > 0
    val effectiveMode = if (timed) ReciteMode.TEST else mode

    Stepper(stringResource(R.string.quota_single), single, singleMax) { single = it }
    Stepper(stringResource(R.string.quota_multi), multi, multiMax) { multi = it }
    Stepper(stringResource(R.string.quota_judge), judge, judgeMax) { judge = it }

    Text(
        stringResource(R.string.quota_total, total),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    Text(stringResource(R.string.question_order), style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SegmentedButton(
            selected = order == CustomOrder.SEQUENTIAL,
            onClick = { order = CustomOrder.SEQUENTIAL },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.order_sequential)) }
        SegmentedButton(
            selected = order == CustomOrder.RANDOM,
            onClick = { order = CustomOrder.RANDOM },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.order_random)) }
    }

    Text(stringResource(R.string.timer_minutes_hint), style = MaterialTheme.typography.titleSmall)
    Stepper(
        label = if (timed) stringResource(R.string.timer_minutes_value, minutes) else stringResource(R.string.timer_off),
        value = minutes,
        max = 180,
        step = 5,
        onChange = { minutes = it },
    )

    Text(stringResource(R.string.recite_mode), style = MaterialTheme.typography.titleSmall)
    if (timed) {
        Text(
            stringResource(R.string.timer_forces_mode_b),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(4.dp))
    if (timed) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = true,
                onClick = {},
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
            ) { Text(stringResource(R.string.mode_b_label)) }
        }
    } else {
        ModeSegmented(mode) { mode = it }
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            onStart(
                PracticeStartRequest(
                    practiceType = PracticeType.CUSTOM,
                    reciteMode = effectiveMode,
                    shuffle = order == CustomOrder.RANDOM,
                    customQuota = CustomQuota(single = single, multi = multi, judge = judge),
                    customOrder = order,
                    deadlineMinutes = minutes,
                ),
            )
        },
        enabled = total > 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.action_start))
    }
    Spacer(Modifier.height(8.dp))
    TextButtonBack(onBack)
}

@Composable
private fun Stepper(label: String, value: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.quota_available, max),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - step).coerceAtLeast(0)) }, enabled = value > 0) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TextButtonBack(onBack: () -> Unit) {
    Text(
        stringResource(R.string.action_back),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clickable(onClick = onBack),
    )
}
