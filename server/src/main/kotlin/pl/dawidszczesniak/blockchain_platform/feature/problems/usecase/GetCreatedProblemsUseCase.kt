package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepository

internal interface GetCreatedProblemsUseCase {
    operator fun invoke(userId: Long): List<CreatedProblem>
}

internal class GetCreatedProblemsUseCaseImpl(
    private val repository: ProblemReadRepository,
) : GetCreatedProblemsUseCase {
    override operator fun invoke(userId: Long): List<CreatedProblem> {
        return repository.fetchCreatedProblemsForUser(userId)
    }
}
