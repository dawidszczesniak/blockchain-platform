package pl.dawidszczesniak.blockchain_platform.navigation

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

sealed interface Route {
    data object Home : Route
    data object Login : Route
    data object Problems : Route
    data object CreateProblem : Route
    data object MyProblems : Route
    data object MyParticipation : Route
    data object Settings : Route
    data class ProblemDetails(val problem: ProblemSummary) : Route
}
