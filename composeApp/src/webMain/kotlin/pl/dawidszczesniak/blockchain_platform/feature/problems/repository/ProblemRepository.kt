package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

interface ProblemRepository {
    suspend fun fetchProblems(): List<ProblemSummary>
    suspend fun fetchProblemById(problemId: Int): ProblemSummary
    suspend fun fetchCreatedProblems(): List<CreatedProblem>
    suspend fun fetchParticipationProblems(): List<ParticipationProblem>
    suspend fun createProblem(request: CreateProblemRequestDto): Int
    suspend fun validateCreateProblem(request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto
    suspend fun joinProblem(problemId: Int): JoinProblemResponseDto
    suspend fun runProblemCode(problemId: Int, sourceCode: String, language: String): RunProblemResponseDto
    suspend fun submitProblemCode(problemId: Int, sourceCode: String, language: String): SubmissionJudgeJobDto
    suspend fun fetchSubmissionJudgeJob(jobId: Long): SubmissionJudgeJobDto
}
