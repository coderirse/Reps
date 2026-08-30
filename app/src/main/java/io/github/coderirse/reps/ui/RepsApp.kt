package io.github.coderirse.reps.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.coderirse.reps.ui.favorites.FavoritesScreen
import io.github.coderirse.reps.ui.home.HomeScreen
import io.github.coderirse.reps.ui.navigation.About
import io.github.coderirse.reps.ui.navigation.Favorites
import io.github.coderirse.reps.ui.navigation.Home
import io.github.coderirse.reps.ui.navigation.Settings
import io.github.coderirse.reps.ui.navigation.TOP_LEVEL_TABS
import io.github.coderirse.reps.ui.navigation.WrongBook
import io.github.coderirse.reps.ui.navigation.label
import io.github.coderirse.reps.ui.settings.AboutScreen
import io.github.coderirse.reps.ui.settings.SettingsScreen
import io.github.coderirse.reps.ui.wrongbook.WrongBookScreen

@Composable
fun RepsApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTopLevelTab = TOP_LEVEL_TABS.any { currentDestination?.hasRoute(it.route::class) == true }

    Scaffold(
        bottomBar = {
            if (onTopLevelTab) {
                NavigationBar {
                    TOP_LEVEL_TABS.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(tab.label()) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Home> { HomeScreen() }
            composable<WrongBook> { WrongBookScreen() }
            composable<Favorites> { FavoritesScreen() }
            composable<Settings> {
                SettingsScreen(onOpenAbout = { navController.navigate(About) { launchSingleTop = true } })
            }
            composable<About> { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
