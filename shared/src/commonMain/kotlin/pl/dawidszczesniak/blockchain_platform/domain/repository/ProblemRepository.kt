package pl.dawidszczesniak.blockchain_platform.domain.repository

import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary

interface ProblemRepository {
    suspend fun fetchProblems(): List<ProblemSummary>
    suspend fun fetchCreatedProblems(): List<CreatedProblem>
    suspend fun fetchParticipationProblems(): List<ParticipationProblem>
}
