package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepository

internal interface GetParticipationProblemsUseCase {
    operator fun invoke(userId: Long): List<ParticipationProblem>
}

internal class GetParticipationProblemsUseCaseImpl(
    private val repository: ProblemReadRepository,
) : GetParticipationProblemsUseCase {
    override operator fun invoke(userId: Long): List<ParticipationProblem> {
        return repository.fetchParticipationProblemsForUser(userId)
    }
}
