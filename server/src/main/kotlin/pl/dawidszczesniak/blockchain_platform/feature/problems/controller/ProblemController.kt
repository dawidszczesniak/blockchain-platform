package pl.dawidszczesniak.blockchain_platform.feature.problems.controller

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreatedProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ParticipationProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemSummaryDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.mapper.toDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCase

internal class ProblemController(
    private val getProblemSummariesUseCase: GetProblemSummariesUseCase,
    private val getCreatedProblemsUseCase: GetCreatedProblemsUseCase,
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
    private val createProblemUseCase: CreateProblemUseCase,
    private val joinProblemUseCase: JoinProblemUseCase,
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

    fun joinProblem(userId: Long, problemId: Int): JoinProblemResponseDto {
        val result = joinProblemUseCase(userId, problemId)
        return JoinProblemResponseDto(
            joined = result.joined,
            registeredParticipants = result.registeredParticipants,
            requiredParticipants = result.requiredParticipants,
        )
    }
}
