package pl.dawidszczesniak.blockchain_platform.feature.problems.controller

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase

internal class ProblemController(
    private val getProblemSummariesUseCase: GetProblemSummariesUseCase,
    private val getCreatedProblemsUseCase: GetCreatedProblemsUseCase,
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
) {
    fun getProblemSummaries(): List<ProblemSummary> {
        return getProblemSummariesUseCase()
    }

    fun getCreatedProblems(): List<CreatedProblem> {
        return getCreatedProblemsUseCase()
    }

    fun getParticipationProblems(): List<ParticipationProblem> {
        return getParticipationProblemsUseCase()
    }
}
