package pl.dawidszczesniak.blockchain_platform.domain.usecase

import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.repository.ProblemRepository

class GetCreatedProblems(
    private val repository: ProblemRepository,
) {
    suspend operator fun invoke(): List<CreatedProblem> {
        return repository.fetchCreatedProblems()
    }
}
