package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.LocalDate
import java.time.format.DateTimeParseException
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemTestCaseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemTestDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository

internal class CreateProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface CreateProblemUseCase {
    operator fun invoke(request: CreateProblemRequestDto): Int
}

internal class CreateProblemUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val dashboardMetricsRefresher: DashboardMetricsRefresher,
    private val transactionRunner: DbTransactionRunner,
) : CreateProblemUseCase {
    override fun invoke(request: CreateProblemRequestDto): Int {
        val description = request.description.trim()
        if (description.isBlank()) {
            throw CreateProblemValidationException("Description is required.")
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

        val tests = parseTests(request)

        val title = deriveTitle(description)
        val createdProblemId = repository.createProblemForDefaultUser(
            NewProblemDraft(
                title = title,
                description = description,
                prizeAmount = request.prizeAmount,
                entryFeeAmount = request.entryFeeAmount,
                requiredParticipants = request.requiredParticipants,
                joinUntilDate = joinUntilDate,
                submitUntilDate = submitUntilDate,
                tests = tests,
            )
        )
        transactionRunner.inTransaction {
            dashboardMetricsRefresher.refreshTodayMetrics()
        }
        return createdProblemId
    }

    private fun parseTests(request: CreateProblemRequestDto): List<NewProblemTestDraft> {
        val structuredTests = request.testCases.map { it.toDraft() }
        if (structuredTests.isNotEmpty()) {
            validateTestCount(structuredTests.size)
            structuredTests.forEachIndexed { index, test ->
                validateStructuredTest(index = index, test = test)
            }
            return structuredTests
        }

        val legacyTests = request.tests.map { it.trim() }
        if (legacyTests.isEmpty() || legacyTests.any { it.isBlank() }) {
            throw CreateProblemValidationException(
                "At least one non-empty test is required."
            )
        }
        validateTestCount(legacyTests.size)
        return legacyTests.map { validatorCode ->
            NewProblemTestDraft(
                inputData = "",
                expectedOutput = "",
                validatorCode = validatorCode,
                isHidden = true,
                timeoutMs = DEFAULT_TEST_TIMEOUT_MS,
                memoryLimitMb = DEFAULT_TEST_MEMORY_LIMIT_MB,
            )
        }
    }

    private fun validateTestCount(count: Int) {
        if (count > MAX_TEST_CASES) {
            throw CreateProblemValidationException(
                "Too many tests. Maximum supported tests count is $MAX_TEST_CASES."
            )
        }
    }

    private fun validateStructuredTest(index: Int, test: NewProblemTestDraft) {
        val humanIndex = index + 1
        if (test.validatorCode.isBlank() && test.expectedOutput.isBlank()) {
            throw CreateProblemValidationException(
                "testCases[$humanIndex] must define validatorCode or expectedOutput."
            )
        }
        if (test.timeoutMs !in 1..MAX_TEST_TIMEOUT_MS) {
            throw CreateProblemValidationException(
                "testCases[$humanIndex].timeoutMs must be in range 1..$MAX_TEST_TIMEOUT_MS."
            )
        }
        if (test.memoryLimitMb !in 1..MAX_TEST_MEMORY_LIMIT_MB) {
            throw CreateProblemValidationException(
                "testCases[$humanIndex].memoryLimitMb must be in range 1..$MAX_TEST_MEMORY_LIMIT_MB."
            )
        }
    }

    private fun CreateProblemTestCaseDto.toDraft(): NewProblemTestDraft {
        return NewProblemTestDraft(
            inputData = inputData.trim(),
            expectedOutput = expectedOutput.trim(),
            validatorCode = validatorCode.trim(),
            isHidden = isHidden,
            timeoutMs = timeoutMs,
            memoryLimitMb = memoryLimitMb,
        )
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

private const val MAX_TITLE_LENGTH = 120
private const val MAX_TEST_CASES = 100
private const val DEFAULT_TEST_TIMEOUT_MS = 1000
private const val DEFAULT_TEST_MEMORY_LIMIT_MB = 256
private const val MAX_TEST_TIMEOUT_MS = 60_000
private const val MAX_TEST_MEMORY_LIMIT_MB = 2048
