package io.github.coderirse.reps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.ui.import.typeLabel
import io.github.coderirse.reps.ui.theme.onSuccessContainerColor
import io.github.coderirse.reps.ui.theme.successColor
import io.github.coderirse.reps.ui.theme.successContainerColor

/**
 * Shared read-only question sheet (options with the key highlighted, answer,
 * explanation, note editor) used by the wrong-book and favorites detail views.
 * Callers inject their own header meta and bottom action row via slots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailSheet(
    question: QuestionEntity,
    title: String,
    onDismiss: () -> Unit,
    /** Extra line(s) under the title, e.g. wrong count / last-wrong date. */
    meta: (@Composable () -> Unit)? = null,
    /** null while the note is still loading; the editor renders only when loaded. */
    initialNote: String?,
    onSaveNote: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            meta?.invoke()
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                AssetImage(assetPath = path)
            }
            Spacer(Modifier.height(12.dp))

            val correctLetters: Set<String> = when (question.type) {
                QuestionType.MULTI -> question.correctAnswer.split(",").toSet()
                else -> setOf(question.correctAnswer)
            }
            optionsOf(question).forEach { (letter, text) ->
                val value = letter ?: text
                val isCorrect = value in correctLetters || (letter != null && letter in correctLetters)
                DetailOptionRow(
                    letter = letter,
                    text = text,
                    containerColor = if (isCorrect) successContainerColor() else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (isCorrect) onSuccessContainerColor() else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.study_answer_label, question.correctAnswer),
                        style = MaterialTheme.typography.titleSmall,
                        color = successColor(),
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

            Spacer(Modifier.height(12.dp))
            // Gate on the async load: initializing the field with "" before the
            // real note arrives would show an empty editor, and saving then
            // silently deleted the existing note (review H4).
            initialNote?.let { loadedNote ->
                var note by remember(question.id) { mutableStateOf(loadedNote) }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.study_note)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onSaveNote(note) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.study_note_save))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = actions,
            )
        }
    }
}

/** Judge labels double as stored answer values (see judge_option_true). */
@Composable
private fun optionsOf(question: QuestionEntity): List<Pair<String?, String>> = when (question.type) {
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

@Composable
private fun DetailOptionRow(
    letter: String?,
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(12.dp))
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
        Text(text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}
