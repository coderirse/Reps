package io.github.coderirse.reps.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.coderirse.reps.R
import io.github.coderirse.reps.ui.components.EmptyState
import kotlinx.coroutines.launch

/**
 * Phase 1: empty state guiding the first import. The button is wired to a
 * placeholder snackbar until the CSV import flow lands in Phase 2.
 */
@Composable
fun HomeScreen() {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoonMessage = stringResource(R.string.home_import_coming_soon)

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        EmptyState(
            icon = Icons.Outlined.AddCircle,
            title = stringResource(R.string.home_empty_title),
            description = stringResource(R.string.home_empty_description),
            modifier = Modifier.align(Alignment.Center),
            action = {
                Button(onClick = { scope.launch { snackbarHostState.showSnackbar(comingSoonMessage) } }) {
                    Text(stringResource(R.string.home_import_button))
                }
            },
        )
    }
}
