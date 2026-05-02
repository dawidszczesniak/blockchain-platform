package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.JoinProblemResult

internal class JoinProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface JoinProblemUseCase {
    operator fun invoke(userId: Long, problemId: Int): JoinProblemResult
}

internal class JoinProblemUseCaseImpl : JoinProblemUseCase {
    override operator fun invoke(userId: Long, problemId: Int): JoinProblemResult {
        if (problemId <= 0) {
            throw JoinProblemValidationException("Invalid problem identifier.")
        }
        throw JoinProblemValidationException(
            "Direct backend-only join is disabled. Prepare and confirm the on-chain transaction instead."
        )
    }
}
