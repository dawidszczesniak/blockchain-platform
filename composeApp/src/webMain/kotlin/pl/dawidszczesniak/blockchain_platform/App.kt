package pl.dawidszczesniak.blockchain_platform

import androidx.compose.runtime.*

@Composable
fun App() {

    var isLoggedIn by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(Route.Home) }
    var pendingRouteAfterLogin by remember { mutableStateOf<Route?>(null) }

    AppTheme {
        fun navigate(target: Route) {
            if (!isLoggedIn && (target == Route.CreateProblem || target == Route.Settings || target == Route.MyProblems || target == Route.MyParticipation)) {
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
                route = pendingRouteAfterLogin ?: Route.Home
                pendingRouteAfterLogin = null
            },
            onLogout = {
                isLoggedIn = false
                pendingRouteAfterLogin = null
                route = Route.Home
            }
        )
    }
}
