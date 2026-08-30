package io.github.coderirse.reps.ui.favorites

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.coderirse.reps.R
import io.github.coderirse.reps.ui.components.EmptyState

@Composable
fun FavoritesScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.FavoriteBorder,
        title = stringResource(R.string.favorites_empty_title),
        description = stringResource(R.string.favorites_empty_description),
        modifier = modifier,
    )
}
