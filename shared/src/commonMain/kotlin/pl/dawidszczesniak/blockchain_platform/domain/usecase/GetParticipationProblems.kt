package pl.dawidszczesniak.blockchain_platform.domain.usecase

import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.repository.ProblemRepository

class GetParticipationProblems(
    private val repository: ProblemRepository,
) {
    suspend operator fun invoke(): List<ParticipationProblem> {
        return repository.fetchParticipationProblems()
    }
}
