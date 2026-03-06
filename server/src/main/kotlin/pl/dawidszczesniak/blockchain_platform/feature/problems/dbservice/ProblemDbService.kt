package pl.dawidszczesniak.blockchain_platform.feature.problems.dbservice

import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemDao
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

internal interface ProblemDbService {
    fun fetchProblemSummaries(): List<ProblemSummary>
    fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem>
    fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem>
}

internal class ProblemDbServiceImpl(
    private val problemDao: ProblemDao,
) : ProblemDbService {
    override fun fetchProblemSummaries(): List<ProblemSummary> {
        return problemDao.fetchProblemSummaries()
    }

    override fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem> {
        return problemDao.fetchCreatedProblemsForDefaultUser()
    }

    override fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem> {
        return problemDao.fetchParticipationProblemsForDefaultUser()
    }
}
