package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface GetProblemListUseCase {
    suspend operator fun invoke(): List<ProblemSummary>
}

class GetProblemListUseCaseImpl(
    private val repository: ProblemRepository,
) : GetProblemListUseCase {
    override suspend operator fun invoke(): List<ProblemSummary> {
        return repository.fetchProblems()
    }
}
