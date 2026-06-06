package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface GetParticipationProblemsUseCase {
    suspend operator fun invoke(): List<ParticipationProblem>
}

class GetParticipationProblemsUseCaseImpl(
    private val repository: ProblemRepository,
) : GetParticipationProblemsUseCase {
    override suspend operator fun invoke(): List<ParticipationProblem> {
        return repository.fetchParticipationProblems()
    }
}
