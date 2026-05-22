package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import pl.dawidszczesniak.blockchain_platform.feature.problems.datasource.ProblemRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
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

    override suspend fun fetchProblemById(problemId: Int): ProblemSummary {
        return remoteDataSource.fetchProblemById(problemId).toDomain()
    }

    override suspend fun fetchCreatedProblems(): List<CreatedProblem> {
        return remoteDataSource.fetchCreatedProblems().map { it.toDomain() }
    }

    override suspend fun fetchParticipationProblems(): List<ParticipationProblem> {
        return remoteDataSource.fetchParticipationProblems().map { it.toDomain() }
    }

    override suspend fun prepareCreateProblemOnChain(request: CreateProblemRequestDto) =
        remoteDataSource.prepareCreateProblemOnChain(request)

    override suspend fun confirmCreateProblemOnChain(request: ConfirmCreateProblemRequestDto): Int {
        return remoteDataSource.confirmCreateProblemOnChain(request).id
    }

    override suspend fun validateCreateProblem(request: ValidateCreateProblemRequestDto) =
        remoteDataSource.validateCreateProblem(request)

    override suspend fun cancelCreateProblemValidation(runId: String) =
        remoteDataSource.cancelCreateProblemValidation(runId)

    override suspend fun prepareJoinProblemOnChain(problemId: Int) =
        remoteDataSource.prepareJoinProblemOnChain(problemId)

    override suspend fun confirmJoinProblemOnChain(problemId: Int, request: ConfirmJoinProblemRequestDto) =
        remoteDataSource.confirmJoinProblemOnChain(problemId, request)

    override suspend fun runProblemCode(problemId: Int, sourceCode: String, language: String) =
        remoteDataSource.runProblemCode(
            problemId = problemId,
            request = RunProblemRequestDto(sourceCode = sourceCode, language = language),
        )

    override suspend fun submitProblemCode(problemId: Int, sourceCode: String, language: String) =
        remoteDataSource.submitProblemCode(
            problemId = problemId,
            request = RunProblemRequestDto(sourceCode = sourceCode, language = language),
        )

    override suspend fun fetchSubmissionJudgeJob(jobId: Long) =
        remoteDataSource.fetchSubmissionJudgeJob(jobId)

    override suspend fun retrySubmissionJudgeJob(jobId: Long) =
        remoteDataSource.retrySubmissionJudgeJob(jobId)
}
