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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import io.github.coderirse.reps.R
import io.github.coderirse.reps.ui.favorites.FavoritesScreen
import io.github.coderirse.reps.ui.home.HomeScreen
import io.github.coderirse.reps.ui.home.PracticeConfigScreen
import io.github.coderirse.reps.ui.history.HistoryScreen
import io.github.coderirse.reps.ui.home.PracticeConfigViewModel
import io.github.coderirse.reps.ui.import.ImportPreviewScreen
import io.github.coderirse.reps.ui.navigation.About
import io.github.coderirse.reps.ui.navigation.Favorites
import io.github.coderirse.reps.ui.navigation.History
import io.github.coderirse.reps.ui.navigation.Home
import io.github.coderirse.reps.ui.navigation.ImportPreview
import io.github.coderirse.reps.ui.navigation.PracticeConfig
import io.github.coderirse.reps.ui.navigation.SessionResult
import io.github.coderirse.reps.ui.navigation.Settings
import io.github.coderirse.reps.ui.navigation.Study
import io.github.coderirse.reps.ui.navigation.TOP_LEVEL_TABS
import io.github.coderirse.reps.ui.navigation.WrongBook
import io.github.coderirse.reps.ui.navigation.label
import io.github.coderirse.reps.ui.settings.AboutScreen
import io.github.coderirse.reps.ui.settings.SettingsScreen
import io.github.coderirse.reps.ui.study.ResultScreen
import io.github.coderirse.reps.ui.study.StudyScreen
import io.github.coderirse.reps.ui.wrongbook.WrongBookScreen
import java.net.URLDecoder
import java.net.URLEncoder

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
            composable<Home> { entry ->
                HomeScreen(
                    snackbarMessage = entry.savedStateHandle.get<String>(IMPORT_MESSAGE_KEY),
                    onSnackbarShown = { entry.savedStateHandle.remove<String>(IMPORT_MESSAGE_KEY) },
                    onOpenImportPreview = { uri ->
                        navController.navigate(ImportPreview(URLEncoder.encode(uri.toString(), "UTF-8")))
                    },
                    onOpenPracticeConfig = { subjectId, practiceType ->
                        navController.navigate(PracticeConfig(subjectId, practiceType))
                    },
                    onStartSession = { sessionId -> navController.navigate(Study(sessionId)) },
                )
            }
            composable<PracticeConfig> { entry ->
                val args = entry.toRoute<PracticeConfig>()
                PracticeConfigScreen(
                    practiceType = args.practiceType,
                    onBack = { navController.popBackStack() },
                    onSessionStarted = { sessionId ->
                        navController.navigate(Study(sessionId)) {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    },
                    viewModel = viewModel(factory = PracticeConfigViewModel.create(args.subjectId, args.practiceType)),
                )
            }
            composable<ImportPreview> { entry ->
                val args = entry.toRoute<ImportPreview>()
                val context = androidx.compose.ui.platform.LocalContext.current
                ImportPreviewScreen(
                    uri = android.net.Uri.parse(URLDecoder.decode(args.encodedUri, "UTF-8")),
                    onBack = { navController.popBackStack() },
                    onImported = { _, count ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.set(IMPORT_MESSAGE_KEY, context.getString(R.string.import_done_message, count))
                        navController.popBackStack()
                    },
                )
            }
            composable<Study> { entry ->
                val args = entry.toRoute<Study>()
                StudyScreen(
                    sessionId = args.sessionId,
                    onClose = { navController.popBackStack() },
                    onSessionFinished = { id ->
                        navController.navigate(SessionResult(id)) {
                            // Replace Study with Result on the back stack.
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    },
                )
            }
            composable<SessionResult> { entry ->
                val args = entry.toRoute<SessionResult>()
                ResultScreen(
                    sessionId = args.sessionId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable<WrongBook> {
                WrongBookScreen(
                    onOpenConfig = { subjectId ->
                        navController.navigate(PracticeConfig(subjectId, io.github.coderirse.reps.data.db.entity.PracticeType.WRONG_BOOK))
                    },
                    onSessionStarted = { sessionId -> navController.navigate(Study(sessionId)) },
                )
            }
            composable<Favorites> {
                FavoritesScreen(
                    onOpenConfig = { subjectId ->
                        navController.navigate(PracticeConfig(subjectId, io.github.coderirse.reps.data.db.entity.PracticeType.FAVORITE))
                    },
                )
            }
            composable<History> {
                HistoryScreen(
                    onOpenResult = { sessionId -> navController.navigate(SessionResult(sessionId)) },
                )
            }
            composable<Settings> {
                SettingsScreen(onOpenAbout = { navController.navigate(About) { launchSingleTop = true } })
            }
            composable<About> { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private const val IMPORT_MESSAGE_KEY = "import_message"
