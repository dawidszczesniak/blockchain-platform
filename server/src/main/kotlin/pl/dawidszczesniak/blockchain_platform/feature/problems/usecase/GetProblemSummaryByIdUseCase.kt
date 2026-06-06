package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepository

internal interface GetProblemSummaryByIdUseCase {
    operator fun invoke(problemId: Int): ProblemSummary
}

internal class GetProblemSummaryByIdUseCaseImpl(
    private val repository: ProblemReadRepository,
) : GetProblemSummaryByIdUseCase {
    override fun invoke(problemId: Int): ProblemSummary {
        return repository.fetchProblemSummaryById(problemId)
            ?: throw IllegalArgumentException("Problem not found.")
    }
}
