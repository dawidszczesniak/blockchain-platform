package pl.dawidszczesniak.blockchain_platform.feature.problems.create

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface CreateProblemUseCase {
    suspend operator fun invoke(request: CreateProblemRequestDto): Int
}

class CreateProblemUseCaseImpl(
    private val repository: ProblemRepository,
) : CreateProblemUseCase {
    override suspend fun invoke(request: CreateProblemRequestDto): Int {
        return repository.createProblem(request)
    }
}
