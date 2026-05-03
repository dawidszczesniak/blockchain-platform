package pl.dawidszczesniak.blockchain_platform.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletRuntimeEvent
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletRuntimeEventType
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletProvider
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletSessionSubscription
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.login.repository.LoginRepository
import pl.dawidszczesniak.blockchain_platform.navigation.Route

data class AppState(
    val route: Route = Route.Home,
    val isLoggedIn: Boolean = false,
    val pendingRouteAfterLogin: Route? = null,
    val isRestoringSession: Boolean = true,
)

sealed interface AppIntent {
    data class Navigate(val route: Route) : AppIntent
    data object OpenLogin : AppIntent
    data object LoginSucceeded : AppIntent
    data object Logout : AppIntent
}

class AppViewModel(
    private val loginRepository: LoginRepository,
    private val walletProvider: WalletProvider,
    private val walletSessionStore: WalletSessionStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AppState())
    private var walletSessionSubscription: WalletSessionSubscription? = null
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    fun onIntent(intent: AppIntent) {
        when (intent) {
            is AppIntent.Navigate -> {
                _state.update { current ->
                    if (
                        !current.isRestoringSession &&
                        !current.isLoggedIn &&
                        intent.route.requiresAuthentication()
                    ) {
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
                    current.copy(
                        route = Route.Login,
                        pendingRouteAfterLogin = if (current.route == Route.Login) {
                            current.pendingRouteAfterLogin
                        } else {
                            current.route
                        },
                    )
                }
            }

            AppIntent.LoginSucceeded -> {
                startWatchingCurrentWallet()
                _state.update { current ->
                    current.copy(
                        isLoggedIn = true,
                        isRestoringSession = false,
                        route = current.pendingRouteAfterLogin ?: Route.Home,
                        pendingRouteAfterLogin = null,
                    )
                }
            }

            AppIntent.Logout -> {
                performLogout()
            }
        }
    }

    fun close() {
        stopWatchingWalletSession()
        scope.cancel()
    }

    private fun restoreSession() {
        scope.launch {
            val sessionWallet = loginRepository.getSessionWallet()?.trim().orEmpty()
            if (sessionWallet.isBlank()) {
                walletSessionStore.clear()
            } else {
                runCatching {
                    walletSessionStore.restoreForSession(
                        sessionWalletAddress = sessionWallet,
                        walletProvider = walletProvider,
                    )
                }
                startWatchingCurrentWallet()
            }
            _state.update { current ->
                current.copy(
                    isLoggedIn = sessionWallet.isNotBlank(),
                    isRestoringSession = false,
                )
            }
        }
    }

    private fun startWatchingCurrentWallet() {
        stopWatchingWalletSession()
        val walletId = walletSessionStore.currentWalletId() ?: return
        walletSessionSubscription = walletProvider.watchWalletSession(walletId) { event ->
            scope.launch {
                handleWalletRuntimeEvent(event)
            }
        }
    }

    private fun stopWatchingWalletSession() {
        walletSessionSubscription?.cancel()
        walletSessionSubscription = null
    }

    private suspend fun handleWalletRuntimeEvent(event: WalletRuntimeEvent) {
        when (event.type) {
            WalletRuntimeEventType.AccountsChanged -> {
                val sessionWallet = loginRepository.getSessionWallet()?.trim().orEmpty()
                val updatedWalletAddress = event.walletAddress?.trim().orEmpty()
                if (
                    updatedWalletAddress.isBlank() ||
                    sessionWallet.isBlank() ||
                    !updatedWalletAddress.equals(sessionWallet, ignoreCase = true)
                ) {
                    performLogout()
                    return
                }
                val currentWalletId = walletSessionStore.currentWalletId() ?: return
                walletSessionStore.setCurrentWallet(
                    walletId = currentWalletId,
                    walletAddress = updatedWalletAddress,
                )
            }

            WalletRuntimeEventType.ChainChanged -> {
                stopWatchingWalletSession()
                walletSessionStore.clear()
            }

            WalletRuntimeEventType.Disconnect -> {
                performLogout()
            }
        }
    }

    private fun performLogout() {
        stopWatchingWalletSession()
        walletSessionStore.clear()
        _state.update { current ->
            current.copy(
                isLoggedIn = false,
                isRestoringSession = false,
                route = Route.Home,
                pendingRouteAfterLogin = null,
            )
        }
        scope.launch {
            loginRepository.logout()
        }
    }
}

private fun Route.requiresAuthentication(): Boolean {
    return when (this) {
        Route.CreateProblem,
        Route.MyProblems,
        Route.MyParticipation,
        Route.Settings,
        is Route.CreatedProblemDetails,
        -> true

        Route.Home,
        Route.Login,
        Route.Problems,
        is Route.ProblemDetails,
        -> false
    }
}
