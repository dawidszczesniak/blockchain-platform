package pl.dawidszczesniak.blockchain_platform.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pl.dawidszczesniak.blockchain_platform.navigation.Route

data class AppState(
    val route: Route = Route.Home,
    val isLoggedIn: Boolean = false,
    val pendingRouteAfterLogin: Route? = null,
)

sealed interface AppIntent {
    data class Navigate(val route: Route) : AppIntent
    data object OpenLogin : AppIntent
    data object LoginSucceeded : AppIntent
    data object Logout : AppIntent
}

class AppViewModel {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun onIntent(intent: AppIntent) {
        when (intent) {
            is AppIntent.Navigate -> {
                _state.update { current ->
                    if (!current.isLoggedIn && intent.route.requiresAuthentication()) {
                        current.copy(
                            route = Route.Login,
                            pendingRouteAfterLogin = intent.route,
                        )
                    } else {
                        current.copy(route = intent.route)
                    }
                }
            }

            AppIntent.OpenLogin -> {
                _state.update { current ->
                    current.copy(route = Route.Login)
                }
            }

            AppIntent.LoginSucceeded -> {
                _state.update { current ->
                    current.copy(
                        isLoggedIn = true,
                        route = current.pendingRouteAfterLogin ?: Route.Home,
                        pendingRouteAfterLogin = null,
                    )
                }
            }

            AppIntent.Logout -> {
                _state.update { current ->
                    current.copy(
                        isLoggedIn = false,
                        route = Route.Home,
                        pendingRouteAfterLogin = null,
                    )
                }
            }
        }
    }
}

private fun Route.requiresAuthentication(): Boolean {
    return when (this) {
        Route.CreateProblem,
        Route.MyProblems,
        Route.MyParticipation,
        Route.Settings,
        -> true

        Route.Home,
        Route.Login,
        Route.Problems,
        -> false
    }
}
