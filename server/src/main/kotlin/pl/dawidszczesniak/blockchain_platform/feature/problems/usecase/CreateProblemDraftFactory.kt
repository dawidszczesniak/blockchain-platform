package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.datetime.toJavaLocalDate
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.normalizeAtomicAmount
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.PreparedCreateProblemIntent
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.StoredProblemExampleDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.StoredProblemTestDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemExampleDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemTestDraft

internal data class ValidatedCreateProblemDraft(
    val title: String,
    val description: String,
    val constraints: String,
    val examples: List<StoredProblemExampleDraft>,
    val referenceSolutionCode: String,
    val referenceSolutionHash: String,
    val referenceRuntimeMs: Int,
    val referenceMemoryUsedKb: Int?,
    val referenceConsensusNodes: Int,
    val validationNodeId: String?,
    val validationRunHash: String?,
    val validationResultHash: String?,
    val validationImageHash: String?,
    val validatedAt: java.time.Instant,
    val paymentAsset: PaymentAssetConfig,
    val prizeAmountAtomic: String,
    val entryFeeAmountAtomic: String,
    val requiredParticipants: Int,
    val joinUntilDate: LocalDate,
    val submitUntilDate: LocalDate,
    val tests: List<StoredProblemTestDraft>,
    val competitionKey: String,
) {
    fun toNewProblemDraft(
        onchainCompetitionId: Long? = null,
        onchainContractAddress: String? = null,
        onchainCreationKey: String? = null,
        onchainCreationTxHash: String? = null,
        onchainCreationFromWallet: String? = null,
        onchainCreationConfirmedAt: java.time.Instant? = null,
    ): NewProblemDraft {
        return NewProblemDraft(
            title = title,
            description = description,
            constraints = constraints,
            examples = examples.map { example ->
                NewProblemExampleDraft(
                    input = example.input,
                    output = example.output,
                    explanation = example.explanation,
                )
            },
            referenceSolutionCode = referenceSolutionCode,
            referenceSolutionHash = referenceSolutionHash,
            referenceRuntimeMs = referenceRuntimeMs,
            referenceMemoryUsedKb = referenceMemoryUsedKb,
            referenceConsensusNodes = referenceConsensusNodes,
            validationNodeId = validationNodeId,
            validationRunHash = validationRunHash,
            validationResultHash = validationResultHash,
            validationImageHash = validationImageHash,
            validatedAt = validatedAt,
            paymentAssetCode = paymentAsset.code,
            prizeAmountAtomic = prizeAmountAtomic,
            entryFeeAmountAtomic = entryFeeAmountAtomic,
            requiredParticipants = requiredParticipants,
            joinUntilDate = joinUntilDate,
            submitUntilDate = submitUntilDate,
            tests = tests.map { test ->
                NewProblemTestDraft(
                    inputData = test.inputData,
                    expectedOutput = test.expectedOutput,
                    validatorCode = test.validatorCode,
                    validatorLanguage = test.validatorLanguage,
                    isHidden = test.isHidden,
                    timeoutMs = test.timeoutMs,
                    memoryLimitMb = test.memoryLimitMb,
                )
            },
            onchainCompetitionId = onchainCompetitionId,
            onchainContractAddress = onchainContractAddress,
            onchainCreationKey = onchainCreationKey,
            onchainCreationTxHash = onchainCreationTxHash,
            onchainCreationFromWallet = onchainCreationFromWallet,
            onchainCreationConfirmedAt = onchainCreationConfirmedAt,
        )
    }

    fun toPreparedIntent(userId: Long, walletAddress: String): PreparedCreateProblemIntent {
        return PreparedCreateProblemIntent(
            intentId = "",
            userId = userId,
            walletAddress = walletAddress,
            title = title,
            description = description,
            constraints = constraints,
            examples = examples,
            referenceSolutionCode = referenceSolutionCode,
            referenceSolutionHash = referenceSolutionHash,
            referenceRuntimeMs = referenceRuntimeMs,
            referenceMemoryUsedKb = referenceMemoryUsedKb,
            referenceConsensusNodes = referenceConsensusNodes,
            validationNodeId = validationNodeId,
            validationRunHash = validationRunHash,
            validationResultHash = validationResultHash,
            validationImageHash = validationImageHash,
            validatedAt = validatedAt.toString(),
            paymentAssetCode = paymentAsset.code,
            prizeAmountAtomic = prizeAmountAtomic,
            entryFeeAmountAtomic = entryFeeAmountAtomic,
            requiredParticipants = requiredParticipants,
            joinUntilDate = joinUntilDate.toString(),
            submitUntilDate = submitUntilDate.toString(),
            tests = tests,
            competitionKey = competitionKey,
            expiresAt = "",
        )
    }
}

