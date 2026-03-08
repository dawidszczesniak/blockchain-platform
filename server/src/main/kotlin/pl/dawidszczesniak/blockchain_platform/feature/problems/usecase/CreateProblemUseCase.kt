package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.LocalDate
import java.time.format.DateTimeParseException
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemDraft
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

        val tests = request.tests.map { it.trim() }
        if (tests.isEmpty() || tests.any { it.isBlank() }) {
            throw CreateProblemValidationException("At least one non-empty test is required.")
        }

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
