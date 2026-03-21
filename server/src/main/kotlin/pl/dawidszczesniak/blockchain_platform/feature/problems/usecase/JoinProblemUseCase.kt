package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.JoinProblemResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository

internal class JoinProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface JoinProblemUseCase {
    operator fun invoke(userId: Long, problemId: Int): JoinProblemResult
}

internal class JoinProblemUseCaseImpl(
    private val repository: ProblemWriteRepository,
) : JoinProblemUseCase {
    override operator fun invoke(userId: Long, problemId: Int): JoinProblemResult {
        if (problemId <= 0) {
            throw JoinProblemValidationException("Invalid problem identifier.")
        }
        return try {
            repository.registerUserForProblem(
                userId = userId,
                problemId = problemId,
            )
        } catch (error: IllegalArgumentException) {
            throw JoinProblemValidationException(error.message ?: "Cannot register for this problem.")
        }
    }
}
