package io.github.coderirse.reps.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.coderirse.reps.R
import io.github.coderirse.reps.data.db.entity.PracticeType
import io.github.coderirse.reps.data.db.entity.StudySessionEntity

/**
 * Practice picker for a tapped subject: resume card (when an unfinished
 * session exists) plus the four mode entries, each leading to the practice
 * config page.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PracticeSheet(
    subjectName: String,
    resumeSession: StudySessionEntity?,
    onResume: (Long) -> Unit,
    onOpenConfig: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                subjectName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            resumeSession?.let { session ->
                val total = session.questionIds.split(',').count { it.isNotBlank() }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.practice_resume)) },
                    supportingContent = {
                        Text(stringResource(R.string.practice_resume_meta, session.currentIndex + 1, total))
                    },
                    modifier = Modifier.clickable { onResume(session.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            ModeEntry(
                icon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                labelRes = R.string.practice_recite,
                descRes = R.string.practice_recite_desc,
                onClick = { onOpenConfig(PracticeType.RECITE) },
            )
            ModeEntry(
                icon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                labelRes = R.string.practice_exam,
                descRes = R.string.practice_exam_desc,
                onClick = { onOpenConfig(PracticeType.EXAM) },
            )
            ModeEntry(
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                labelRes = R.string.practice_wrong_book,
                descRes = R.string.practice_wrong_book_desc,
                onClick = { onOpenConfig(PracticeType.WRONG_BOOK) },
            )
            ModeEntry(
                icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                labelRes = R.string.practice_favorite,
                descRes = R.string.practice_favorite_desc,
                onClick = { onOpenConfig(PracticeType.FAVORITE) },
            )
        }
    }
}

@Composable
private fun ModeEntry(
    icon: @Composable () -> Unit,
    labelRes: Int,
    descRes: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        supportingContent = {
            Text(
                stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = icon,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
