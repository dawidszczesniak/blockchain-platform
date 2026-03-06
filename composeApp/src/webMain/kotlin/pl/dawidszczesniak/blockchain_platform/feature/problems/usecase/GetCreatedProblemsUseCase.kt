package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface GetCreatedProblemsUseCase {
    suspend operator fun invoke(): List<CreatedProblem>
}

class GetCreatedProblemsUseCaseImpl(
    private val repository: ProblemRepository,
) : GetCreatedProblemsUseCase {
    override suspend operator fun invoke(): List<CreatedProblem> {
        return repository.fetchCreatedProblems()
    }
}
