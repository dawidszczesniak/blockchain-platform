package pl.dawidszczesniak.blockchain_platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.presentation.app.AppIntent
import pl.dawidszczesniak.blockchain_platform.presentation.app.AppViewModel

@Composable
fun App() {
    val koin = LocalKoin.current
    val appViewModel = remember { koin.get<AppViewModel>() }
    val state by appViewModel.state.collectAsState()

    AppTheme {
        AppShell(
            currentRoute = state.route,
            onNavigate = { appViewModel.onIntent(AppIntent.Navigate(it)) },
            isLoggedIn = state.isLoggedIn,
            onLoginClick = {
                appViewModel.onIntent(AppIntent.OpenLogin)
            },
            onLogin = {
                appViewModel.onIntent(AppIntent.LoginSucceeded)
            },
            onLogout = {
                appViewModel.onIntent(AppIntent.Logout)
            }
        )
    }
}
