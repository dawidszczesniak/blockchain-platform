package pl.dawidszczesniak.blockchain_platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.dawidszczesniak.blockchain_platform.screens.CreateProblemScreen
import pl.dawidszczesniak.blockchain_platform.screens.HomeScreen
import pl.dawidszczesniak.blockchain_platform.screens.LoginScreen
import pl.dawidszczesniak.blockchain_platform.screens.MyParticipationScreen
import pl.dawidszczesniak.blockchain_platform.screens.MyProblemsScreen
import pl.dawidszczesniak.blockchain_platform.screens.ProblemsListScreen
import pl.dawidszczesniak.blockchain_platform.screens.SettingsScreen
import pl.dawidszczesniak.blockchain_platform.ui.AppBackdrop
import pl.dawidszczesniak.blockchain_platform.ui.AppPageContainer

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
                modifier = Modifier.padding(padding),
                contentPadding = pagePadding
            ) {
                when (currentRoute) {
                    Route.Home -> HomeScreen(onNavigateToProblems = { onNavigate(Route.Problems) })
                    Route.Problems -> ProblemsListScreen(
                        onCreateProblem = { onNavigate(Route.CreateProblem) }
                    )
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
