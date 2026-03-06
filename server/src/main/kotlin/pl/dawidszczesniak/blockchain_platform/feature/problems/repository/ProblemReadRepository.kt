package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import pl.dawidszczesniak.blockchain_platform.feature.problems.dbservice.ProblemDbService
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

internal interface ProblemReadRepository {
    fun fetchProblemSummaries(): List<ProblemSummary>
    fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem>
    fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem>
}

internal class ProblemReadRepositoryImpl(
    private val dbService: ProblemDbService,
) : ProblemReadRepository {
    override fun fetchProblemSummaries(): List<ProblemSummary> {
        return dbService.fetchProblemSummaries()
    }

    override fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem> {
        return dbService.fetchCreatedProblemsForDefaultUser()
    }

    override fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem> {
        return dbService.fetchParticipationProblemsForDefaultUser()
    }
}
