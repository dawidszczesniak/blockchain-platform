package pl.dawidszczesniak.blockchain_platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import pl.dawidszczesniak.blockchain_platform.screens.CreateProblemScreen
import pl.dawidszczesniak.blockchain_platform.screens.LoginScreen
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
    var problemsScreenKey by remember { mutableStateOf(0) }

    fun goHomeRecreate() {
        problemsScreenKey++
        onNavigate(Route.Problems)
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
                    onHomeClick = { goHomeRecreate() }
                )
            }
        ) { padding ->
            AppPageContainer(modifier = Modifier.padding(padding)) {
                when (currentRoute) {
                    Route.Problems -> key(problemsScreenKey) { ProblemsListScreen() }
                    Route.CreateProblem -> CreateProblemScreen()
                    Route.Settings -> SettingsScreen()
                    Route.Login -> LoginScreen(onLogin = onLogin)
                }
            }
        }
    }
}
