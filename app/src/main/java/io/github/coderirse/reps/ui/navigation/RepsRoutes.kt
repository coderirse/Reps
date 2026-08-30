package io.github.coderirse.reps.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.coderirse.reps.R
import kotlinx.serialization.Serializable

@Serializable
data object Home

@Serializable
data object WrongBook

@Serializable
data object Favorites

@Serializable
data object Settings

@Serializable
data object About

/** Phase 2: import flow and study flow. URI is URL-encoded for the route. */
@Serializable
data class ImportPreview(val encodedUri: String)

@Serializable
data class Study(val sessionId: Long)

@Serializable
data class SessionResult(val sessionId: Long)

data class TopLevelTab(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val TOP_LEVEL_TABS = listOf(
    TopLevelTab(Home, R.string.tab_library, Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelTab(WrongBook, R.string.tab_wrong_book, Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List),
    TopLevelTab(Favorites, R.string.tab_favorites, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    TopLevelTab(Settings, R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun TopLevelTab.label(): String = stringResource(labelRes)
