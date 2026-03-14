package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

internal class ProblemAuthorizationException(
    message: String,
) : IllegalStateException(message)
