package io.github.coderirse.reps.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.db.dao.FavoriteRow
import io.github.coderirse.reps.data.db.entity.QuestionEntity
import io.github.coderirse.reps.ui.components.CardChip
import io.github.coderirse.reps.ui.components.EmptyState
import io.github.coderirse.reps.ui.components.QuestionDetailSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

/** 收藏 Tab: favorites with subject filter, detail sheet and one-tap practice. */
@Composable
fun FavoritesScreen(
    onOpenConfig: (Long) -> Unit,
    onSessionStarted: (Long) -> Unit,
    viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory),
) {
    val subjectFilter by viewModel.subjectFilter.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle(initialValue = null)
    val subjectIds by viewModel.subjectIds.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var subjectNames by remember { mutableStateOf(emptyMap<Long, String>()) }
    var subjectPicker by remember { mutableStateOf<List<Long>?>(null) }
    var detailRow by remember { mutableStateOf<FavoriteRow?>(null) }
    var detailQuestion by remember { mutableStateOf<QuestionEntity?>(null) }
    var detailNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(subjectIds) {
        subjectNames = subjectIds.associateWith { viewModel.getSubjectName(it) }
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

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.tab_favorites),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
            if (subjectIds.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardChip(label = stringResource(R.string.filter_all), selected = subjectFilter == null) {
                        viewModel.setSubjectFilter(null)
                    }
                    subjectIds.forEach { id ->
                        CardChip(label = subjectNames[id] ?: "#$id", selected = subjectFilter == id) {
                            viewModel.setSubjectFilter(id)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            val currentRows = rows
            when {
                currentRows == null -> Unit
                currentRows.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(R.string.favorites_empty_title),
                    description = stringResource(R.string.favorites_empty_description),
                )
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(currentRows, key = { it.question.id }) { row ->
                        FavoriteItem(row, onOpenDetail = { detailRow = row })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
            // Pinned to the bottom instead of scrolling with the list.
            if (!currentRows.isNullOrEmpty()) {
                Button(
                    onClick = {
                        val target = subjectFilter ?: subjectIds.singleOrNull()
                        if (target == null) {
                            // Same active hand-off as the wrong book: pick a
                            // subject in a dialog instead of a passive hint.
                            subjectPicker = subjectIds
                        } else {
                            onOpenConfig(target)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.favorites_start_practice)) }
            }
        }
    }

    subjectPicker?.let { ids ->
        AlertDialog(
            onDismissRequest = { subjectPicker = null },
            title = { Text(stringResource(R.string.favorites_pick_subject)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ids.forEach { id ->
                        TextButton(
                            onClick = {
                                subjectPicker = null
                                onOpenConfig(id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                subjectNames[id] ?: "#$id",
                                style = MaterialTheme.typography.bodyLarge,
                            )
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
        QuestionDetailSheet(
            question = detailQuestion ?: row.question,
            title = stringResource(R.string.favorites_detail_title),
            onDismiss = { detailRow = null },
            initialNote = detailNote,
            onSaveNote = { viewModel.saveNote(row.question.id, it) },
            actions = {
                TextButton(
                    onClick = {
                        detailRow = null
                        viewModel.removeFavorite(row.question.id)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.favorite_remove))
                }
                Button(
                    onClick = {
                        detailRow = null
                        scope.launch { viewModel.startSingleQuestionPractice(row.question.id)?.let(onSessionStarted) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.wrong_book_practice_this))
                }
            },
        )
    }
}

@Composable
private fun FavoriteItem(row: FavoriteRow, onOpenDetail: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpenDetail),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                row.question.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    row.question.chapter?.takeIf { it.isNotBlank() },
                    row.question.category?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
