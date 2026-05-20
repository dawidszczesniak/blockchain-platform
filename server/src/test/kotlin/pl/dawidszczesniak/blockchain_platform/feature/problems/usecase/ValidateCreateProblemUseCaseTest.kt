package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemTestCaseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto

class ValidateCreateProblemUseCaseTest {
    @Test
    fun `returns consensus runtime and memory in validation response`() {
        val useCase = ValidateCreateProblemUseCaseImpl(
            referenceValidationService = FakeValidateCreateProblemReferenceValidationService(),
            cancellationRegistry = CreateProblemValidationCancellationRegistryImpl(),
        )

        val response = useCase(
            userId = 7L,
            request = ValidateCreateProblemRequestDto(
                referenceSolutionCode = "fun solve(input: String): String = input.trim()",
                testCases = listOf(
                    CreateProblemTestCaseDto(
                        inputData = "5",
                        isHidden = false,
                    )
                ),
            ),
        )

        assertEquals(1, response.total)
        assertEquals(1, response.successful)
        assertEquals(true, response.allSuccessful)
        assertEquals(105, response.runtimeMs)
        assertEquals(1400, response.memoryUsedKb)
    }
}

private class FakeValidateCreateProblemReferenceValidationService : CreateProblemReferenceValidationService {
    override fun validateReferenceSolution(
        referenceSolutionLanguage: String,
        referenceSolutionCode: String,
        testCases: List<CreateProblemTestCaseDto>,
        requireDeterminism: Boolean,
        validationRunId: String?,
        cancellation: SandboxRunCancellation?,
    ): CreateProblemReferenceValidationResult {
        return CreateProblemReferenceValidationResult(
            tests = listOf(
                CreateProblemReferenceTestResult(
                    index = 1,
                    status = CreateProblemReferenceTestStatus.Ok,
                    output = "5",
                    executionTimeMs = 19,
                    memoryUsedKb = 512,
                    message = null,
                )
            ),
            evidence = CreateProblemReferenceValidationEvidence(
                nodeId = "sandbox-node-1",
                runHash = "0xrun",
                resultHash = "0xresult",
                imageHash = "0ximage",
                consensusNodes = 3,
                runtimeMs = 105,
                memoryUsedKb = 1400,
                validatedAt = Instant.parse("2026-05-19T00:00:00Z"),
            ),
        )
    }
}
