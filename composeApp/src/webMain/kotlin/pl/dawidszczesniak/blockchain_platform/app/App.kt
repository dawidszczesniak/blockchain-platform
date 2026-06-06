package pl.dawidszczesniak.blockchain_platform.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.feature.settings.AppLanguageStore
import pl.dawidszczesniak.blockchain_platform.ui.AppShell
import pl.dawidszczesniak.blockchain_platform.ui.AppTheme

@Composable
fun App() {
    val koin = LocalKoin.current
    val appViewModel = remember { koin.get<AppViewModel>() }
    val appLanguageStore = remember { koin.get<AppLanguageStore>() }
    DisposableEffect(appViewModel) {
        onDispose { appViewModel.close() }
    }
    val state by appViewModel.state.collectAsState()
    val language by appLanguageStore.language.collectAsState()

    AppTheme {
        key(language) {
            AppShell(
                currentRoute = state.route,
                onNavigate = { appViewModel.onIntent(AppIntent.Navigate(it)) },
                isLoggedIn = state.isLoggedIn,
                isRestoringSession = state.isRestoringSession,
                sessionExpirationReason = state.sessionExpirationReason,
                onLoginClick = {
                    appViewModel.onIntent(AppIntent.OpenLogin)
                },
                onLogin = {
                    appViewModel.onIntent(AppIntent.LoginSucceeded)
                },
                onLogout = {
                    appViewModel.onIntent(AppIntent.Logout)
                },
                onDismissSessionExpired = {
                    appViewModel.onIntent(AppIntent.DismissSessionExpiredNotice)
                }
            )
        }
    }
}
