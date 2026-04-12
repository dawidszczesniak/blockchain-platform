package pl.dawidszczesniak.blockchain_platform.feature.problems.controller

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreatedProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ParticipationProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemSummaryDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.mapper.toDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.EnqueueProblemSubmissionUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetSubmissionJudgeJobUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ValidateCreateProblemUseCase

internal class ProblemController(
    private val getProblemSummariesUseCase: GetProblemSummariesUseCase,
    private val getCreatedProblemsUseCase: GetCreatedProblemsUseCase,
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
    private val createProblemUseCase: CreateProblemUseCase,
    private val validateCreateProblemUseCase: ValidateCreateProblemUseCase,
    private val joinProblemUseCase: JoinProblemUseCase,
    private val runProblemCodeUseCase: RunProblemCodeUseCase,
    private val enqueueProblemSubmissionUseCase: EnqueueProblemSubmissionUseCase,
    private val getSubmissionJudgeJobUseCase: GetSubmissionJudgeJobUseCase,
) {
    fun getProblemSummaries(): List<ProblemSummaryDto> {
        return getProblemSummariesUseCase().map { it.toDto() }
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

    fun validateCreateProblem(
        userId: Long,
        request: ValidateCreateProblemRequestDto,
    ): ValidateCreateProblemResponseDto {
        return validateCreateProblemUseCase(userId, request)
    }

    fun joinProblem(userId: Long, problemId: Int): JoinProblemResponseDto {
        val result = joinProblemUseCase(userId, problemId)
        return JoinProblemResponseDto(
            joined = result.joined,
            registeredParticipants = result.registeredParticipants,
            requiredParticipants = result.requiredParticipants,
        )
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
}
