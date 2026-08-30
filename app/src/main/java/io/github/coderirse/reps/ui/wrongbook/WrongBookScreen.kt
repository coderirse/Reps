package io.github.coderirse.reps.ui.wrongbook

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.coderirse.reps.R
import io.github.coderirse.reps.ui.components.EmptyState

@Composable
fun WrongBookScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(R.string.wrong_book_empty_title),
        description = stringResource(R.string.wrong_book_empty_description),
        modifier = modifier,
    )
}
