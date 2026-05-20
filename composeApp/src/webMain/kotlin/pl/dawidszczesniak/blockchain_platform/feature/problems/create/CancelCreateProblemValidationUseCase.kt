package pl.dawidszczesniak.blockchain_platform.feature.problems.create

import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface CancelCreateProblemValidationUseCase {
    suspend operator fun invoke(runId: String)
}

class CancelCreateProblemValidationUseCaseImpl(
    private val repository: ProblemRepository,
) : CancelCreateProblemValidationUseCase {
    override suspend fun invoke(runId: String) {
        repository.cancelCreateProblemValidation(runId)
    }
}
