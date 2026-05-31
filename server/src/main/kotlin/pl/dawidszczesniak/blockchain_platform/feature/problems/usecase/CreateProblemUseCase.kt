package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto

internal class CreateProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface CreateProblemUseCase {
    operator fun invoke(userId: Long, request: CreateProblemRequestDto): Int
}

internal class DisabledDirectCreateProblemUseCase : CreateProblemUseCase {
    override fun invoke(userId: Long, request: CreateProblemRequestDto): Int {
        throw CreateProblemValidationException(
            "Direct backend-only creation is disabled. Prepare and confirm the on-chain transaction instead."
        )
    }
}
