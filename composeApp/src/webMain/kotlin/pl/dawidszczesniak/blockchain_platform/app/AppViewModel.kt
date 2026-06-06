package pl.dawidszczesniak.blockchain_platform.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.ClientConnectivityMonitor
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletRuntimeEvent
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletRuntimeEventType
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletProvider
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletSessionSubscription
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.login.repository.LoginRepository
import pl.dawidszczesniak.blockchain_platform.feature.login.repository.SessionExpiredException
import pl.dawidszczesniak.blockchain_platform.network.SessionExpirationReason
import pl.dawidszczesniak.blockchain_platform.network.SessionExpirationNotifier
import pl.dawidszczesniak.blockchain_platform.navigation.Route

data class AppState(
    val route: Route = Route.Home,
    val isLoggedIn: Boolean = false,
    val pendingRouteAfterLogin: Route? = null,
    val isRestoringSession: Boolean = true,
    val sessionExpirationReason: SessionExpirationReason? = null,
)

sealed interface AppIntent {
    data class Navigate(val route: Route) : AppIntent
    data object OpenLogin : AppIntent
    data object LoginSucceeded : AppIntent
    data object Logout : AppIntent
    data object DismissSessionExpiredNotice : AppIntent
}

class AppViewModel(
    private val loginRepository: LoginRepository,
    private val walletProvider: WalletProvider,
    private val walletSessionStore: WalletSessionStore,
    private val clientConnectivityMonitor: ClientConnectivityMonitor,
    private val sessionExpirationNotifier: SessionExpirationNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AppState())
    private var walletSessionSubscription: WalletSessionSubscription? = null
    private var restoreInFlight: Boolean = false
    private var restoreRetryScheduled: Boolean = false
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        watchClientConnectivity()
        watchSessionExpiration()
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
                        sessionExpirationReason = null,
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
                        sessionExpirationReason = null,
                    )
                }
            }

            AppIntent.Logout -> {
                performLogout()
            }

            AppIntent.DismissSessionExpiredNotice -> {
                _state.update { current ->
                    current.copy(sessionExpirationReason = null)
                }
            }
        }
    }

    fun close() {
        stopWatchingWalletSession()
        scope.cancel()
    }

    private fun restoreSession() {
        if (restoreInFlight) return
        restoreInFlight = true
        scope.launch {
            try {
                if (!clientConnectivityMonitor.state.value.isOnline) {
                    _state.update { current ->
                        current.copy(isRestoringSession = true)
                    }
                    return@launch
                }

                val sessionWallet = try {
                    loginRepository.getSessionWallet()?.trim().orEmpty()
                } catch (error: SessionExpiredException) {
                    handleSessionExpired(error.reason)
                    return@launch
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    keepSessionRestorePending()
                    return@launch
                }

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
            } finally {
                restoreInFlight = false
            }
        }
    }

    private fun watchClientConnectivity() {
        scope.launch {
            clientConnectivityMonitor.state.collect { connectivity ->
                if (connectivity.isOnline && state.value.isRestoringSession) {
                    restoreSession()
                }
            }
        }
    }

    private fun watchSessionExpiration() {
        scope.launch {
            sessionExpirationNotifier.events.collect { reason ->
                handleSessionExpired(reason)
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
                if (!clientConnectivityMonitor.state.value.isOnline) {
                    return
                }
                val sessionWallet = try {
                    loginRepository.getSessionWallet()?.trim().orEmpty()
                } catch (error: SessionExpiredException) {
                    handleSessionExpired(error.reason)
                    return
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    return
                }
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
                sessionExpirationReason = null,
            )
        }
        scope.launch {
            loginRepository.logout()
        }
    }

    private fun keepSessionRestorePending() {
        _state.update { current ->
            current.copy(isRestoringSession = true)
        }
        scheduleSessionRestoreRetry()
    }

    private fun scheduleSessionRestoreRetry() {
        if (restoreRetryScheduled || !clientConnectivityMonitor.state.value.isOnline) {
            return
        }
        restoreRetryScheduled = true
        scope.launch {
            delay(5_000L)
            restoreRetryScheduled = false
            if (_state.value.isRestoringSession) {
                restoreSession()
            }
        }
    }

    private fun handleSessionExpired(reason: SessionExpirationReason) {
        stopWatchingWalletSession()
        walletSessionStore.clear()
        _state.update { current ->
            current.copy(
                isLoggedIn = false,
                isRestoringSession = false,
                pendingRouteAfterLogin = current.route.takeIf { it.requiresAuthentication() },
                sessionExpirationReason = reason,
            )
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
