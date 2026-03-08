package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import pl.dawidszczesniak.blockchain_platform.feature.problems.datasource.ProblemRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.mapper.toDomain

class ProblemRepositoryImpl(
    private val remoteDataSource: ProblemRemoteDataSource,
) : ProblemRepository {
    override suspend fun fetchProblems(): List<ProblemSummary> {
        return remoteDataSource.fetchProblems().map { it.toDomain() }
    }

    override suspend fun fetchCreatedProblems(): List<CreatedProblem> {
        return remoteDataSource.fetchCreatedProblems().map { it.toDomain() }
    }

    override suspend fun fetchParticipationProblems(): List<ParticipationProblem> {
        return remoteDataSource.fetchParticipationProblems().map { it.toDomain() }
    }

    override suspend fun createProblem(request: CreateProblemRequestDto): Int {
        return remoteDataSource.createProblem(request).id
    }
}
