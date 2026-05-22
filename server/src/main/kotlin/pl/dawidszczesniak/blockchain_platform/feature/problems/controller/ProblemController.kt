package pl.dawidszczesniak.blockchain_platform.feature.problems.controller

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CancelCreateProblemValidationRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreatedProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ParticipationProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareJoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemSummaryDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.mapper.toDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CancelCreateProblemValidationUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.EnqueueProblemSubmissionUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetSubmissionJudgeJobUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummaryByIdUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RetrySubmissionJudgeJobUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareCreateProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmCreateProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareJoinProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmJoinProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ValidateCreateProblemUseCase

internal class ProblemController(
    private val getProblemSummariesUseCase: GetProblemSummariesUseCase,
    private val getProblemSummaryByIdUseCase: GetProblemSummaryByIdUseCase,
    private val getCreatedProblemsUseCase: GetCreatedProblemsUseCase,
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
    private val createProblemUseCase: CreateProblemUseCase,
    private val prepareCreateProblemOnChainUseCase: PrepareCreateProblemOnChainUseCase,
    private val confirmCreateProblemOnChainUseCase: ConfirmCreateProblemOnChainUseCase,
    private val validateCreateProblemUseCase: ValidateCreateProblemUseCase,
    private val cancelCreateProblemValidationUseCase: CancelCreateProblemValidationUseCase,
    private val joinProblemUseCase: JoinProblemUseCase,
    private val prepareJoinProblemOnChainUseCase: PrepareJoinProblemOnChainUseCase,
    private val confirmJoinProblemOnChainUseCase: ConfirmJoinProblemOnChainUseCase,
    private val runProblemCodeUseCase: RunProblemCodeUseCase,
    private val enqueueProblemSubmissionUseCase: EnqueueProblemSubmissionUseCase,
    private val getSubmissionJudgeJobUseCase: GetSubmissionJudgeJobUseCase,
    private val retrySubmissionJudgeJobUseCase: RetrySubmissionJudgeJobUseCase,
) {
    fun getProblemSummaries(): List<ProblemSummaryDto> {
        return getProblemSummariesUseCase().map { it.toDto() }
    }

    fun getProblemSummary(problemId: Int): ProblemSummaryDto {
        return getProblemSummaryByIdUseCase(problemId).toDto()
    }

    fun getCreatedProblems(userId: Long): List<CreatedProblemDto> {
        return getCreatedProblemsUseCase(userId).map { it.toDto() }
    }

    fun getParticipationProblems(userId: Long): List<ParticipationProblemDto> {
        return getParticipationProblemsUseCase(userId).map { it.toDto() }
    }

    fun createProblem(userId: Long, request: CreateProblemRequestDto): CreateProblemResponseDto {
        val createdId = createProblemUseCase(userId, request)
        return CreateProblemResponseDto(id = createdId)
    }

    fun prepareCreateProblemOnChain(
        userId: Long,
        walletAddress: String,
        request: CreateProblemRequestDto,
    ): PrepareCreateProblemResponseDto {
        return prepareCreateProblemOnChainUseCase(userId, walletAddress, request)
    }

    fun confirmCreateProblemOnChain(
        userId: Long,
        walletAddress: String,
        request: ConfirmCreateProblemRequestDto,
    ): CreateProblemResponseDto {
        return confirmCreateProblemOnChainUseCase(userId, walletAddress, request)
    }

    fun validateCreateProblem(
        userId: Long,
        request: ValidateCreateProblemRequestDto,
    ): ValidateCreateProblemResponseDto {
        return validateCreateProblemUseCase(userId, request)
    }

    fun cancelCreateProblemValidation(
        userId: Long,
        request: CancelCreateProblemValidationRequestDto,
    ) {
        cancelCreateProblemValidationUseCase(userId, request.validationRunId)
    }

    fun joinProblem(userId: Long, problemId: Int): JoinProblemResponseDto {
        val result = joinProblemUseCase(userId, problemId)
        return JoinProblemResponseDto(
            joined = result.joined,
            registeredParticipants = result.registeredParticipants,
            requiredParticipants = result.requiredParticipants,
        )
    }

    fun prepareJoinProblemOnChain(
        userId: Long,
        walletAddress: String,
        problemId: Int,
    ): PrepareJoinProblemResponseDto {
        return prepareJoinProblemOnChainUseCase(userId, walletAddress, problemId)
    }

    fun confirmJoinProblemOnChain(
        userId: Long,
        walletAddress: String,
        problemId: Int,
        request: ConfirmJoinProblemRequestDto,
    ): JoinProblemResponseDto {
        return confirmJoinProblemOnChainUseCase(userId, walletAddress, problemId, request)
    }

    fun runProblemCode(
        userId: Long,
        problemId: Int,
        request: RunProblemRequestDto,
    ): RunProblemResponseDto {
        return runProblemCodeUseCase(userId, problemId, request)
    }

    fun submitProblemCode(
        userId: Long,
        problemId: Int,
        request: RunProblemRequestDto,
    ): SubmissionJudgeJobDto {
        return enqueueProblemSubmissionUseCase(userId, problemId, request)
    }

    fun getSubmissionJudgeJob(
        userId: Long,
        jobId: Long,
    ): SubmissionJudgeJobDto {
        return getSubmissionJudgeJobUseCase(userId, jobId)
    }

    fun retrySubmissionJudgeJob(
        userId: Long,
        jobId: Long,
    ): SubmissionJudgeJobDto {
        return retrySubmissionJudgeJobUseCase(userId, jobId)
    }
}
