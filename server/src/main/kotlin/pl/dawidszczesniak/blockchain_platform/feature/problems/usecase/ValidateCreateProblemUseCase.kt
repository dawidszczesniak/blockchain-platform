package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemValidationTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto

internal interface ValidateCreateProblemUseCase {
    operator fun invoke(userId: Long, request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto
}

internal class ValidateCreateProblemUseCaseImpl(
    private val referenceValidationService: CreateProblemReferenceValidationService,
) : ValidateCreateProblemUseCase {
    override fun invoke(userId: Long, request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto {
        val validationResult = referenceValidationService.validateReferenceSolution(
            referenceSolutionLanguage = request.referenceSolutionLanguage,
            referenceSolutionCode = request.referenceSolutionCode,
            testCases = request.testCases,
            requireDeterminism = false,
        )
        val results = validationResult.tests.map { test ->
            CreateProblemValidationTestResultDto(
                index = test.index,
                status = test.status.toApiStatus(),
                output = test.output,
                executionTimeMs = test.executionTimeMs,
                message = test.message,
            )
        }
        val successful = validationResult.tests.count { it.status == CreateProblemReferenceTestStatus.Ok && it.output != null }
        return ValidateCreateProblemResponseDto(
            total = results.size,
            successful = successful,
            allSuccessful = successful == results.size,
            results = results,
            sandboxNodeId = validationResult.evidence.nodeId,
            sandboxImageHash = validationResult.evidence.imageHash,
            sandboxRunHash = validationResult.evidence.runHash,
        )
    }
}

private fun CreateProblemReferenceTestStatus.toApiStatus(): String {
    return when (this) {
        CreateProblemReferenceTestStatus.Ok -> "OK"
        CreateProblemReferenceTestStatus.Timeout -> "TIMEOUT"
        CreateProblemReferenceTestStatus.Error -> "ERROR"
    }
}
