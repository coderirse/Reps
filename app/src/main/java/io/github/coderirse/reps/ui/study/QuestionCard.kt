package io.github.coderirse.reps.ui.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.ReciteMode
import io.github.coderirse.reps.ui.import.typeLabel
import io.github.coderirse.reps.ui.theme.onSuccessContainerColor
import io.github.coderirse.reps.ui.theme.successColor
import io.github.coderirse.reps.ui.theme.successContainerColor

private data class OptionVisual(
    val value: String,
    val letter: String?,
    val container: Color,
    val content: Color,
    val clickable: Boolean,
    val checked: Boolean,
)

@Composable
fun QuestionCard(
    question: QuestionEntity,
    mode: String,
    ui: QuestionUiState,
    multiTemp: Set<String>,
    onOptionTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 考试模式: 选中只标记不判色，交卷前可改答案. */
    examMode: Boolean = false,
) {
    val success = successColor()
    val onSuccessContainer = onSuccessContainerColor()
    val successContainer = successContainerColor()
    val wrongContainer = MaterialTheme.colorScheme.errorContainer
    val wrong = MaterialTheme.colorScheme.error

    // Light haptic + once-per-grade guard: buzz only on the transition into
    // graded state, not when swiping back to an already graded question.
    val haptics = LocalHapticFeedback.current
    var wasGraded by remember(question.id) { mutableStateOf(ui.graded) }
    LaunchedEffect(ui.graded) {
        if (ui.graded && !wasGraded) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasGraded = ui.graded
    }

    val options: List<Pair<String?, String>> = when (question.type) {
        // Judge labels double as stored answer values; the resources must
        // stay identical to Grading.normalizeJudge output.
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

    val correctLetters: Set<String> = when (question.type) {
        QuestionType.JUDGE -> setOf(question.correctAnswer)
        QuestionType.MULTI -> question.correctAnswer.split(",").toSet()
        else -> setOf(question.correctAnswer)
    }
    val selectedLetters: Set<String> = when {
        // Re-editing an answered multi (exam mode): show the in-progress picks.
        question.type == QuestionType.MULTI && multiTemp.isNotEmpty() -> multiTemp
        ui.answered && question.type == QuestionType.MULTI -> (ui.selectedAnswer ?: "").split(",").toSet()
        ui.answered -> setOf(ui.selectedAnswer ?: "")
        else -> multiTemp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                typeLabel(question.type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            question.chapter?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            question.category?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(question.content, style = MaterialTheme.typography.titleMedium)
        question.imageFile?.let { path ->
            Spacer(Modifier.height(12.dp))
            io.github.coderirse.reps.ui.components.AssetImage(
                assetPath = path,
                // Cap the height so image questions fit one screen together
                // with all options; tap still opens the full-size preview.
                maxHeight = 200.dp,
            )
        }
        Spacer(Modifier.height(16.dp))

        options.forEach { (letter, text) ->
            val value = letter ?: text
            val isCorrectOption = value in correctLetters || letter in correctLetters
            val isSelected = value in selectedLetters || (letter != null && letter in selectedLetters)
            val visual = when {
                // Exam: show the pick as plain selection, never right/wrong.
                examMode && isSelected -> OptionVisual(
                    value, letter, MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    clickable = mode == ReciteMode.TEST, checked = true,
                )
                examMode -> OptionVisual(
                    value, letter, MaterialTheme.colorScheme.surfaceContainerLow,
                    MaterialTheme.colorScheme.onSurface, clickable = mode == ReciteMode.TEST, checked = false,
                )
                ui.graded && isCorrectOption -> OptionVisual(
                    value, letter, successContainer, onSuccessContainer,
                    clickable = false, checked = isSelected,
                )
                ui.graded && isSelected -> OptionVisual(
                    value, letter, wrongContainer, wrong, clickable = false, checked = true,
                )
                ui.graded -> OptionVisual(
                    value, letter, MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant, clickable = false, checked = false,
                )
                // 看答案子模式: revealed without grading — highlight the key.
                !examMode && ui.revealed && !ui.graded && isCorrectOption -> OptionVisual(
                    value, letter, successContainer, onSuccessContainer,
                    clickable = false, checked = false,
                )
                !examMode && ui.revealed && !ui.graded -> OptionVisual(
                    value, letter, MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant, clickable = false, checked = false,
                )
                isSelected -> OptionVisual(
                    value, letter, MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    clickable = mode == ReciteMode.TEST, checked = true,
                )
                else -> OptionVisual(
                    value, letter, MaterialTheme.colorScheme.surfaceContainerLow,
                    MaterialTheme.colorScheme.onSurface, clickable = mode == ReciteMode.TEST, checked = false,
                )
            }
            OptionRow(
                letter = letter,
                text = text,
                visual = visual,
                showCheckbox = question.type == QuestionType.MULTI && !ui.graded,
                onTap = { if (visual.clickable) onOptionTap(value) },
            )
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(
            visible = ui.revealed,
            enter = expandVertically() + fadeIn(),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    if (ui.graded && ui.isCorrect != null) {
                        Text(
                            stringResource(
                                if (ui.isCorrect) R.string.study_correct_tag else R.string.study_wrong_tag,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ui.isCorrect) success else MaterialTheme.colorScheme.error,
                        )
                        if (!ui.isCorrect && ui.selectedAnswer != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.study_your_answer_prefix, ui.selectedAnswer),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        stringResource(R.string.study_answer_label, question.correctAnswer),
                        style = MaterialTheme.typography.titleSmall,
                        color = success,
                    )
                    question.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OptionRow(
    letter: String?,
    text: String,
    visual: OptionVisual,
    showCheckbox: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.container, RoundedCornerShape(12.dp))
            .clickable(enabled = visual.clickable, onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        letter?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(visual.value.takeIf { letter == null } ?: text, style = MaterialTheme.typography.bodyLarge, color = visual.content, modifier = Modifier.weight(1f))
        if (showCheckbox) {
            Checkbox(checked = visual.checked, onCheckedChange = null)
        }
    }
}
