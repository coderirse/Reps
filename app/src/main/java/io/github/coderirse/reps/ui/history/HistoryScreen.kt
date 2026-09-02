package io.github.coderirse.reps.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.coderirse.reps.R
import io.github.coderirse.reps.RepsApplication
import io.github.coderirse.reps.core.TimeFormat
import io.github.coderirse.reps.data.db.RepsDatabase
import io.github.coderirse.reps.data.db.dao.HistoryRow
import io.github.coderirse.reps.ui.components.EmptyState
import io.github.coderirse.reps.ui.home.practiceModeLabel
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(db: RepsDatabase) : ViewModel() {

    /** Completed sessions (交卷), newest first, with graded stats. */
    val history: Flow<List<HistoryRow>> = db.studySessionDao().observeHistory()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RepsApplication
                HistoryViewModel(app.database)
            }
        }
    }
}

/** 历史 Tab: every submitted session, tap to reopen its result page. */
@Composable
fun HistoryScreen(
    onOpenResult: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val rows by viewModel.history.collectAsStateWithLifecycle(initialValue = null)
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.tab_history),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        val current = rows
        when {
            current == null -> Unit
            current.isEmpty() -> EmptyState(
                icon = Icons.Filled.History,
                title = stringResource(R.string.history_empty_title),
                description = stringResource(R.string.history_empty_description),
            )
            else -> LazyColumn(Modifier.weight(1f)) {
                items(current, key = { it.session.id }) { row ->
                    HistoryCard(row, dateFormat, onOpenResult)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    row: HistoryRow,
    dateFormat: SimpleDateFormat,
    onOpenResult: (Long) -> Unit,
) {
    val session = row.session
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onOpenResult(session.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    practiceModeLabel(session.practiceType),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    stringResource(
                        R.string.history_stat,
                        row.correct,
                        row.answered.coerceAtLeast(0),
                        TimeFormat.duration(session.accumulatedMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    dateFormat.format(Date(session.lastActiveAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
