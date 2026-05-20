package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemTestCaseDto

class CreateProblemDraftFactoryTest {
    @Test
    fun `requires non blank title`() {
        val validationService = FakeCreateProblemReferenceValidationService()
        val factory = CreateProblemDraftFactory(
            referenceValidationService = validationService,
            paymentAssetCatalog = PaymentAssetCatalog.fromEnvironment(
                env = emptyMap(),
                blockchainConfig = BlockchainConfig.fromEnvironment(emptyMap()),
            ),
        )

        val error = assertFailsWith<CreateProblemValidationException> {
            factory.build(
                request = CreateProblemRequestDto(
                    title = "   ",
                    description = "Count the number of set bits.",
                    constraints = "",
                    referenceSolutionCode = "fun solve(input: String): String = input",
                    paymentAssetCode = "ETH",
                    prizeAmountAtomic = "1000000000000000000",
                    entryFeeAmountAtomic = "0",
                    requiredParticipants = 1,
                    joinUntilDate = LocalDate(2026, 5, 17),
                    submitUntilDate = LocalDate(2026, 5, 18),
                    testCases = listOf(
                        CreateProblemTestCaseDto(
                            inputData = "5",
                            isHidden = false,
                        )
                    ),
                ),
                creatorWalletAddress = "0x1111111111111111111111111111111111111111",
            )
        }

        assertEquals("Title is required.", error.message)
        assertFalse(validationService.called)
    }

    @Test
    fun `preserves exact reference solution formatting and benchmark metrics`() {
        val rawReferenceCode = "\nfun solve(input: String): String {\n    return input.trim()\n}\n"
        val validationService = FakeCreateProblemReferenceValidationService(
            result = CreateProblemReferenceValidationResult(
                tests = listOf(
                    CreateProblemReferenceTestResult(
                        index = 1,
                        status = CreateProblemReferenceTestStatus.Ok,
                        output = "5",
                        executionTimeMs = 17,
                        memoryUsedKb = 640,
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
                    validatedAt = Instant.parse("2026-05-17T00:00:00Z"),
                ),
            )
        )
        val factory = CreateProblemDraftFactory(
            referenceValidationService = validationService,
            paymentAssetCatalog = PaymentAssetCatalog.fromEnvironment(
                env = emptyMap(),
                blockchainConfig = BlockchainConfig.fromEnvironment(emptyMap()),
            ),
        )

        val draft = factory.build(
            request = CreateProblemRequestDto(
                title = "Bit count",
                description = "Count bits.",
                constraints = "",
                referenceSolutionCode = rawReferenceCode,
                paymentAssetCode = "ETH",
                prizeAmountAtomic = "1000000000000000000",
                entryFeeAmountAtomic = "0",
                requiredParticipants = 1,
                joinUntilDate = LocalDate(2026, 5, 17),
                submitUntilDate = LocalDate(2026, 5, 18),
                testCases = listOf(
                    CreateProblemTestCaseDto(
                        inputData = "5",
                        isHidden = false,
                    )
                ),
            ),
            creatorWalletAddress = "0x1111111111111111111111111111111111111111",
        )

        assertTrue(validationService.called)
        assertEquals(rawReferenceCode, draft.referenceSolutionCode)
        assertEquals(105, draft.referenceRuntimeMs)
        assertEquals(1400, draft.referenceMemoryUsedKb)
        assertEquals(3, draft.referenceConsensusNodes)
    }
}

private class FakeCreateProblemReferenceValidationService(
    private val result: CreateProblemReferenceValidationResult = CreateProblemReferenceValidationResult(
        tests = emptyList(),
        evidence = CreateProblemReferenceValidationEvidence(
            nodeId = null,
            runHash = null,
            resultHash = null,
            imageHash = null,
            consensusNodes = 3,
            runtimeMs = 123,
            memoryUsedKb = 456,
            validatedAt = Instant.parse("2026-05-17T00:00:00Z"),
        ),
    ),
) : CreateProblemReferenceValidationService {
    var called: Boolean = false

    override fun validateReferenceSolution(
        referenceSolutionLanguage: String,
        referenceSolutionCode: String,
        testCases: List<CreateProblemTestCaseDto>,
        requireDeterminism: Boolean,
        validationRunId: String?,
        cancellation: SandboxRunCancellation?,
    ): CreateProblemReferenceValidationResult {
        called = true
        return result
    }
}
