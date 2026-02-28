package pl.dawidszczesniak.blockchain_platform.domain.usecase

import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.domain.repository.ProblemRepository

class GetProblemListUseCase(
    private val repository: ProblemRepository,
) {
    suspend operator fun invoke(): List<ProblemSummary> {
        return repository.fetchProblems()
    }
}
