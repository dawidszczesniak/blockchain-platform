package pl.dawidszczesniak.blockchain_platform.db

import java.time.LocalDate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemParticipantsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemWinnersTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.UsersTable

internal class DbSeeder(
    private val database: Database,
) {
    fun seedIfEmpty() {
        transaction(database) {
            val existingCount = ProblemsTable.selectAll().count()
            if (existingCount > 0L) {
                return@transaction
            }

            val userIds = (1..11).map { index ->
                insertUser(walletAddressForSeed(index))
            }

            val firstProblemId = insertProblem(
                title = "Neural Network Compression",
                description = "Optimize a neural model pipeline to reduce inference cost.",
                problemStatus = ProblemLifecycleStatus.Open,
                prizeAmount = 25L,
                entryFeeAmount = 3L,
                requiredParticipants = 12,
                joinUntilDate = LocalDate.parse("2026-03-08"),
                submitUntilDate = LocalDate.parse("2026-03-15"),
                createdByUserId = userIds[0],
            )

            val secondProblemId = insertProblem(
                title = "Oracle Aggregation",
                description = "Build robust oracle aggregation logic for volatile market feeds.",
                problemStatus = ProblemLifecycleStatus.Closed,
                prizeAmount = 40L,
                entryFeeAmount = 5L,
                requiredParticipants = 18,
                joinUntilDate = LocalDate.parse("2026-03-10"),
                submitUntilDate = LocalDate.parse("2026-03-17"),
                createdByUserId = userIds[1],
            )

            userIds.take(7).forEach { userId ->
                registerUser(problemId = firstProblemId, userId = userId)
            }
            userIds.forEach { userId ->
                registerUser(problemId = secondProblemId, userId = userId)
            }

            insertProblemSubmission(
                problemId = firstProblemId,
                userId = userIds[0],
                status = SubmissionAttemptStatus.Rejected,
                sourceCode = "fun solve(input: String): String = input.reversed()",
                language = "kotlin",
            )
            insertProblemSubmission(
                problemId = firstProblemId,
                userId = userIds[0],
                status = SubmissionAttemptStatus.Accepted,
                sourceCode = "fun solve(input: String): String = input.lowercase()",
                language = "kotlin",
            )
            insertProblemSubmission(
                problemId = firstProblemId,
                userId = userIds[2],
                status = SubmissionAttemptStatus.Accepted,
                sourceCode = "def solve(text):\n    return text.strip().lower()",
                language = "python",
            )
            insertProblemSubmission(
                problemId = secondProblemId,
                userId = userIds[4],
                status = SubmissionAttemptStatus.Error,
                sourceCode = "function solve(input) { throw new Error('runtime'); }",
                language = "javascript",
            )
            insertProblemSubmission(
                problemId = secondProblemId,
                userId = userIds[4],
                status = SubmissionAttemptStatus.Rejected,
                sourceCode = "function solve(input) { return input; }",
                language = "javascript",
            )
            insertProblemSubmission(
                problemId = secondProblemId,
                userId = userIds[4],
                status = SubmissionAttemptStatus.Accepted,
                sourceCode = "function solve(input) { return input.trim(); }",
                language = "javascript",
            )

            insertProblemWinner(
                problemId = firstProblemId,
                winnerUserId = userIds[2],
                payoutAmount = 25L,
            )
            insertProblemWinner(
                problemId = secondProblemId,
                winnerUserId = userIds[4],
                payoutAmount = 40L,
            )
        }
    }

    private fun insertProblem(
        title: String,
        description: String,
        problemStatus: ProblemLifecycleStatus,
        prizeAmount: Long,
        entryFeeAmount: Long,
        requiredParticipants: Int,
        joinUntilDate: LocalDate,
        submitUntilDate: LocalDate,
        createdByUserId: Long,
    ): Long {
        val inserted = ProblemsTable.insert {
            it[ProblemsTable.createdByUserId] = createdByUserId
            it[ProblemsTable.problemStatus] = problemStatus.dbValue
            it[ProblemsTable.title] = title
            it[ProblemsTable.description] = description
            it[ProblemsTable.prizeAmount] = prizeAmount
            it[ProblemsTable.entryFeeAmount] = entryFeeAmount
            it[ProblemsTable.requiredParticipants] = requiredParticipants
            it[ProblemsTable.joinUntilDate] = joinUntilDate
            it[ProblemsTable.submitUntilDate] = submitUntilDate
        }
        return inserted[ProblemsTable.problemId]
    }

    private fun insertUser(walletAddress: String): Long {
        val inserted = UsersTable.insert {
            it[UsersTable.walletAddress] = walletAddress
        }
        return inserted[UsersTable.userId]
    }

    private fun registerUser(problemId: Long, userId: Long) {
        ProblemParticipantsTable.insert {
            it[ProblemParticipantsTable.problemId] = problemId
            it[ProblemParticipantsTable.userId] = userId
        }
    }

    private fun insertProblemSubmission(
        problemId: Long,
        userId: Long,
        status: SubmissionAttemptStatus,
        sourceCode: String,
        language: String,
    ) {
        ProblemSubmissionsTable.insert {
            it[ProblemSubmissionsTable.problemId] = problemId
            it[ProblemSubmissionsTable.userId] = userId
            it[ProblemSubmissionsTable.status] = status.dbValue
            it[ProblemSubmissionsTable.sourceCode] = sourceCode
            it[ProblemSubmissionsTable.language] = language
        }
    }

    private fun insertProblemWinner(
        problemId: Long,
        winnerUserId: Long,
        payoutAmount: Long,
    ) {
        ProblemWinnersTable.insert {
            it[ProblemWinnersTable.problemId] = problemId
            it[ProblemWinnersTable.winnerUserId] = winnerUserId
            it[ProblemWinnersTable.payoutAmount] = payoutAmount
        }
    }

    private fun walletAddressForSeed(index: Int): String {
        return "0x${index.toString(16).padStart(40, '0')}"
    }
}
