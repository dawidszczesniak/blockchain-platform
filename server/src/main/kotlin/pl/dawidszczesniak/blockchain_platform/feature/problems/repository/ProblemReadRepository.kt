package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.ProblemLifecycleStatus
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemParticipantsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemWinnersTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.UsersTable
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemDao
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemRowColumns
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemExample
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemExampleDto

internal interface ProblemReadRepository {
    fun fetchProblemSummaries(): List<ProblemSummary>
    fun fetchCreatedProblemsForUser(userId: Long): List<CreatedProblem>
    fun fetchParticipationProblemsForUser(userId: Long): List<ParticipationProblem>
}

internal class ProblemReadRepositoryImpl(
    private val problemDao: ProblemDao,
    private val transactionRunner: DbTransactionRunner,
) : ProblemReadRepository, ProblemWriteRepository {
    override fun fetchProblemSummaries(): List<ProblemSummary> {
        return transactionRunner.inTransaction {
            val participantCounts = participantCountsByProblem()
            val today = LocalDate.now()

            problemDao.fetchOpenProblemRows().map { row ->
                val problemId = row[ProblemsTable.problemId]
                val daysToJoinEnd = daysBetween(today, row[ProblemsTable.joinUntilDate]).coerceAtLeast(0)
                ProblemSummary(
                    id = problemId.toInt(),
                    title = row[ProblemsTable.title],
                    description = row[ProblemsTable.description],
                    constraints = row[ProblemsTable.constraintsText],
                    examples = parseProblemExamples(row[ProblemsTable.examplesJson]),
                    prizeAmount = row[ProblemsTable.prizeAmount],
                    entryFeeAmount = row[ProblemsTable.entryFeeAmount],
                    requiredParticipants = row[ProblemsTable.requiredParticipants],
                    registeredParticipants = participantCounts[problemId] ?: 0,
                    daysToStart = daysToJoinEnd,
                    daysToJoinEnd = daysToJoinEnd,
                    joinUntilLabel = row[ProblemsTable.joinUntilDate].toString(),
                    submitUntilLabel = row[ProblemsTable.submitUntilDate].toString(),
                )
            }
        }
    }

    override fun fetchCreatedProblemsForUser(userId: Long): List<CreatedProblem> {
        return transactionRunner.inTransaction {
            val participantCounts = participantCountsByProblem()
            val submissionCounts = submissionCountsByProblem()
            val winnerByProblem = winnerInfoByProblem()
            val today = LocalDate.now()

            problemDao.fetchCreatedProblemRowsForUser(userId).map { row ->
                val problemId = row[ProblemsTable.problemId]
                val requiredParticipants = row[ProblemsTable.requiredParticipants]
                val registeredParticipants = participantCounts[problemId] ?: 0
                val daysToJoinEnd = daysBetween(today, row[ProblemsTable.joinUntilDate])
                val daysToSubmitEnd = daysBetween(today, row[ProblemsTable.submitUntilDate])
                val winnerInfo = winnerByProblem[problemId]
                val status = createdProblemStatus(
                    problemStatus = row[ProblemsTable.problemStatus],
                    requiredParticipants = requiredParticipants,
                    registeredParticipants = registeredParticipants,
                    daysToJoinEnd = daysToJoinEnd,
                    daysToSubmitEnd = daysToSubmitEnd,
                    winnerWallet = winnerInfo?.walletAddress,
                )
                val startedOnLabel = if (status == CreatedProblemStatus.Started) {
                    if (registeredParticipants >= requiredParticipants && daysToJoinEnd >= 0) {
                        today.toString()
                    } else {
                        row[ProblemsTable.joinUntilDate].toString()
                    }
                } else {
                    null
                }

                CreatedProblem(
                    id = problemId.toInt(),
                    title = row[ProblemsTable.title],
                    status = status,
                    requiredParticipants = requiredParticipants,
                    registeredParticipants = registeredParticipants,
                    submissions = submissionCounts[problemId] ?: 0,
                    startedOn = startedOnLabel,
                    finishedOn = if (status == CreatedProblemStatus.Completed) {
                        winnerInfo?.wonAtLabel ?: row[ProblemsTable.submitUntilDate].toString()
                    } else {
                        null
                    },
                    registrationEnds = if (status == CreatedProblemStatus.Waiting) {
                        row[ProblemsTable.joinUntilDate].toString()
                    } else {
                        null
                    },
                    timeElapsed = if (status == CreatedProblemStatus.Expired) {
                        row[ProblemsTable.submitUntilDate].toString()
                    } else {
                        null
                    },
                    winner = if (status == CreatedProblemStatus.Completed) {
                        winnerInfo?.walletAddress
                    } else {
                        null
                    },
                )
            }
        }
    }

    override fun fetchParticipationProblemsForUser(userId: Long): List<ParticipationProblem> {
        return transactionRunner.inTransaction {
            val participantCounts = participantCountsByProblem()
            val attemptCounts = submissionAttemptsByProblemAndUser()
            val today = LocalDate.now()

            problemDao.fetchParticipationProblemRowsForUser(userId).map { row ->
                val problemId = row[ProblemsTable.problemId]
                val attempts = attemptCounts[problemId to userId] ?: 0
                val daysToSubmitEnd = daysBetween(today, row[ProblemsTable.submitUntilDate])

                ParticipationProblem(
                    id = problemId.toInt(),
                    title = row[ProblemsTable.title],
                    status = if (attempts > 0) {
                        ParticipationStatus.Submitted
                    } else {
                        ParticipationStatus.NotSubmitted
                    },
                    timeLeftLabel = "${max(0, daysToSubmitEnd)}d",
                    participants = participantCounts[problemId] ?: 0,
                    attemptsCount = attempts,
                )
            }
        }
    }

    override fun createProblemForUser(userId: Long, draft: NewProblemDraft): Int {
        return transactionRunner.inTransaction {
            val problemId = problemDao.insertProblem(
                createdByUserId = userId,
                title = draft.title,
                description = draft.description,
                constraints = draft.constraints,
                examplesJson = serializeProblemExamples(draft.examples),
                prizeAmount = draft.prizeAmount,
                entryFeeAmount = draft.entryFeeAmount,
                requiredParticipants = draft.requiredParticipants,
                joinUntilDate = draft.joinUntilDate,
                submitUntilDate = draft.submitUntilDate,
            )

            draft.tests.forEachIndexed { index, test ->
                problemDao.insertProblemTest(
                    problemId = problemId,
                    testOrder = index + 1,
                    inputData = test.inputData,
                    expectedOutput = test.expectedOutput,
                    validatorCode = test.validatorCode,
                    isHidden = test.isHidden,
                    timeoutMs = test.timeoutMs,
                    memoryLimitMb = test.memoryLimitMb,
                )
            }
            problemId.toInt()
        }
    }

    override fun registerUserForProblem(userId: Long, problemId: Int): JoinProblemResult {
        return transactionRunner.inTransaction {
            val normalizedProblemId = problemId.toLong()
            val problemRow = problemDao.fetchOpenProblemRow(normalizedProblemId)
                ?: throw IllegalArgumentException("Problem not found or not open.")
            val requiredParticipants = problemRow[ProblemsTable.requiredParticipants]
            val joinUntilDate = problemRow[ProblemsTable.joinUntilDate]

            val alreadyRegistered = problemDao.isUserRegisteredForProblem(
                problemId = normalizedProblemId,
                userId = userId,
            )
            if (!alreadyRegistered) {
                if (LocalDate.now().isAfter(joinUntilDate)) {
                    throw IllegalArgumentException("Registration period has ended.")
                }
                val participantsBeforeJoin = problemDao.countParticipants(normalizedProblemId)
                if (participantsBeforeJoin >= requiredParticipants) {
                    throw IllegalArgumentException("Competition has already started. Registration is closed.")
                }
                runCatching {
                    problemDao.insertProblemParticipant(
                        problemId = normalizedProblemId,
                        userId = userId,
                    )
                }.onFailure { error ->
                    val nowRegistered = problemDao.isUserRegisteredForProblem(
                        problemId = normalizedProblemId,
                        userId = userId,
                    )
                    if (!nowRegistered) {
                        throw error
                    }
                }
            }

            val registeredParticipants = problemDao.countParticipants(normalizedProblemId)
            JoinProblemResult(
                joined = !alreadyRegistered,
                registeredParticipants = registeredParticipants,
                requiredParticipants = requiredParticipants,
            )
        }
    }

    private fun participantCountsByProblem(): Map<Long, Int> {
        return problemDao.fetchParticipantCountRows().associate { row ->
            row[ProblemParticipantsTable.problemId] to row[ProblemRowColumns.participantCount].toInt()
        }
    }

    private fun submissionCountsByProblem(): Map<Long, Int> {
        return problemDao.fetchSubmissionCountRows().associate { row ->
            row[ProblemSubmissionsTable.problemId] to row[ProblemRowColumns.submissionCount].toInt()
        }
    }

    private fun submissionAttemptsByProblemAndUser(): Map<Pair<Long, Long>, Int> {
        return problemDao.fetchSubmissionAttemptRows().associate { row ->
            (row[ProblemSubmissionsTable.problemId] to row[ProblemSubmissionsTable.userId]) to
                row[ProblemRowColumns.attemptCount].toInt()
        }
    }

    private fun winnerInfoByProblem(): Map<Long, WinnerInfo> {
        val result = mutableMapOf<Long, WinnerInfo>()

        problemDao.fetchWinnerRows().forEach { row ->
            val problemId = row[ProblemWinnersTable.problemId]
            if (problemId !in result) {
                val wonAtLabel = row[ProblemWinnersTable.wonAt].toLocalDate().toString()
                result[problemId] = WinnerInfo(
                    walletAddress = row[UsersTable.walletAddress],
                    wonAtLabel = wonAtLabel,
                )
            }
        }

        return result
    }

    private fun createdProblemStatus(
        problemStatus: String,
        requiredParticipants: Int,
        registeredParticipants: Int,
        daysToJoinEnd: Int,
        daysToSubmitEnd: Int,
        winnerWallet: String?,
    ): CreatedProblemStatus {
        if (problemStatus == ProblemLifecycleStatus.Closed.dbValue) {
            return if (!winnerWallet.isNullOrBlank()) {
                CreatedProblemStatus.Completed
            } else {
                CreatedProblemStatus.Expired
            }
        }
        if (!winnerWallet.isNullOrBlank()) {
            return CreatedProblemStatus.Completed
        }
        val hasReachedParticipantThreshold = registeredParticipants >= requiredParticipants
        if (hasReachedParticipantThreshold) {
            return if (daysToSubmitEnd >= 0) {
                CreatedProblemStatus.Started
            } else {
                CreatedProblemStatus.Expired
            }
        }
        if (daysToJoinEnd >= 0) {
            return CreatedProblemStatus.Waiting
        }
        return CreatedProblemStatus.Expired
    }

    private fun daysBetween(fromDate: LocalDate, toDate: LocalDate): Int {
        return ChronoUnit.DAYS.between(fromDate, toDate).toInt()
    }
}

private data class WinnerInfo(
    val walletAddress: String,
    val wonAtLabel: String,
)

private val problemExamplesJson = Json {
    ignoreUnknownKeys = true
}

private fun parseProblemExamples(raw: String): List<ProblemExample> {
    if (raw.isBlank()) {
        return emptyList()
    }
    val parsed = runCatching {
        problemExamplesJson.decodeFromString<List<ProblemExampleDto>>(raw)
    }.getOrDefault(emptyList())
    return parsed.map { example ->
        ProblemExample(
            input = example.input,
            output = example.output,
            explanation = example.explanation,
        )
    }
}

private fun serializeProblemExamples(examples: List<NewProblemExampleDraft>): String {
    if (examples.isEmpty()) {
        return "[]"
    }
    return problemExamplesJson.encodeToString(
        examples.map { example ->
            ProblemExampleDto(
                input = example.input,
                output = example.output,
                explanation = example.explanation,
            )
        }
    )
}
