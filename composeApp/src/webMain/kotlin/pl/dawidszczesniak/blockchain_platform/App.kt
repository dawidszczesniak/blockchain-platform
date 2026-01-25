package pl.dawidszczesniak.blockchain_platform

import androidx.compose.runtime.*

@Composable
fun App() {

    var isLoggedIn by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(Route.Problems) }
    var pendingRouteAfterLogin by remember { mutableStateOf<Route?>(null) }

    AppTheme {
        fun navigate(target: Route) {
            if (!isLoggedIn && (target == Route.CreateProblem || target == Route.Settings)) {
                pendingRouteAfterLogin = target
                route = Route.Login
            } else {
                route = target
            }
        }

        AppShell(
            currentRoute = route,
            onNavigate = { navigate(it) },
            isLoggedIn = isLoggedIn,
            onLoginClick = {
                route = Route.Login
            },
            onLogin = {
                isLoggedIn = true
                route = pendingRouteAfterLogin ?: Route.Problems
                pendingRouteAfterLogin = null
            },
            onLogout = {
                isLoggedIn = false
                pendingRouteAfterLogin = null
                route = Route.Problems
            }
        )
    }
}
