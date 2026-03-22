package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface SubmitProblemCodeUseCase {
    suspend operator fun invoke(problemId: Int, sourceCode: String): SubmitProblemResponseDto
}

class SubmitProblemCodeUseCaseImpl(
    private val repository: ProblemRepository,
) : SubmitProblemCodeUseCase {
    override suspend fun invoke(problemId: Int, sourceCode: String): SubmitProblemResponseDto {
        return repository.submitProblemCode(
            problemId = problemId,
            sourceCode = sourceCode,
        )
    }
}
