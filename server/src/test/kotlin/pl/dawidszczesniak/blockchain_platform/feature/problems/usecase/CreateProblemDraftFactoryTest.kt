package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
}

private class FakeCreateProblemReferenceValidationService : CreateProblemReferenceValidationService {
    var called: Boolean = false

    override fun validateReferenceSolution(
        referenceSolutionLanguage: String,
        referenceSolutionCode: String,
        testCases: List<CreateProblemTestCaseDto>,
        requireDeterminism: Boolean,
    ): CreateProblemReferenceValidationResult {
        called = true
        return CreateProblemReferenceValidationResult(
            tests = emptyList(),
            evidence = CreateProblemReferenceValidationEvidence(
                nodeId = null,
                runHash = null,
                resultHash = null,
                imageHash = null,
                validatedAt = Instant.parse("2026-05-17T00:00:00Z"),
            ),
        )
    }
}
