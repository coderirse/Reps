package io.github.coderirse.reps.ui.wrongbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.db.dao.WrongBookRow
import io.github.coderirse.reps.ui.components.EmptyState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 错题本: unmastered wrong answers with counts, manual mastery toggle and
 * one-tap wrong-book practice. Subject chips keep practice single-subject.
 */
@Composable
fun WrongBookScreen(
    onSessionStarted: (Long) -> Unit,
    viewModel: WrongBookViewModel = viewModel(factory = WrongBookViewModel.Factory),
) {
    val unmastered by viewModel.unmastered.collectAsStateWithLifecycle(initialValue = null)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var subjectFilter by remember { mutableStateOf<Long?>(null) }
    var subjectNames by remember { mutableStateOf(emptyMap<Long, String>()) }
    var showMastered by remember { mutableStateOf(false) }
    var startBlockedHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(startBlockedHint) {
        startBlockedHint?.let {
            snackbarHostState.showSnackbar(it)
            startBlockedHint = null
        }
    }

    val rows = unmastered
    val masteredRows = viewModel.mastered.collectAsStateWithLifecycle(initialValue = emptyList<WrongBookRow>()).value
    val filtered = rows
        ?.filter { subjectFilter == null || it.question.subjectId == subjectFilter }
        .orEmpty()
    val subjectIds = (rows.orEmpty() + masteredRows).map { it.question.subjectId }.distinct()

    LaunchedEffect(subjectIds) {
        subjectNames = subjectIds.associateWith { viewModel.getSubjectName(it) }
    }

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.tab_wrong_book),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
            if (subjectIds.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(label = stringResource(R.string.filter_all), selected = subjectFilter == null) {
                        subjectFilter = null
                    }
                    subjectIds.forEach { id ->
                        FilterChip(label = subjectNames[id] ?: "#$id", selected = subjectFilter == id) {
                            subjectFilter = id
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
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
            when {
                rows == null -> Unit
                rows.isEmpty() && masteredRows.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.CheckCircle,
                    title = stringResource(R.string.wrong_book_empty_title),
                    description = stringResource(R.string.wrong_book_empty_description),
                )
                showMastered -> LazyColumn(Modifier.weight(1f)) {
                    items(masteredRows.filter { subjectFilter == null || it.question.subjectId == subjectFilter }, key = { it.question.id }) { row ->
                        WrongBookItem(
                            row = row,
                            dateText = dateFormat.format(Date(row.lastWrongAt)),
                            toggleIcon = {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = stringResource(R.string.wrong_book_unmark_mastered),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
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
                            toggleIcon = {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = stringResource(R.string.wrong_book_mark_mastered),
                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                )
                            },
                            onToggle = { viewModel.setMastered(row.question.id, true) },
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                val target = subjectFilter ?: subjectIds.singleOrNull()
                                if (target == null) {
                                    startBlockedHint = "错题跨多个题库，请先在上方选择一个题库"
                                } else {
                                    scope.launch {
                                        viewModel.startPractice(target)?.let(onSessionStarted)
                                    }
                                }
                            },
                            enabled = filtered.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text(stringResource(R.string.wrong_book_start_practice)) }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
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
    toggleIcon: @Composable () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.question.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.wrong_book_meta, row.wrongCount, dateText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onToggle) { toggleIcon() }
        }
    }
}
