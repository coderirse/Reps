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
import io.github.coderirse.reps.ui.theme.LocalRepsDarkTheme
import io.github.coderirse.reps.ui.theme.SuccessDark
import io.github.coderirse.reps.ui.theme.SuccessLight

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
) {
    val dark = LocalRepsDarkTheme.current
    val success = if (dark) SuccessDark else SuccessLight
    val successContainer = if (dark) Color(0xFF1B3A1F) else Color(0xFFC8E6C9)
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
        QuestionType.JUDGE -> listOf(null to "对", null to "错")
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
        ui.graded && question.type == QuestionType.MULTI -> (ui.selectedAnswer ?: "").split(",").toSet()
        ui.graded -> setOf(ui.selectedAnswer ?: "")
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
                contentDescription = null,
            )
        }
        Spacer(Modifier.height(16.dp))

        options.forEach { (letter, text) ->
            val value = letter ?: text
            val isCorrectOption = value in correctLetters || letter in correctLetters
            val isSelected = value in selectedLetters || (letter != null && letter in selectedLetters)
            val visual = when {
                ui.graded && isCorrectOption -> OptionVisual(
                    value, letter, successContainer, Color(0xFF1B5E20).takeIf { !dark } ?: Color(0xFFB9F6CA),
                    clickable = false, checked = isSelected,
                )
                ui.graded && isSelected -> OptionVisual(
                    value, letter, wrongContainer, wrong, clickable = false, checked = true,
                )
                ui.graded -> OptionVisual(
                    value, letter, MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant, clickable = false, checked = false,
                )
                ui.actionType == io.github.coderirse.reps.data.db.entity.AnswerActionType.BROWSED -> OptionVisual(
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
            Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        question.explanation?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.study_no_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            .padding(horizontal = 12.dp, vertical = 12.dp),
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
