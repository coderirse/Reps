package io.github.coderirse.reps.ui.study

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.QuestionType
import io.github.coderirse.reps.data.db.entity.ReciteMode
import io.github.coderirse.reps.data.prefs.ThemeMode
import io.github.coderirse.reps.ui.theme.LocalRepsDarkTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    sessionId: Long,
    onClose: () -> Unit,
    onSessionFinished: (Long) -> Unit,
    onSessionRestart: (Long) -> Unit,
    viewModel: StudyViewModel = viewModel(factory = StudyViewModel.create(sessionId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = io.github.coderirse.reps.data.prefs.UserSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    var showAnswerCard by remember { mutableStateOf(false) }
    var showPracticeMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var pendingPracticeType by remember { mutableStateOf<String?>(null) }
    val dark = LocalRepsDarkTheme.current

    // ON_STOP fallback save runs on the application scope (docs section 5.2).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.saveNow()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Timed session hit zero (or was already expired): leave for the result page.
    LaunchedEffect(state.sessionCompleted) {
        if (state.sessionCompleted) onSessionFinished(sessionId)
    }

    if (state.loading) {
        Scaffold { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        }
        return
    }
    if (state.loadError != null) {
        Scaffold { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            }
        }
        return
    }

    val questions = state.questions
    val pagerState = rememberPagerState(pageCount = { questions.size })

    // VM state drives the pager (answer-card jumps).
    LaunchedEffect(state.currentIndex) {
        if (pagerState.currentPage != state.currentIndex) {
            pagerState.scrollToPage(state.currentIndex)
        }
    }
    // Swipe navigation feeds back into the VM (persists the position left).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            viewModel.onIndexChange(page)
        }
    }

    val currentQuestion = questions.getOrNull(state.currentIndex)
    val currentUi = currentQuestion?.let { state.perQuestion[it.id] }
    val timed = state.remainingMs != null
    val ungradedCount = questions.count { state.perQuestion[it.id]?.graded != true }
    val remainingText = state.remainingMs?.let { ms ->
        val totalSec = (ms / 1000).coerceAtLeast(0)
        "%02d:%02d".format(totalSec / 60, totalSec % 60)
    }
    // Multi-choice pending confirmation: the primary button becomes 确认答案.
    val multiPending = currentQuestion != null &&
        currentQuestion.type == QuestionType.MULTI &&
        state.reciteMode == ReciteMode.TEST &&
        currentUi?.graded != true &&
        state.multiTemp[currentQuestion.id].orEmpty().isNotEmpty()

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Top bar row 1: back / subject / countdown / overflow menu
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.subjectName.ifBlank { stringResource(R.string.app_name) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        "${state.currentIndex + 1}/${questions.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (timed) {
                    Text(
                        remainingText.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if ((state.remainingMs ?: 0) < 60_000) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.study_more))
                }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.study_dark_menu)) },
                        // Quick toggle kept inside the study flow; the source of
                        // truth remains the settings page. Row 1 previously had
                        // an inline "深色" Switch that permanently overrode the
                        // system preference and crowded the bar.
                        trailingIcon = {
                            Switch(
                                checked = dark,
                                onCheckedChange = { value ->
                                    viewModel.setThemeMode(if (value) ThemeMode.DARK else ThemeMode.LIGHT)
                                },
                            )
                        },
                        onClick = {
                            viewModel.setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
                        },
                    )
                }
            }

            // Row 2: practice-type dropdown + Mode A/B switcher + favorite/note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = { showPracticeMenu = true }) {
                    Text(practiceTypeLabel(state.practiceType))
                }
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = state.reciteMode == ReciteMode.BROWSE,
                        onClick = { viewModel.onReciteModeChange(ReciteMode.BROWSE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.study_mode_a)) }
                    SegmentedButton(
                        selected = state.reciteMode == ReciteMode.TEST,
                        onClick = { viewModel.onReciteModeChange(ReciteMode.TEST) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.study_mode_b)) }
                }
                currentQuestion?.let { question ->
                    val isFavorite = question.id in state.favorites
                    IconButton(onClick = { viewModel.toggleFavorite(question.id) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.study_favorite),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showNoteDialog = true }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.study_note),
                            tint = if (state.notes.containsKey(question.id)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = {
                    if (questions.isEmpty()) 0f else (state.currentIndex + 1f) / questions.size
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                questions.getOrNull(page)?.let { question ->
                    QuestionCard(
                        question = question,
                        mode = state.reciteMode,
                        ui = state.perQuestion[question.id] ?: QuestionUiState(),
                        multiTemp = state.multiTemp[question.id].orEmpty(),
                        onOptionTap = { value -> viewModel.onOptionTap(question, value) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Bottom bar: prev / answer-card / spacer / (submit) / (confirm|next)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(state.currentIndex - 1) } },
                    enabled = state.currentIndex > 0,
                ) { Text(stringResource(R.string.study_prev)) }
                TextButton(onClick = { showAnswerCard = true }) {
                    Text(stringResource(R.string.study_answer_card))
                }
                Spacer(Modifier.weight(1f))
                // Submit is available in EVERY mode: untimed sessions used to
                // stay ACTIVE forever (restore dialog kept reappearing).
                TextButton(onClick = { showSubmitDialog = true }) {
                    Text(stringResource(R.string.study_submit))
                }
                Button(
                    onClick = {
                        val question = currentQuestion
                        when {
                            multiPending && question != null ->
                                viewModel.onConfirmMulti(question)
                            else ->
                                scope.launch { pagerState.animateScrollToPage(state.currentIndex + 1) }
                        }
                    },
                    enabled = if (multiPending) true else state.currentIndex < questions.size - 1,
                ) {
                    Text(
                        stringResource(
                            if (multiPending) R.string.study_confirm_multi else R.string.study_next,
                        ),
                    )
                }
            }
        }

        if (showAnswerCard) {
            AnswerCardSheet(
                total = questions.size,
                currentIndex = state.currentIndex,
                perQuestion = state.perQuestion,
                questionIdAt = { index -> questions.getOrNull(index)?.id },
                onJump = { index ->
                    showAnswerCard = false
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                onDismiss = { showAnswerCard = false },
            )
        }

        DropdownMenu(expanded = showPracticeMenu, onDismissRequest = { showPracticeMenu = false }) {
            // Only the two switchable types; the rest (专项/自定义/错题/收藏)
            // need their own configuration and live on the practice sheet —
            // permanently disabled menu items read as "broken".
            listOf(
                PracticeType.SEQUENTIAL to false,
                PracticeType.RANDOM to true,
            ).forEach { (type, shuffle) ->
                DropdownMenuItem(
                    text = { Text(practiceTypeLabel(type)) },
                    onClick = {
                        showPracticeMenu = false
                        pendingPracticeType = type
                    },
                )
            }
        }

        pendingPracticeType?.let { type ->
            AlertDialog(
                onDismissRequest = { pendingPracticeType = null },
                title = { Text(stringResource(R.string.practice_switch_title)) },
                text = { Text(stringResource(R.string.practice_switch_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingPracticeType = null
                        scope.launch {
                            viewModel.recreateSession(type, shuffle = type == PracticeType.RANDOM)
                                ?.let { newId -> onSessionRestart(newId) }
                        }
                    }) { Text(stringResource(R.string.action_start)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPracticeType = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                },
            )
        }

        if (showSubmitDialog) {
            AlertDialog(
                onDismissRequest = { showSubmitDialog = false },
                title = { Text(stringResource(R.string.study_submit_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            if (ungradedCount > 0) {
                                R.string.study_submit_incomplete_text
                            } else {
                                R.string.study_submit_confirm_text
                            },
                            ungradedCount,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSubmitDialog = false
                        viewModel.submit()
                    }) { Text(stringResource(R.string.study_submit)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSubmitDialog = false }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                },
            )
        }

        if (showNoteDialog) {
            currentQuestion?.let { question ->
                NoteDialog(
                    initial = state.notes[question.id].orEmpty(),
                    onDismiss = { showNoteDialog = false },
                    onSave = { text ->
                        showNoteDialog = false
                        viewModel.saveNote(question.id, text)
                    },
                )
            }
        }
    }
}

@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_note_edit_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.study_note_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun practiceTypeLabel(type: String): String = stringResource(
    when (type) {
        PracticeType.SEQUENTIAL -> R.string.practice_sequential
        PracticeType.RANDOM -> R.string.practice_random
        PracticeType.CATEGORY -> R.string.practice_category
        PracticeType.CUSTOM -> R.string.practice_custom
        PracticeType.WRONG_BOOK -> R.string.practice_wrong_book
        PracticeType.FAVORITE -> R.string.practice_favorite
        else -> R.string.practice_sequential
    },
)
