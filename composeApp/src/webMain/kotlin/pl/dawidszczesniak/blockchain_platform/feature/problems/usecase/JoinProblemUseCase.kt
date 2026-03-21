package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface JoinProblemUseCase {
    suspend operator fun invoke(problemId: Int): JoinProblemResponseDto
}

class JoinProblemUseCaseImpl(
    private val repository: ProblemRepository,
) : JoinProblemUseCase {
    override suspend fun invoke(problemId: Int): JoinProblemResponseDto {
        return repository.joinProblem(problemId)
    }
}
