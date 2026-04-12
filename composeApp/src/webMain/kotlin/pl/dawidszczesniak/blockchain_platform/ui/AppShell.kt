package pl.dawidszczesniak.blockchain_platform.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.dawidszczesniak.blockchain_platform.navigation.Route
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemScreen
import pl.dawidszczesniak.blockchain_platform.feature.home.HomeScreen
import pl.dawidszczesniak.blockchain_platform.feature.login.LoginScreen
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendHealthViewModel
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendMaintenanceScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.participation.MyParticipationScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.created.CreatedProblemDetailsScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.created.MyProblemsScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.details.ProblemDetailsScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.details.ProblemDetailsViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.list.ProblemsListScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.list.ProblemsListViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository
import pl.dawidszczesniak.blockchain_platform.feature.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    currentRoute: Route,
    onNavigate: (Route) -> Unit,
    isLoggedIn: Boolean,
    isRestoringSession: Boolean,
    onLoginClick: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val koin = LocalKoin.current
    val scope = rememberCoroutineScope()
    val backendHealthViewModel = remember { koin.get<BackendHealthViewModel>() }
    val problemDetailsViewModel = remember { koin.get<ProblemDetailsViewModel>() }
    val problemRepository = remember { koin.get<ProblemRepository>() }
    DisposableEffect(backendHealthViewModel) {
        onDispose { backendHealthViewModel.close() }
    }
    DisposableEffect(problemDetailsViewModel) {
        onDispose { problemDetailsViewModel.close() }
    }
    val backendHealthState by backendHealthViewModel.state.collectAsState()
    if (backendHealthState.isAvailable == false) {
        BackendMaintenanceScreen()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackdrop(modifier = Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopBar(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    isLoggedIn = isLoggedIn,
                    onLoginClick = onLoginClick,
                    onLogout = onLogout,
                    onHomeClick = { onNavigate(Route.Home) }
                )
            }
        ) { padding ->
            val pagePadding = PaddingValues(top = 24.dp, bottom = 24.dp)

            AppPageContainer(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = pagePadding
            ) {
                if (isRestoringSession || backendHealthState.isAvailable == null) {
                    AppSurface(modifier = Modifier.fillMaxWidth()) {
                        AppPanelLoader(minHeight = 260.dp)
                    }
                } else {
                    when (val route = currentRoute) {
                        Route.Home -> HomeScreen(onNavigateToProblems = { onNavigate(Route.Problems) })
                        Route.Problems -> {
                            val problemsListViewModel = remember { koin.get<ProblemsListViewModel>() }
                            ProblemsListScreen(
                                viewModel = problemsListViewModel,
                                onCreateProblem = { onNavigate(Route.CreateProblem) },
                                onOpenProblem = { onNavigate(Route.ProblemDetails(it)) }
                            )
                        }
                        is Route.ProblemDetails -> {
                            ProblemDetailsScreen(
                                problem = route.problem,
                                viewModel = problemDetailsViewModel,
                                isLoggedIn = isLoggedIn,
                                onRequireLogin = onLoginClick,
                                onBackToProblems = { onNavigate(Route.Problems) },
                            )
                        }
                        is Route.CreatedProblemDetails -> {
                            CreatedProblemDetailsScreen(
                                problem = route.problem,
                                createdProblem = route.createdProblem,
                                onBackToMyProblems = { onNavigate(Route.MyProblems) },
                            )
                        }
                        Route.CreateProblem -> CreateProblemScreen()
                    Route.MyProblems -> MyProblemsScreen(
                        onCreateProblem = { onNavigate(Route.CreateProblem) },
                        onOpenProblem = { createdProblem, onComplete ->
                            scope.launch {
                                runCatching { problemRepository.fetchProblemById(createdProblem.id) }
                                    .onSuccess { problemSummary ->
                                        onComplete(true)
                                        onNavigate(
                                            Route.CreatedProblemDetails(
                                                problem = problemSummary,
                                                createdProblem = createdProblem,
                                            )
                                        )
                                    }
                                    .onFailure {
                                        onComplete(false)
                                    }
                            }
                        }
                    )
                        Route.MyParticipation -> MyParticipationScreen(
                            onBrowseProblems = { onNavigate(Route.Problems) },
                            onOpenProblem = { problemId, onComplete ->
                                scope.launch {
                                    runCatching { problemRepository.fetchProblemById(problemId) }
                                        .onSuccess { problemSummary ->
                                            onComplete(true)
                                            onNavigate(Route.ProblemDetails(problemSummary))
                                        }
                                        .onFailure {
                                            onComplete(false)
                                        }
                                }
                            }
                        )
                        Route.Settings -> SettingsScreen()
                        Route.Login -> LoginScreen(onLogin = onLogin)
                    }
                }
            }
        }
    }
}
