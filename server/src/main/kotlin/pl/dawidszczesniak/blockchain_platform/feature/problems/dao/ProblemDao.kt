package pl.dawidszczesniak.blockchain_platform.feature.problems.dao

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import pl.dawidszczesniak.blockchain_platform.db.ProblemLifecycleStatus
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemParticipantsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
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
    fun fetchCreatedProblemRowsForDefaultUser(): List<ResultRow>
    fun fetchParticipationProblemRowsForDefaultUser(): List<ResultRow>
    fun fetchParticipantCountRows(): List<ResultRow>
    fun fetchSubmissionCountRows(): List<ResultRow>
    fun fetchSubmissionAttemptRows(): List<ResultRow>
    fun fetchWinnerRows(): List<ResultRow>
    fun fetchDefaultUserId(): Long?
}

internal class ProblemDaoImpl(
    private val database: Database,
) : ProblemDao {
    override fun fetchOpenProblemRows(): List<ResultRow> {
        return transaction(database) {
            ProblemsTable
                .selectAll()
                .where { ProblemsTable.problemStatus eq ProblemLifecycleStatus.Open.dbValue }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .toList()
        }
    }

    override fun fetchCreatedProblemRowsForDefaultUser(): List<ResultRow> {
        return transaction(database) {
            val userId = resolveDefaultUserId() ?: return@transaction emptyList()
            ProblemsTable
                .selectAll()
                .where { ProblemsTable.createdByUserId eq userId }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .toList()
        }
    }

    override fun fetchParticipationProblemRowsForDefaultUser(): List<ResultRow> {
        return transaction(database) {
            val userId = resolveDefaultUserId() ?: return@transaction emptyList()
            (ProblemParticipantsTable innerJoin ProblemsTable)
                .selectAll()
                .where { ProblemParticipantsTable.userId eq userId }
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .toList()
        }
    }

    override fun fetchParticipantCountRows(): List<ResultRow> {
        return transaction(database) {
            ProblemParticipantsTable
                .select(ProblemParticipantsTable.problemId, ProblemRowColumns.participantCount)
                .groupBy(ProblemParticipantsTable.problemId)
                .toList()
        }
    }

    override fun fetchSubmissionCountRows(): List<ResultRow> {
        return transaction(database) {
            ProblemSubmissionsTable
                .select(ProblemSubmissionsTable.problemId, ProblemRowColumns.submissionCount)
                .groupBy(ProblemSubmissionsTable.problemId)
                .toList()
        }
    }

    override fun fetchSubmissionAttemptRows(): List<ResultRow> {
        return transaction(database) {
            ProblemSubmissionsTable
                .select(
                    ProblemSubmissionsTable.problemId,
                    ProblemSubmissionsTable.userId,
                    ProblemRowColumns.attemptCount,
                )
                .groupBy(ProblemSubmissionsTable.problemId, ProblemSubmissionsTable.userId)
                .toList()
        }
    }

    override fun fetchWinnerRows(): List<ResultRow> {
        return transaction(database) {
            ProblemWinnersTable
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
    }

    override fun fetchDefaultUserId(): Long? {
        return transaction(database) {
            resolveDefaultUserId()
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
}
