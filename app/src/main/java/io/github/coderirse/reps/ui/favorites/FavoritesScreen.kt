package io.github.coderirse.reps.ui.favorites

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import io.github.coderirse.reps.data.db.dao.FavoriteRow
import io.github.coderirse.reps.ui.components.CardChip
import io.github.coderirse.reps.ui.components.EmptyState
import kotlinx.coroutines.launch

/** 收藏 Tab: favorites with subject filter and one-tap favorite practice. */
@Composable
fun FavoritesScreen(
    onOpenConfig: (Long) -> Unit,
    viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory),
) {
    val subjectFilter by viewModel.subjectFilter.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle(initialValue = null)
    val subjectIds by viewModel.subjectIds.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var subjectNames by remember { mutableStateOf(emptyMap<Long, String>()) }
    var startHint by remember { mutableStateOf<String?>(null) }
    val hintText = stringResource(R.string.favorites_pick_subject_hint)

    LaunchedEffect(startHint) {
        startHint?.let {
            snackbarHostState.showSnackbar(it)
            startHint = null
        }
    }
    LaunchedEffect(subjectIds) {
        subjectNames = subjectIds.associateWith { viewModel.getSubjectName(it) }
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
                        FavoriteItem(row)
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
                            startHint = hintText
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
}

@Composable
private fun FavoriteItem(row: FavoriteRow) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
