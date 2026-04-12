package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface RunProblemCodeUseCase {
    suspend operator fun invoke(problemId: Int, sourceCode: String, language: String): RunProblemResponseDto
}

class RunProblemCodeUseCaseImpl(
    private val repository: ProblemRepository,
) : RunProblemCodeUseCase {
    override suspend fun invoke(problemId: Int, sourceCode: String, language: String): RunProblemResponseDto {
        return repository.runProblemCode(
            problemId = problemId,
            sourceCode = sourceCode,
            language = language,
        )
    }
}
