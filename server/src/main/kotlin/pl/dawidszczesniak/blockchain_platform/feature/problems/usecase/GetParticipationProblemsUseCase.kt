package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepository

internal interface GetParticipationProblemsUseCase {
    operator fun invoke(): List<ParticipationProblem>
}

internal class GetParticipationProblemsUseCaseImpl(
    private val repository: ProblemReadRepository,
) : GetParticipationProblemsUseCase {
    override operator fun invoke(): List<ParticipationProblem> {
        return repository.fetchParticipationProblemsForDefaultUser()
    }
}
