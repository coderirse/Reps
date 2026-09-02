package io.github.coderirse.reps.ui.wrongbook

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.coderirse.reps.data.db.dao.SubjectWrongCount
import io.github.coderirse.reps.data.db.dao.WrongBookRow
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.ui.components.AssetImage
import io.github.coderirse.reps.ui.components.EmptyState
import io.github.coderirse.reps.ui.import.typeLabel
import io.github.coderirse.reps.ui.theme.onSuccessContainerColor
import io.github.coderirse.reps.ui.theme.successColor
import io.github.coderirse.reps.ui.theme.successContainerColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 错题本: unmastered wrong answers with counts, manual mastery toggle and
 * one-tap wrong-book practice. Tapping a card opens the full question with
 * its answer, explanation and note; when wrongs span several subjects the
 * practice button asks which subject to drill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongBookScreen(
    onOpenConfig: (Long) -> Unit,
    onSessionStarted: (Long) -> Unit,
    viewModel: WrongBookViewModel = viewModel(factory = WrongBookViewModel.Factory),
) {
    val unmastered by viewModel.unmastered.collectAsStateWithLifecycle(initialValue = null)
    val masteredRows by viewModel.mastered.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val noUnmasteredHint = stringResource(R.string.wrong_book_no_unmastered)
    var subjectFilter by remember { mutableStateOf<Long?>(null) }
    var subjectNames by remember { mutableStateOf(emptyMap<Long, String>()) }
    var showMastered by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var detailRow by remember { mutableStateOf<WrongBookRow?>(null) }
    var detailQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
    var detailNote by remember { mutableStateOf<String?>(null) }
    var subjectPicker by remember { mutableStateOf<List<SubjectWrongCount>?>(null) }

    LaunchedEffect(hint) {
        hint?.let {
            snackbarHostState.showSnackbar(it)
            hint = null
        }
    }

    // Detail sheet loads the freshest copy of the question plus its note.
    LaunchedEffect(detailRow?.question?.id) {
        val id = detailRow?.question?.id
        if (id == null) {
            detailQuestion = null
            detailNote = null
            return@LaunchedEffect
        }
        detailQuestion = viewModel.getQuestion(id)
        detailNote = viewModel.getNote(id)
    }

    val rows = unmastered
    val filtered = rows
        ?.filter { subjectFilter == null || it.question.subjectId == subjectFilter }
        .orEmpty()
    val subjectIds = (rows.orEmpty() + masteredRows).map { it.question.subjectId }.distinct()

    LaunchedEffect(subjectIds) {
        subjectNames = subjectIds.associateWith { viewModel.getSubjectName(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Opaque header: the list used to scroll straight through the chips.
            Surface(color = MaterialTheme.colorScheme.background) {
                Column {
                    Text(
                        stringResource(R.string.tab_wrong_book),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                    if (subjectIds.size > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                label = stringResource(R.string.filter_all),
                                selected = subjectFilter == null,
                            ) { subjectFilter = null }
                            subjectIds.forEach { id ->
                                FilterChip(
                                    label = subjectNames[id] ?: "#$id",
                                    selected = subjectFilter == id,
                                ) { subjectFilter = id }
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            label = stringResource(R.string.wrong_book_tab_unmastered),
                            selected = !showMastered,
                        ) { showMastered = false }
                        FilterChip(
                            label = stringResource(R.string.wrong_book_tab_mastered, masteredRows.size),
                            selected = showMastered,
                        ) { showMastered = true }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            when {
                rows == null -> Unit
                rows.isEmpty() && masteredRows.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.CheckCircle,
                    title = stringResource(R.string.wrong_book_empty_title),
                    description = stringResource(R.string.wrong_book_empty_description),
                )
                showMastered -> LazyColumn(Modifier.weight(1f)) {
                    items(
                        masteredRows.filter { subjectFilter == null || it.question.subjectId == subjectFilter },
                        key = { it.question.id },
                    ) { row ->
                        WrongBookItem(
                            row = row,
                            dateText = dateFormat.format(Date(row.lastWrongAt)),
                            mastered = true,
                            onOpenDetail = { detailRow = row },
                            onToggle = { viewModel.setMastered(row.question.id, false) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.question.id }) { row ->
                        WrongBookItem(
                            row = row,
                            dateText = dateFormat.format(Date(row.lastWrongAt)),
                            mastered = false,
                            onOpenDetail = { detailRow = row },
                            onToggle = { viewModel.setMastered(row.question.id, true) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
            // Pinned to the bottom: long wrong lists used to bury this button.
            if (!showMastered) {
                Button(
                    onClick = {
                        val target = subjectFilter
                        if (target != null) {
                            onOpenConfig(target)
                        } else {
                            scope.launch {
                                val counts = viewModel.getUnmasteredCountsBySubject()
                                when {
                                    counts.isEmpty() -> hint = noUnmasteredHint
                                    counts.size == 1 -> onOpenConfig(counts.first().subjectId)
                                    else -> subjectPicker = counts
                                }
                            }
                        }
                    },
                    enabled = filtered.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.wrong_book_start_practice)) }
            }
        }
    }

    subjectPicker?.let { counts ->
        AlertDialog(
            onDismissRequest = { subjectPicker = null },
            title = { Text(stringResource(R.string.wrong_book_pick_subject)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    counts.forEach { entry ->
                        TextButton(
                            onClick = {
                                subjectPicker = null
                                onOpenConfig(entry.subjectId)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    subjectNames[entry.subjectId] ?: "#${entry.subjectId}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.wrong_book_subject_meta, entry.count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { subjectPicker = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    detailRow?.let { row ->
        WrongQuestionDetailSheet(
            question = detailQuestion ?: row.question,
            wrongCount = row.wrongCount,
            lastWrongText = dateFormat.format(Date(row.lastWrongAt)),
            mastered = row.mastered,
            initialNote = detailNote,
            onSaveNote = { viewModel.saveNote(row.question.id, it) },
            onToggleMastered = {
                viewModel.setMastered(row.question.id, !row.mastered)
                detailRow = null
            },
            onPracticeThis = {
                detailRow = null
                scope.launch { viewModel.startSingleQuestionPractice(row.question.id)?.let(onSessionStarted) }
            },
            onDismiss = { detailRow = null },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun WrongBookItem(
    row: WrongBookRow,
    dateText: String,
    mastered: Boolean,
    onOpenDetail: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpenDetail),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.question.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Red is reserved for the wrong count; the date is metadata.
                        Text(
                            stringResource(R.string.wrong_book_wrong_count, row.wrongCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.wrong_book_last_wrong, dateText),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (mastered) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(
                            if (mastered) R.string.wrong_book_unmark_mastered else R.string.wrong_book_mark_mastered,
                        ),
                        tint = if (mastered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(
                            if (mastered) R.string.wrong_book_action_mastered else R.string.wrong_book_action_master,
                        ),
                    )
                }
            }
        }
    }
}

/** Read-only question view: options, correct answer, explanation and note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WrongQuestionDetailSheet(
    question: QuestionEntity,
    wrongCount: Int,
    lastWrongText: String,
    mastered: Boolean,
    /** null while the note is still loading; the editor renders only when loaded. */
    initialNote: String?,
    onSaveNote: (String) -> Unit,
    onToggleMastered: () -> Unit,
    onPracticeThis: () -> Unit,
    onDismiss: () -> Unit,
) {
    val success = successColor()
    val successContainer = successContainerColor()
    val onSuccessContainer = onSuccessContainerColor()

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
    val correctLetters: Set<String> = when (question.type) {
        QuestionType.MULTI -> question.correctAnswer.split(",").toSet()
        else -> setOf(question.correctAnswer)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.wrong_book_detail_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.wrong_book_wrong_count, wrongCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(R.string.wrong_book_last_wrong, lastWrongText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(question.content, style = MaterialTheme.typography.titleMedium)
            question.imageFile?.let { path ->
                Spacer(Modifier.height(12.dp))
                AssetImage(assetPath = path)
            }
            Spacer(Modifier.height(12.dp))

            options.forEach { (letter, text) ->
                val value = letter ?: text
                val isCorrect = value in correctLetters || (letter != null && letter in correctLetters)
                DetailOptionRow(
                    letter = letter,
                    text = text,
                    containerColor = if (isCorrect) successContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (isCorrect) {
                        onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onToggleMastered, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (mastered) Icons.Outlined.CheckCircle else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (mastered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(
                            if (mastered) R.string.wrong_book_unmark_mastered else R.string.wrong_book_mark_mastered,
                        ),
                    )
                }
                Button(onClick = onPracticeThis, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wrong_book_practice_this))
                }
            }
        }
    }
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
