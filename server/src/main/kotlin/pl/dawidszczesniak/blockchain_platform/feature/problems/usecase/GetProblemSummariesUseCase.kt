package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepository

internal interface GetProblemSummariesUseCase {
    operator fun invoke(): List<ProblemSummary>
}

internal class GetProblemSummariesUseCaseImpl(
    private val repository: ProblemReadRepository,
) : GetProblemSummariesUseCase {
    override operator fun invoke(): List<ProblemSummary> {
        return repository.fetchProblemSummaries()
    }
}
