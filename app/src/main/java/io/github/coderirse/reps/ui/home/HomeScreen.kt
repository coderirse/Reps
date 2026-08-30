package io.github.coderirse.reps.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.db.entity.SubjectEntity
import io.github.coderirse.reps.ui.components.EmptyState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 题库 Tab: empty state with import CTA, or the imported subject list.
 * Tapping a subject opens the practice picker sheet.
 */
@Composable
fun HomeScreen(
    snackbarMessage: String?,
    onSnackbarShown: () -> Unit,
    onOpenImportPreview: (android.net.Uri) -> Unit,
    onStartSession: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle(initialValue = null)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            onSnackbarShown()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onOpenImportPreview)
    }

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        val currentSubjects = subjects
        when {
            currentSubjects == null -> Unit
            currentSubjects.isEmpty() -> EmptyState(
                icon = Icons.Filled.AddCircle,
                title = stringResource(R.string.home_empty_title),
                description = stringResource(R.string.home_empty_description),
                modifier = Modifier.align(Alignment.Center),
                action = {
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.home_import_button))
                    }
                },
            )
            else -> SubjectList(
                subjects = currentSubjects,
                onImportClick = { filePicker.launch(arrayOf("*/*")) },
                onStartSession = onStartSession,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                scope = scope,
            )
        }
    }
}

@Composable
private fun SubjectList(
    subjects: List<SubjectEntity>,
    onImportClick: () -> Unit,
    onStartSession: (Long) -> Unit,
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var sheetSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<SubjectEntity?>(null) }
    var chapterCounts by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    var categoryCounts by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    var countsByType by remember { mutableStateOf(emptyMap<String, Int>()) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Load sheet context data whenever a subject is tapped.
    LaunchedEffect(sheetSubject?.id) {
        sheetSubject?.let { subject ->
            chapterCounts = viewModel.chapterCounts(subject.id).map { it.value to it.count }
            categoryCounts = viewModel.categoryCounts(subject.id).map { it.value to it.count }
            countsByType = viewModel.countsByType(subject.id)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.tab_library),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(subjects, key = { it.id }) { subject ->
                SubjectCard(
                    subject = subject,
                    dateText = dateFormat.format(Date(subject.createdAt)),
                    onTap = { sheetSubject = subject },
                    onDelete = { deleteCandidate = subject },
                )
            }
            item {
                Button(
                    onClick = onImportClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.home_import_button)) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    sheetSubject?.let { subject ->
        PracticeSheet(
            subjectName = subject.name,
            chapterCounts = chapterCounts,
            categoryCounts = categoryCounts,
            countsByType = countsByType,
            onStart = { request ->
                val subjectId = subject.id
                sheetSubject = null
                scope.launch {
                    val sessionId = viewModel.startPractice(
                        subjectId = subjectId,
                        practiceType = request.practiceType,
                        reciteMode = request.reciteMode,
                        filterDimension = request.filterDimension,
                        filterValue = request.filterValue,
                        shuffle = request.shuffle,
                        customQuota = request.customQuota,
                        customOrder = request.customOrder,
                        deadlineMinutes = request.deadlineMinutes,
                    )
                    onStartSession(sessionId)
                }
            },
            onDismiss = { sheetSubject = null },
        )
    }

    deleteCandidate?.let { subject ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.dialog_delete_subject_title)) },
            text = { Text(stringResource(R.string.dialog_delete_subject_text, subject.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val name = subject.name
                    viewModel.deleteSubject(subject.id)
                    deleteCandidate = null
                    scope.launch { snackbarHostState.showSnackbar("已删除「$name」") }
                }) { Text(stringResource(R.string.dialog_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun SubjectCard(
    subject: SubjectEntity,
    dateText: String,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onTap),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.subject_meta, subject.questionCount, dateText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