internal class CreateProblemDraftFactory(
    private val referenceValidationService: CreateProblemReferenceValidationService,
    private val paymentAssetCatalog: PaymentAssetCatalog,
) {
    fun build(request: CreateProblemRequestDto, creatorWalletAddress: String): ValidatedCreateProblemDraft {
        val rawTitle = request.title.trim()
        if (rawTitle.isBlank()) {
            throw CreateProblemValidationException("Title is required.")
        }
        if (rawTitle.length > MAX_TITLE_LENGTH) {
            throw CreateProblemValidationException("Title is too long. Max length is $MAX_TITLE_LENGTH characters.")
        }
        val description = request.description.trim()
        if (description.isBlank()) {
            throw CreateProblemValidationException("Description is required.")
        }
        if (description.length > MAX_DESCRIPTION_CHARS) {
            throw CreateProblemValidationException("Description is too long. Max length is $MAX_DESCRIPTION_CHARS characters.")
        }
        val paymentAsset = runCatching { paymentAssetCatalog.requireByCode(request.paymentAssetCode) }
            .getOrElse { error ->
                throw CreateProblemValidationException(error.message ?: "Unsupported payment asset.")
            }
        val prizeAmountAtomic = try {
            normalizeAtomicAmount(request.prizeAmountAtomic, "Prize amount", allowZero = false)
        } catch (error: IllegalArgumentException) {
            throw CreateProblemValidationException(error.message ?: "Prize amount is invalid.")
        }
        val entryFeeAmountAtomic = try {
            normalizeAtomicAmount(request.entryFeeAmountAtomic, "Entry fee amount")
        } catch (error: IllegalArgumentException) {
            throw CreateProblemValidationException(error.message ?: "Entry fee amount is invalid.")
        }
        if (request.requiredParticipants <= 0) {
            throw CreateProblemValidationException("Required participants must be greater than 0.")
        }

        val joinUntilDate = request.joinUntilDate
        val submitUntilDate = request.submitUntilDate
        if (submitUntilDate <= joinUntilDate) {
            throw CreateProblemValidationException("submitUntilDate must be later than joinUntilDate.")
        }

        val constraints = request.constraints.trim()
        if (constraints.length > MAX_CONSTRAINTS_CHARS) {
            throw CreateProblemValidationException(
                "Constraints are too long. Max length is $MAX_CONSTRAINTS_CHARS characters."
            )
        }

        if (request.tests.isNotEmpty()) {
            throw CreateProblemValidationException(
                "Legacy 'tests' format is no longer supported. Send structured 'testCases'."
            )
        }

        val publicTests = request.testCases.filterNot { it.isHidden }
        if (publicTests.size < MIN_PUBLIC_TEST_CASES) {
            throw CreateProblemValidationException("At least $MIN_PUBLIC_TEST_CASES tests must be public.")
        }

        val validationResult = referenceValidationService.validateReferenceSolution(
            referenceSolutionLanguage = request.referenceSolutionLanguage,
            referenceSolutionCode = request.referenceSolutionCode,
            testCases = request.testCases,
            requireDeterminism = true,
        )
        val expectedOutputs = validationResult.tests.map { testResult ->
            if (testResult.status != CreateProblemReferenceTestStatus.Ok || testResult.output == null) {
                val reason = testResult.message?.ifBlank { null } ?: "Execution failed."
                throw CreateProblemValidationException(
                    "Reference solution failed testCases[${testResult.index}]: $reason"
                )
            }
            testResult.output
        }
        val computedTests = request.testCases.zip(expectedOutputs).map { (testCase, expectedOutput) ->
            StoredProblemTestDraft(
                inputData = testCase.inputData,
                expectedOutput = expectedOutput,
                validatorCode = "",
                validatorLanguage = CREATE_PROBLEM_SUPPORTED_LANGUAGE,
                isHidden = testCase.isHidden,
                timeoutMs = DEFAULT_CREATE_PROBLEM_TEST_TIMEOUT_MS,
                memoryLimitMb = testCase.memoryLimitMb,
            )
        }

        val examples = request.testCases
            .mapIndexedNotNull { index, test ->
                if (test.isHidden) {
                    null
                } else {
                    test to expectedOutputs[index]
                }
            }
            .take(MAX_PROBLEM_EXAMPLES)
            .map { (test, expectedOutput) ->
                StoredProblemExampleDraft(
                    input = test.inputData,
                    output = expectedOutput,
                    explanation = GENERATED_EXAMPLE_EXPLANATION,
                )
            }

        val referenceSolutionCode = request.referenceSolutionCode
        val referenceSolutionHash = createProblemSha256Hex(referenceSolutionCode)
        val title = rawTitle
        val normalizedJoinDate = joinUntilDate.toJavaLocalDate()
        val normalizedSubmitDate = submitUntilDate.toJavaLocalDate()
        val competitionKey = buildCompetitionKey(
            creatorWalletAddress = creatorWalletAddress,
            title = title,
            description = description,
            constraints = constraints,
            referenceSolutionHash = referenceSolutionHash,
            paymentAssetCode = paymentAsset.code,
            prizeAmountAtomic = prizeAmountAtomic,
            entryFeeAmountAtomic = entryFeeAmountAtomic,
            requiredParticipants = request.requiredParticipants,
            joinUntilDate = normalizedJoinDate,
            submitUntilDate = normalizedSubmitDate,
            tests = computedTests,
        )
        return ValidatedCreateProblemDraft(
            title = title,
            description = description,
            constraints = constraints,
            examples = examples,
            referenceSolutionCode = referenceSolutionCode,
            referenceSolutionHash = referenceSolutionHash,
            referenceRuntimeMs = validationResult.evidence.runtimeMs,
            referenceMemoryUsedKb = validationResult.evidence.memoryUsedKb,
            referenceConsensusNodes = validationResult.evidence.consensusNodes,
            validationNodeId = validationResult.evidence.nodeId,
            validationRunHash = validationResult.evidence.runHash,
            validationResultHash = validationResult.evidence.resultHash,
            validationImageHash = validationResult.evidence.imageHash,
            validatedAt = validationResult.evidence.validatedAt,
            paymentAsset = paymentAsset,
            prizeAmountAtomic = prizeAmountAtomic,
            entryFeeAmountAtomic = entryFeeAmountAtomic,
            requiredParticipants = request.requiredParticipants,
            joinUntilDate = normalizedJoinDate,
            submitUntilDate = normalizedSubmitDate,
            tests = computedTests,
            competitionKey = competitionKey,
        )
    }

    private fun buildCompetitionKey(
        creatorWalletAddress: String,
        title: String,
        description: String,
        constraints: String,
        referenceSolutionHash: String,
        paymentAssetCode: String,
        prizeAmountAtomic: String,
        entryFeeAmountAtomic: String,
        requiredParticipants: Int,
        joinUntilDate: LocalDate,
        submitUntilDate: LocalDate,
        tests: List<StoredProblemTestDraft>,
    ): String {
        val canonical = buildString {
            append(normalizeWalletAddress(creatorWalletAddress))
            append('|')
            append(title)
            append('|')
            append(description)
            append('|')
            append(constraints)
            append('|')
            append(referenceSolutionHash)
            append('|')
            append(paymentAssetCode)
            append('|')
            append(prizeAmountAtomic)
            append('|')
            append(entryFeeAmountAtomic)
            append('|')
            append(requiredParticipants)
            append('|')
            append(joinUntilDate)
            append('|')
            append(submitUntilDate)
            tests.forEach { test ->
                append('|')
                append(test.inputData)
                append('|')
                append(test.expectedOutput)
                append('|')
                append(test.isHidden)
                append('|')
                append(test.timeoutMs)
                append('|')
                append(test.memoryLimitMb)
            }
        }
        return Hash.sha3String(canonical)
    }
}

internal fun createProblemSha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun normalizeWalletAddress(walletAddress: String): String {
    return "0x${walletAddress.trim().removePrefix("0x").lowercase()}"
}

private const val MAX_TITLE_LENGTH = 120
private const val MIN_PUBLIC_TEST_CASES = 1
private const val MAX_PROBLEM_EXAMPLES = 10
private const val MAX_DESCRIPTION_CHARS = 20_000
private const val MAX_CONSTRAINTS_CHARS = 8_000
private const val GENERATED_EXAMPLE_EXPLANATION = "Auto-generated from reference solution."
