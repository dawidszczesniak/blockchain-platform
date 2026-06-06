package pl.dawidszczesniak.blockchain_platform.feature.problems.create

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface ValidateCreateProblemUseCase {
    suspend operator fun invoke(request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto
}

class ValidateCreateProblemUseCaseImpl(
    private val repository: ProblemRepository,
) : ValidateCreateProblemUseCase {
    override suspend fun invoke(request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto {
        return repository.validateCreateProblem(request)
    }
}
