package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemValidationTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto

internal interface ValidateCreateProblemUseCase {
    operator fun invoke(userId: Long, request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto
}

internal class ValidateCreateProblemUseCaseImpl(
    private val referenceValidationService: CreateProblemReferenceValidationService,
    private val cancellationRegistry: CreateProblemValidationCancellationRegistry,
) : ValidateCreateProblemUseCase {
    override fun invoke(userId: Long, request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto {
        val validationRunId = request.validationRunId?.trim()?.ifBlank { null }
        val cancellation = validationRunId?.let { cancellationRegistry.open(userId = userId, runId = it) }
        val validationResult = try {
            referenceValidationService.validateReferenceSolution(
                referenceSolutionLanguage = request.referenceSolutionLanguage,
                referenceSolutionCode = request.referenceSolutionCode,
                testCases = request.testCases,
                requireDeterminism = false,
                validationRunId = validationRunId,
                cancellation = cancellation,
            )
        } finally {
            if (validationRunId != null) {
                cancellationRegistry.finish(userId = userId, runId = validationRunId)
            }
        }
        val results = validationResult.tests.map { test ->
            CreateProblemValidationTestResultDto(
                index = test.index,
                status = test.status.toApiStatus(),
                output = test.output,
                executionTimeMs = test.executionTimeMs,
                memoryUsedKb = test.memoryUsedKb,
                message = test.message,
            )
        }
        val successful = validationResult.tests.count { it.status == CreateProblemReferenceTestStatus.Ok && it.output != null }
        return ValidateCreateProblemResponseDto(
            total = results.size,
            successful = successful,
            allSuccessful = successful == results.size,
            results = results,
            runtimeMs = validationResult.evidence.runtimeMs,
            memoryUsedKb = validationResult.evidence.memoryUsedKb,
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
