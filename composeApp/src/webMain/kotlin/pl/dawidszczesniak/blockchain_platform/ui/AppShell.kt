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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.dawidszczesniak.blockchain_platform.navigation.Route
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemScreen
import pl.dawidszczesniak.blockchain_platform.feature.home.HomeScreen
import pl.dawidszczesniak.blockchain_platform.feature.login.LoginScreen
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendHealthViewModel
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendMaintenanceScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.participation.MyParticipationScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.created.MyProblemsScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.list.ProblemsListScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.list.ProblemsListViewModel
import pl.dawidszczesniak.blockchain_platform.feature.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    currentRoute: Route,
    onNavigate: (Route) -> Unit,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val koin = LocalKoin.current
    val backendHealthViewModel = remember { koin.get<BackendHealthViewModel>() }
    DisposableEffect(backendHealthViewModel) {
        onDispose { backendHealthViewModel.close() }
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
                when (currentRoute) {
                    Route.Home -> HomeScreen(onNavigateToProblems = { onNavigate(Route.Problems) })
                    Route.Problems -> {
                        val problemsListViewModel = remember { koin.get<ProblemsListViewModel>() }
                        ProblemsListScreen(
                            viewModel = problemsListViewModel,
                            onCreateProblem = { onNavigate(Route.CreateProblem) }
                        )
                    }
                    Route.CreateProblem -> CreateProblemScreen()
                    Route.MyProblems -> MyProblemsScreen(
                        onCreateProblem = { onNavigate(Route.CreateProblem) }
                    )
                    Route.MyParticipation -> MyParticipationScreen(
                        onBrowseProblems = { onNavigate(Route.Problems) }
                    )
                    Route.Settings -> SettingsScreen()
                    Route.Login -> LoginScreen(onLogin = onLogin)
                }
            }
        }
    }
}
