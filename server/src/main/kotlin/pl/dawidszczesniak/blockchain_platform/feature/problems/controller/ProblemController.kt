package pl.dawidszczesniak.blockchain_platform.feature.problems.controller

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreatedProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ParticipationProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemSummaryDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.mapper.toDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase

internal class ProblemController(
    private val getProblemSummariesUseCase: GetProblemSummariesUseCase,
    private val getCreatedProblemsUseCase: GetCreatedProblemsUseCase,
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
    private val createProblemUseCase: CreateProblemUseCase,
) {
    fun getProblemSummaries(): List<ProblemSummaryDto> {
        return getProblemSummariesUseCase().map { it.toDto() }
    }

    fun getCreatedProblems(): List<CreatedProblemDto> {
        return getCreatedProblemsUseCase().map { it.toDto() }
    }

    fun getParticipationProblems(): List<ParticipationProblemDto> {
        return getParticipationProblemsUseCase().map { it.toDto() }
    }

    fun createProblem(request: CreateProblemRequestDto): CreateProblemResponseDto {
        val createdId = createProblemUseCase(request)
        return CreateProblemResponseDto(id = createdId)
    }
}
