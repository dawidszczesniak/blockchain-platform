package pl.dawidszczesniak.blockchain_platform.feature.problems.dao

import java.time.LocalDate
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import pl.dawidszczesniak.blockchain_platform.db.ProblemLifecycleStatus
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemParticipantsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemTestsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemWinnersTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.UsersTable

internal object ProblemRowColumns {
    val participantCount = ProblemParticipantsTable.userId.count()
    val submissionCount = ProblemSubmissionsTable.submissionId.count()
    val attemptCount = ProblemSubmissionsTable.submissionId.count()
}

internal interface ProblemDao {
    fun fetchOpenProblemRows(): List<ResultRow>
    fun fetchCreatedProblemRowsForUser(userId: Long): List<ResultRow>
    fun fetchParticipationProblemRowsForUser(userId: Long): List<ResultRow>
    fun fetchParticipantCountRows(): List<ResultRow>
    fun fetchSubmissionCountRows(): List<ResultRow>
    fun fetchSubmissionAttemptRows(): List<ResultRow>
    fun fetchWinnerRows(): List<ResultRow>
    fun insertProblem(
        createdByUserId: Long,
        title: String,
        description: String,
        prizeAmount: Long,
        entryFeeAmount: Long,
        requiredParticipants: Int,
        joinUntilDate: LocalDate,
        submitUntilDate: LocalDate,
    ): Long
    fun insertProblemTest(
        problemId: Long,
        testOrder: Int,
        inputData: String,
        expectedOutput: String,
        validatorCode: String,
        isHidden: Boolean,
        timeoutMs: Int,
        memoryLimitMb: Int,
    )
}

internal class ProblemDaoImpl : ProblemDao {
    override fun fetchOpenProblemRows(): List<ResultRow> {
        return ProblemsTable
            .selectAll()
            .where { ProblemsTable.problemStatus eq ProblemLifecycleStatus.Open.dbValue }
            .orderBy(
                ProblemsTable.createdAt to SortOrder.DESC,
                ProblemsTable.problemId to SortOrder.DESC,
            )
            .toList()
    }

    override fun fetchCreatedProblemRowsForUser(userId: Long): List<ResultRow> {
        return ProblemsTable
            .selectAll()
            .where { ProblemsTable.createdByUserId eq userId }
            .orderBy(
                ProblemsTable.createdAt to SortOrder.DESC,
                ProblemsTable.problemId to SortOrder.DESC,
            )
            .toList()
    }

    override fun fetchParticipationProblemRowsForUser(userId: Long): List<ResultRow> {
        return (ProblemParticipantsTable innerJoin ProblemsTable)
            .selectAll()
            .where { ProblemParticipantsTable.userId eq userId }
            .orderBy(
                ProblemsTable.createdAt to SortOrder.DESC,
                ProblemsTable.problemId to SortOrder.DESC,
            )
            .toList()
    }

    override fun fetchParticipantCountRows(): List<ResultRow> {
        return ProblemParticipantsTable
            .select(ProblemParticipantsTable.problemId, ProblemRowColumns.participantCount)
            .groupBy(ProblemParticipantsTable.problemId)
            .toList()
    }

    override fun fetchSubmissionCountRows(): List<ResultRow> {
        return ProblemSubmissionsTable
            .select(ProblemSubmissionsTable.problemId, ProblemRowColumns.submissionCount)
            .groupBy(ProblemSubmissionsTable.problemId)
            .toList()
    }

    override fun fetchSubmissionAttemptRows(): List<ResultRow> {
        return ProblemSubmissionsTable
            .select(
                ProblemSubmissionsTable.problemId,
                ProblemSubmissionsTable.userId,
                ProblemRowColumns.attemptCount,
            )
            .groupBy(ProblemSubmissionsTable.problemId, ProblemSubmissionsTable.userId)
            .toList()
    }

    override fun fetchWinnerRows(): List<ResultRow> {
        return ProblemWinnersTable
            .innerJoin(
                UsersTable,
                { ProblemWinnersTable.winnerUserId },
                { UsersTable.userId },
            )
            .selectAll()
            .orderBy(
                ProblemWinnersTable.problemId to SortOrder.ASC,
                ProblemWinnersTable.wonAt to SortOrder.DESC,
                ProblemWinnersTable.winnerUserId to SortOrder.DESC,
            )
            .toList()
    }

    override fun insertProblem(
        createdByUserId: Long,
        title: String,
        description: String,
        prizeAmount: Long,
        entryFeeAmount: Long,
        requiredParticipants: Int,
        joinUntilDate: LocalDate,
        submitUntilDate: LocalDate,
    ): Long {
        val inserted = ProblemsTable.insert {
            it[ProblemsTable.createdByUserId] = createdByUserId
            it[ProblemsTable.problemStatus] = ProblemLifecycleStatus.Open.dbValue
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

    override fun insertProblemTest(
        problemId: Long,
        testOrder: Int,
        inputData: String,
        expectedOutput: String,
        validatorCode: String,
        isHidden: Boolean,
        timeoutMs: Int,
        memoryLimitMb: Int,
    ) {
        ProblemTestsTable.insert {
            it[ProblemTestsTable.problemId] = problemId
            it[ProblemTestsTable.testOrder] = testOrder
            it[ProblemTestsTable.inputData] = inputData
            it[ProblemTestsTable.expectedOutput] = expectedOutput
            it[ProblemTestsTable.validatorCode] = validatorCode
            it[ProblemTestsTable.isHidden] = isHidden
            it[ProblemTestsTable.timeoutMs] = timeoutMs
            it[ProblemTestsTable.memoryLimitMb] = memoryLimitMb
        }
    }
}
