package pl.dawidszczesniak.blockchain_platform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.session_expired_action
import blockchain_platform.composeapp.generated.resources.session_expired_absolute_body
import blockchain_platform.composeapp.generated.resources.session_expired_absolute_title
import blockchain_platform.composeapp.generated.resources.session_expired_body
import blockchain_platform.composeapp.generated.resources.session_expired_dismiss
import blockchain_platform.composeapp.generated.resources.session_expired_idle_body
import blockchain_platform.composeapp.generated.resources.session_expired_idle_title
import blockchain_platform.composeapp.generated.resources.session_expired_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.navigation.Route
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemScreen
import pl.dawidszczesniak.blockchain_platform.feature.home.HomeScreen
import pl.dawidszczesniak.blockchain_platform.feature.login.LoginScreen
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendHealthIntent
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendHealthViewModel
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendMaintenanceScreen
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.ClientConnectivityMonitor
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.NetworkOfflineScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.participation.MyParticipationScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.created.CreatedProblemDetailsScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.created.MyProblemsScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.details.ProblemDetailsScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.details.ProblemDetailsViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.list.ProblemsListScreen
import pl.dawidszczesniak.blockchain_platform.feature.problems.list.ProblemsListViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository
import pl.dawidszczesniak.blockchain_platform.feature.settings.SettingsScreen
import pl.dawidszczesniak.blockchain_platform.network.SessionExpirationReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    currentRoute: Route,
    onNavigate: (Route) -> Unit,
    isLoggedIn: Boolean,
    isRestoringSession: Boolean,
    sessionExpirationReason: SessionExpirationReason?,
    onLoginClick: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDismissSessionExpired: () -> Unit,
) {
    val koin = LocalKoin.current
    val scope = rememberCoroutineScope()
    val clientConnectivityMonitor = remember { koin.get<ClientConnectivityMonitor>() }
    val backendHealthViewModel = remember { koin.get<BackendHealthViewModel>() }
    val problemDetailsViewModel = remember { koin.get<ProblemDetailsViewModel>() }
    val problemRepository = remember { koin.get<ProblemRepository>() }
    DisposableEffect(backendHealthViewModel) {
        onDispose { backendHealthViewModel.close() }
    }
    DisposableEffect(problemDetailsViewModel) {
        onDispose { problemDetailsViewModel.close() }
    }
    val clientConnectivityState by clientConnectivityMonitor.state.collectAsState()
    val backendHealthState by backendHealthViewModel.state.collectAsState()
    var hasRenderedPageContent by remember { mutableStateOf(false) }
    val refreshBackendHealth = {
        backendHealthViewModel.onIntent(BackendHealthIntent.RefreshNow)
    }
    val navigate = { route: Route ->
        refreshBackendHealth()
        onNavigate(route)
    }
    val requireLogin = {
        refreshBackendHealth()
        onLoginClick()
    }
    val logout = {
        refreshBackendHealth()
        onLogout()
    }
    if (clientConnectivityState.isOnline && backendHealthState.isAvailable == false) {
        BackendMaintenanceScreen()
        return
    }
    val isBackendHealthPending = clientConnectivityState.isOnline && backendHealthState.isAvailable == null
    val shouldShowInitialLoader = !hasRenderedPageContent && (isRestoringSession || isBackendHealthPending)

    Box(modifier = Modifier.fillMaxSize()) {
        AppBackdrop(modifier = Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopBar(
                    currentRoute = currentRoute,
                    onNavigate = navigate,
                    isLoggedIn = isLoggedIn,
                    onLoginClick = requireLogin,
                    onLogout = logout,
                    onHomeClick = { navigate(Route.Home) }
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
                if (shouldShowInitialLoader) {
                    AppSurface(modifier = Modifier.fillMaxWidth()) {
                        AppPanelLoader(minHeight = 260.dp)
                    }
                } else {
                    SideEffect {
                        hasRenderedPageContent = true
                    }
                    when (val route = currentRoute) {
                        Route.Home -> HomeScreen(
                            onNavigateToProblems = { navigate(Route.Problems) },
                            onOpenProblem = { problemId ->
                                refreshBackendHealth()
                                scope.launch {
                                    runCatching { problemRepository.fetchProblemById(problemId.toInt()) }
                                        .onSuccess { problemSummary ->
                                            navigate(Route.ProblemDetails(problemSummary))
                                        }
                                }
                            }
                        )
                        Route.Problems -> {
                            val problemsListViewModel = remember { koin.get<ProblemsListViewModel>() }
                            ProblemsListScreen(
                                viewModel = problemsListViewModel,
                                onCreateProblem = { navigate(Route.CreateProblem) },
                                onOpenProblem = { problem ->
                                    refreshBackendHealth()
                                    scope.launch {
                                        runCatching { problemRepository.fetchProblemById(problem.id) }
                                            .onSuccess { problemSummary ->
                                                navigate(Route.ProblemDetails(problemSummary))
                                            }
                                    }
                                }
                            )
                        }
                        is Route.ProblemDetails -> {
                            ProblemDetailsScreen(
                                problem = route.problem,
                                viewModel = problemDetailsViewModel,
                                isLoggedIn = isLoggedIn,
                                onRequireLogin = requireLogin,
                                onBackToProblems = { navigate(Route.Problems) },
                            )
                        }
                        is Route.CreatedProblemDetails -> {
                            CreatedProblemDetailsScreen(
                                problem = route.problem,
                                createdProblem = route.createdProblem,
                                viewModel = problemDetailsViewModel,
                                isLoggedIn = isLoggedIn,
                                onRequireLogin = requireLogin,
                                onBackToMyProblems = { navigate(Route.MyProblems) },
                            )
                        }
                        Route.CreateProblem -> CreateProblemScreen()
                        Route.MyProblems -> MyProblemsScreen(
                            onCreateProblem = { navigate(Route.CreateProblem) },
                            onOpenProblem = { createdProblem, onComplete ->
                                refreshBackendHealth()
                                scope.launch {
                                    runCatching { problemRepository.fetchProblemById(createdProblem.id) }
                                        .onSuccess { problemSummary ->
                                            onComplete(true)
                                            navigate(
                                                Route.CreatedProblemDetails(
                                                    problem = problemSummary,
                                                    createdProblem = createdProblem,
                                                )
                                            )
                                        }
                                        .onFailure {
                                            onComplete(false)
                                        }
                                }
                            }
                        )
                        Route.MyParticipation -> MyParticipationScreen(
                            onBrowseProblems = { navigate(Route.Problems) },
                            onOpenProblem = { problemId, onComplete ->
                                refreshBackendHealth()
                                scope.launch {
                                    runCatching { problemRepository.fetchProblemById(problemId) }
                                        .onSuccess { problemSummary ->
                                            onComplete(true)
                                            navigate(Route.ProblemDetails(problemSummary))
                                        }
                                        .onFailure {
                                            onComplete(false)
                                        }
                                }
                            }
                        )
                        Route.Settings -> SettingsScreen()
                        Route.Login -> LoginScreen(onLogin = {
                            refreshBackendHealth()
                            onLogin()
                        })
                    }
                }
            }
        }
        if (!clientConnectivityState.isOnline) {
            NetworkOfflineScreen(modifier = Modifier.fillMaxSize())
        }
        val currentSessionExpirationReason = sessionExpirationReason
        if (currentSessionExpirationReason != null) {
            SessionExpiredNotice(
                reason = currentSessionExpirationReason,
                onLoginClick = requireLogin,
                onDismiss = onDismissSessionExpired,
            )
        }
    }
}

@Composable
private fun SessionExpiredNotice(
    reason: SessionExpirationReason,
    onLoginClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (reason) {
        SessionExpirationReason.IdleTimeout -> stringResource(Res.string.session_expired_idle_title)
        SessionExpirationReason.AbsoluteTimeout -> stringResource(Res.string.session_expired_absolute_title)
        SessionExpirationReason.Unknown -> stringResource(Res.string.session_expired_title)
    }
    val body = when (reason) {
        SessionExpirationReason.IdleTimeout -> stringResource(Res.string.session_expired_idle_body)
        SessionExpirationReason.AbsoluteTimeout -> stringResource(Res.string.session_expired_absolute_body)
        SessionExpirationReason.Unknown -> stringResource(Res.string.session_expired_body)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedCard(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 320.dp, max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.session_expired_dismiss))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onLoginClick) {
                        Text(stringResource(Res.string.session_expired_action))
                    }
                }
            }
        }
    }
}
