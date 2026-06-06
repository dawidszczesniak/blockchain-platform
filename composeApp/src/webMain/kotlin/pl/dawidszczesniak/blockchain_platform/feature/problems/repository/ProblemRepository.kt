package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCompetitionLifecycleActionRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmSubmissionResultRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CompetitionLifecycleActionResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCompetitionLifecycleActionResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareJoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

interface ProblemRepository {
    suspend fun fetchProblems(): List<ProblemSummary>
    suspend fun fetchProblemById(problemId: Int): ProblemSummary
    suspend fun fetchCreatedProblems(): List<CreatedProblem>
    suspend fun fetchParticipationProblems(): List<ParticipationProblem>
    suspend fun prepareCreateProblemOnChain(request: CreateProblemRequestDto): PrepareCreateProblemResponseDto
    suspend fun confirmCreateProblemOnChain(request: ConfirmCreateProblemRequestDto): Int
    suspend fun validateCreateProblem(request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto
    suspend fun cancelCreateProblemValidation(runId: String)
    suspend fun prepareJoinProblemOnChain(problemId: Int): PrepareJoinProblemResponseDto
    suspend fun confirmJoinProblemOnChain(problemId: Int, request: ConfirmJoinProblemRequestDto): JoinProblemResponseDto
    suspend fun prepareSettleCompetitionOnChain(problemId: Int): PrepareCompetitionLifecycleActionResponseDto
    suspend fun confirmSettleCompetitionOnChain(
        problemId: Int,
        request: ConfirmCompetitionLifecycleActionRequestDto,
    ): CompetitionLifecycleActionResponseDto
    suspend fun prepareCancelCompetitionOnChain(problemId: Int): PrepareCompetitionLifecycleActionResponseDto
    suspend fun confirmCancelCompetitionOnChain(
        problemId: Int,
        request: ConfirmCompetitionLifecycleActionRequestDto,
    ): CompetitionLifecycleActionResponseDto
    suspend fun runProblemCode(problemId: Int, sourceCode: String, language: String): RunProblemResponseDto
    suspend fun submitProblemCode(problemId: Int, sourceCode: String, language: String): SubmissionJudgeJobDto
    suspend fun confirmSubmissionOnChain(submissionId: Long, request: ConfirmSubmissionResultRequestDto): SubmitProblemResponseDto
    suspend fun fetchSubmissionJudgeJob(jobId: Long): SubmissionJudgeJobDto
    suspend fun retrySubmissionJudgeJob(jobId: Long): SubmissionJudgeJobDto
}
