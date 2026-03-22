package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemExampleDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemTestDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository

internal class CreateProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface CreateProblemUseCase {
    operator fun invoke(userId: Long, request: CreateProblemRequestDto): Int
}

internal class CreateProblemUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val dashboardMetricsRefresher: DashboardMetricsRefresher,
    private val transactionRunner: DbTransactionRunner,
    private val referenceValidationService: CreateProblemReferenceValidationService,
) : CreateProblemUseCase {
    override fun invoke(userId: Long, request: CreateProblemRequestDto): Int {
        val rawTitle = request.title.trim()
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
        if (request.prizeAmount < 0) {
            throw CreateProblemValidationException("Prize amount cannot be negative.")
        }
        if (request.entryFeeAmount < 0) {
            throw CreateProblemValidationException("Entry fee amount cannot be negative.")
        }
        if (request.requiredParticipants <= 0) {
            throw CreateProblemValidationException("Required participants must be greater than 0.")
        }

        val joinUntilDate = parseDate(request.joinUntilDate, "joinUntilDate")
        val submitUntilDate = parseDate(request.submitUntilDate, "submitUntilDate")
        if (!submitUntilDate.isAfter(joinUntilDate)) {
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
            NewProblemTestDraft(
                inputData = testCase.inputData,
                expectedOutput = expectedOutput,
                validatorCode = "",
                validatorLanguage = CREATE_PROBLEM_SUPPORTED_LANGUAGE,
                isHidden = testCase.isHidden,
                timeoutMs = testCase.timeoutMs,
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
                NewProblemExampleDraft(
                    input = test.inputData,
                    output = expectedOutput,
                    explanation = GENERATED_EXAMPLE_EXPLANATION,
                )
            }

        val normalizedReferenceSolutionCode = request.referenceSolutionCode.trim()
        val title = if (rawTitle.isNotBlank()) rawTitle else deriveTitle(description)
        val createdProblemId = repository.createProblemForUser(
            userId = userId,
            draft = NewProblemDraft(
                title = title,
                description = description,
                constraints = constraints,
                examples = examples,
                referenceSolutionHash = sha256Hex(normalizedReferenceSolutionCode),
                validationNodeId = validationResult.evidence.nodeId,
                validationRunHash = validationResult.evidence.runHash,
                validationResultHash = validationResult.evidence.resultHash,
                validationImageHash = validationResult.evidence.imageHash,
                validatedAt = validationResult.evidence.validatedAt,
                prizeAmount = request.prizeAmount,
                entryFeeAmount = request.entryFeeAmount,
                requiredParticipants = request.requiredParticipants,
                joinUntilDate = joinUntilDate,
                submitUntilDate = submitUntilDate,
                tests = computedTests,
            )
        )
        transactionRunner.inTransaction {
            dashboardMetricsRefresher.refreshTodayMetrics()
        }
        return createdProblemId
    }

    private fun parseDate(value: String, fieldName: String): LocalDate {
        val normalized = value.trim()
        return try {
            LocalDate.parse(normalized)
        } catch (_: DateTimeParseException) {
            throw CreateProblemValidationException("Invalid date in '$fieldName'. Expected format: YYYY-MM-DD.")
        }
    }

    private fun deriveTitle(description: String): String {
        val base = description
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: description.trim()
        return base.take(MAX_TITLE_LENGTH)
    }
}

private fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private const val MAX_TITLE_LENGTH = 120
private const val MIN_PUBLIC_TEST_CASES = 1
private const val MAX_PROBLEM_EXAMPLES = 10
private const val MAX_DESCRIPTION_CHARS = 20_000
private const val MAX_CONSTRAINTS_CHARS = 8_000
private const val GENERATED_EXAMPLE_EXPLANATION = "Auto-generated from reference solution."
