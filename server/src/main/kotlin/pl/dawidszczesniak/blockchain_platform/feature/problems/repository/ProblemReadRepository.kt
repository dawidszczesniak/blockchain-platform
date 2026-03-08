package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

internal interface ProblemReadRepository {
    fun fetchProblemSummaries(): List<ProblemSummary>
    fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem>
    fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem>
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

    override fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem> {
        return transactionRunner.inTransaction {
            val participantCounts = participantCountsByProblem()
            val submissionCounts = submissionCountsByProblem()
            val winnerByProblem = winnerInfoByProblem()
            val today = LocalDate.now()

            problemDao.fetchCreatedProblemRowsForDefaultUser().map { row ->
                val problemId = row[ProblemsTable.problemId]
                val daysToJoinEnd = daysBetween(today, row[ProblemsTable.joinUntilDate])
                val daysToSubmitEnd = daysBetween(today, row[ProblemsTable.submitUntilDate])
                val winnerInfo = winnerByProblem[problemId]
                val status = createdProblemStatus(
                    problemStatus = row[ProblemsTable.problemStatus],
                    daysToJoinEnd = daysToJoinEnd,
                    daysToSubmitEnd = daysToSubmitEnd,
                    winnerWallet = winnerInfo?.walletAddress,
                )

                CreatedProblem(
                    id = problemId.toInt(),
                    title = row[ProblemsTable.title],
                    status = status,
                    requiredParticipants = row[ProblemsTable.requiredParticipants],
                    registeredParticipants = participantCounts[problemId] ?: 0,
                    submissions = submissionCounts[problemId] ?: 0,
                    startedOn = if (status == CreatedProblemStatus.Started) {
                        row[ProblemsTable.joinUntilDate].toString()
                    } else {
                        null
                    },
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

    override fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem> {
        return transactionRunner.inTransaction {
            val userId = problemDao.fetchDefaultUserId() ?: return@inTransaction emptyList()
            val participantCounts = participantCountsByProblem()
            val attemptCounts = submissionAttemptsByProblemAndUser()
            val today = LocalDate.now()

            problemDao.fetchParticipationProblemRowsForDefaultUser().map { row ->
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

    override fun createProblemForDefaultUser(draft: NewProblemDraft): Int {
        return transactionRunner.inTransaction {
            val createdByUserId = problemDao.fetchOrCreateDefaultUserId()
            val problemId = problemDao.insertProblem(
                createdByUserId = createdByUserId,
                title = draft.title,
                description = draft.description,
                prizeAmount = draft.prizeAmount,
                entryFeeAmount = draft.entryFeeAmount,
                requiredParticipants = draft.requiredParticipants,
                joinUntilDate = draft.joinUntilDate,
                submitUntilDate = draft.submitUntilDate,
            )

            draft.tests.forEachIndexed { index, code ->
                problemDao.insertProblemTest(
                    problemId = problemId,
                    testOrder = index + 1,
                    validatorCode = code,
                )
            }
            problemId.toInt()
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
        if (daysToJoinEnd >= 0) {
            return CreatedProblemStatus.Waiting
        }
        if (daysToSubmitEnd >= 0) {
            return CreatedProblemStatus.Started
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
