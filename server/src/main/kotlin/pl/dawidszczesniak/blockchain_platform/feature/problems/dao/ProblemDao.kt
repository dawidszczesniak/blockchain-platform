package pl.dawidszczesniak.blockchain_platform.feature.problems.dao

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.db.ProblemLifecycleStatus
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemParticipantsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemWinnersTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.UsersTable

internal interface ProblemDao {
    fun fetchProblemSummaries(): List<ProblemSummary>
    fun fetchCreatedProblemsForDefaultUser(): List<CreatedProblem>
    fun fetchParticipationProblemsForDefaultUser(): List<ParticipationProblem>
}

internal class ProblemDaoImpl(
    private val database: Database,
) : ProblemDao {
    override fun fetchProblemSummaries(): List<ProblemSummary> {
        return transaction(database) {
            val participantCounts = participantCountsByProblem()
            val today = LocalDate.now()

            ProblemsTable
                .selectAll()
                .where { ProblemsTable.problemStatus eq ProblemLifecycleStatus.Open.dbValue }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .map { row ->
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
        return transaction(database) {
            val userId = resolveDefaultUserId() ?: return@transaction emptyList()
            val participantCounts = participantCountsByProblem()
            val submissionCounts = submissionCountsByProblem()
            val winnerByProblem = winnerInfoByProblem()
            val today = LocalDate.now()

            ProblemsTable
                .selectAll()
                .where { ProblemsTable.createdByUserId eq userId }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .map { row ->
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
        return transaction(database) {
            val userId = resolveDefaultUserId() ?: return@transaction emptyList()
            val participantCounts = participantCountsByProblem()
            val attemptCounts = submissionAttemptsByProblemAndUser()
            val today = LocalDate.now()

            (ProblemParticipantsTable innerJoin ProblemsTable)
                .selectAll()
                .where { ProblemParticipantsTable.userId eq userId }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .map { row ->
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

    private fun resolveDefaultUserId(): Long? {
        return UsersTable
            .selectAll()
            .orderBy(UsersTable.userId to SortOrder.ASC)
            .limit(1)
            .firstOrNull()
            ?.get(UsersTable.userId)
    }

    private fun participantCountsByProblem(): Map<Long, Int> {
        val countExpression = ProblemParticipantsTable.userId.count()
        return ProblemParticipantsTable
            .select(ProblemParticipantsTable.problemId, countExpression)
            .groupBy(ProblemParticipantsTable.problemId)
            .associate { row ->
                row[ProblemParticipantsTable.problemId] to row[countExpression].toInt()
            }
    }

    private fun submissionCountsByProblem(): Map<Long, Int> {
        val countExpression = ProblemSubmissionsTable.submissionId.count()
        return ProblemSubmissionsTable
            .select(ProblemSubmissionsTable.problemId, countExpression)
            .groupBy(ProblemSubmissionsTable.problemId)
            .associate { row ->
                row[ProblemSubmissionsTable.problemId] to row[countExpression].toInt()
            }
    }

    private fun submissionAttemptsByProblemAndUser(): Map<Pair<Long, Long>, Int> {
        val countExpression = ProblemSubmissionsTable.submissionId.count()
        return ProblemSubmissionsTable
            .select(ProblemSubmissionsTable.problemId, ProblemSubmissionsTable.userId, countExpression)
            .groupBy(ProblemSubmissionsTable.problemId, ProblemSubmissionsTable.userId)
            .associate { row ->
                (row[ProblemSubmissionsTable.problemId] to row[ProblemSubmissionsTable.userId]) to
                    row[countExpression].toInt()
            }
    }

    private fun winnerInfoByProblem(): Map<Long, WinnerInfo> {
        val winnersWithUsers = ProblemWinnersTable.innerJoin(
            UsersTable,
            { ProblemWinnersTable.winnerUserId },
            { UsersTable.userId },
        )

        val rows = winnersWithUsers
            .selectAll()
            .orderBy(
                ProblemWinnersTable.problemId to SortOrder.ASC,
                ProblemWinnersTable.wonAt to SortOrder.DESC,
                ProblemWinnersTable.winnerUserId to SortOrder.DESC,
            )

        val result = mutableMapOf<Long, WinnerInfo>()
        rows.forEach { row ->
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
