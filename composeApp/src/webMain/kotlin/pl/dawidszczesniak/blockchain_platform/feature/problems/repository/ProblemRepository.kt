package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

interface ProblemRepository {
    suspend fun login()
    suspend fun fetchProblems(): List<ProblemSummary>
    suspend fun fetchCreatedProblems(): List<CreatedProblem>
    suspend fun fetchParticipationProblems(): List<ParticipationProblem>
    suspend fun createProblem(request: CreateProblemRequestDto): Int
}
