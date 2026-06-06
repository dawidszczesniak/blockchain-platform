package pl.dawidszczesniak.blockchain_platform.feature.problems.dao

import java.time.LocalDate
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
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
    fun fetchProblemRow(problemId: Long): ResultRow?
    fun fetchOpenProblemRow(problemId: Long): ResultRow?
    fun fetchOpenProblemRowForUpdate(problemId: Long): ResultRow?
    fun fetchCreatedProblemRowsForUser(userId: Long): List<ResultRow>
    fun fetchParticipationProblemRowsForUser(userId: Long): List<ResultRow>
    fun isUserRegisteredForProblem(problemId: Long, userId: Long): Boolean
    fun insertProblemParticipant(
        problemId: Long,
        userId: Long,
        joinTxHash: String? = null,
        joinFromWallet: String? = null,
        joinedOnchainAt: java.time.Instant? = null,
    )
    fun countParticipants(problemId: Long): Int
    fun fetchParticipantCountRows(): List<ResultRow>
    fun fetchSubmissionCountRows(): List<ResultRow>
    fun fetchSubmissionAttemptRows(): List<ResultRow>
    fun fetchWinnerRows(): List<ResultRow>
    fun fetchProblemTestRows(problemId: Long): List<ResultRow>
    fun insertProblem(
        createdByUserId: Long,
        title: String,
        description: String,
        constraints: String,
        examplesJson: String,
        referenceSolutionCode: String,
        referenceSolutionHash: String,
        referenceRuntimeMs: Int,
        referenceMemoryUsedKb: Int?,
        referenceConsensusNodes: Int,
        validationNodeId: String?,
        validationRunHash: String?,
        validationResultHash: String?,
        validationImageHash: String?,
        validatedAt: java.time.Instant,
        paymentAssetCode: String,
        prizeAmountAtomic: String,
        entryFeeAmountAtomic: String,
        requiredParticipants: Int,
        onchainCompetitionId: Long?,
        onchainContractAddress: String?,
        onchainCreationKey: String?,
        onchainCreationTxHash: String?,
        onchainCreationFromWallet: String?,
        onchainCreationConfirmedAt: java.time.Instant?,
        onchainSettlementStatus: String,
        joinUntilDate: LocalDate,
        submitUntilDate: LocalDate,
    ): Long
    fun insertProblemTest(
        problemId: Long,
        testOrder: Int,
        inputData: String,
        expectedOutput: String,
        validatorCode: String,
        validatorLanguage: String,
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

    override fun fetchProblemRow(problemId: Long): ResultRow? {
        return ProblemsTable
            .selectAll()
            .where { ProblemsTable.problemId eq problemId }
            .singleOrNull()
    }

    override fun fetchOpenProblemRow(problemId: Long): ResultRow? {
        return fetchProblemRow(problemId)
            ?.takeIf { row ->
                row[ProblemsTable.problemStatus] == ProblemLifecycleStatus.Open.dbValue
            }
    }

    override fun fetchOpenProblemRowForUpdate(problemId: Long): ResultRow? {
        return ProblemsTable
            .selectAll()
            .where { ProblemsTable.problemId eq problemId }
            .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(null, ProblemsTable))
            .singleOrNull()
            ?.takeIf { row ->
                row[ProblemsTable.problemStatus] == ProblemLifecycleStatus.Open.dbValue
            }
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
        return ProblemParticipantsTable
            .innerJoin(
                otherTable = ProblemsTable,
                onColumn = { ProblemParticipantsTable.problemId },
                otherColumn = { ProblemsTable.problemId },
            )
            .select(ProblemsTable.columns)
            .where { ProblemParticipantsTable.userId eq userId }
            .orderBy(
                ProblemsTable.createdAt to SortOrder.DESC,
                ProblemsTable.problemId to SortOrder.DESC,
            )
            .toList()
    }

    override fun isUserRegisteredForProblem(problemId: Long, userId: Long): Boolean {
        return ProblemParticipantsTable
            .selectAll()
            .where { ProblemParticipantsTable.problemId eq problemId }
            .any { row -> row[ProblemParticipantsTable.userId] == userId }
    }

    override fun insertProblemParticipant(
        problemId: Long,
        userId: Long,
        joinTxHash: String?,
        joinFromWallet: String?,
        joinedOnchainAt: java.time.Instant?,
    ) {
        ProblemParticipantsTable.insert {
            it[ProblemParticipantsTable.problemId] = problemId
            it[ProblemParticipantsTable.userId] = userId
            it[ProblemParticipantsTable.joinTxHash] = joinTxHash
            it[ProblemParticipantsTable.joinFromWallet] = joinFromWallet
            it[ProblemParticipantsTable.joinedOnchainAt] = joinedOnchainAt?.let { instant ->
                java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
            }
        }
    }

    override fun countParticipants(problemId: Long): Int {
        return ProblemParticipantsTable
            .selectAll()
            .where { ProblemParticipantsTable.problemId eq problemId }
            .count()
            .toInt()
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

    override fun fetchProblemTestRows(problemId: Long): List<ResultRow> {
        return ProblemTestsTable
            .selectAll()
            .where { ProblemTestsTable.problemId eq problemId }
            .orderBy(ProblemTestsTable.testOrder to SortOrder.ASC)
            .toList()
    }

    override fun insertProblem(
        createdByUserId: Long,
        title: String,
        description: String,
        constraints: String,
        examplesJson: String,
        referenceSolutionCode: String,
        referenceSolutionHash: String,
        referenceRuntimeMs: Int,
        referenceMemoryUsedKb: Int?,
        referenceConsensusNodes: Int,
        validationNodeId: String?,
        validationRunHash: String?,
        validationResultHash: String?,
        validationImageHash: String?,
        validatedAt: java.time.Instant,
        paymentAssetCode: String,
        prizeAmountAtomic: String,
        entryFeeAmountAtomic: String,
        requiredParticipants: Int,
        onchainCompetitionId: Long?,
        onchainContractAddress: String?,
        onchainCreationKey: String?,
        onchainCreationTxHash: String?,
        onchainCreationFromWallet: String?,
        onchainCreationConfirmedAt: java.time.Instant?,
        onchainSettlementStatus: String,
        joinUntilDate: LocalDate,
        submitUntilDate: LocalDate,
    ): Long {
        val inserted = ProblemsTable.insert {
            it[ProblemsTable.createdByUserId] = createdByUserId
            it[ProblemsTable.problemStatus] = ProblemLifecycleStatus.Open.dbValue
            it[ProblemsTable.title] = title
            it[ProblemsTable.description] = description
            it[ProblemsTable.constraintsText] = constraints
            it[ProblemsTable.examplesJson] = examplesJson
            it[ProblemsTable.referenceSolutionCode] = referenceSolutionCode
            it[ProblemsTable.referenceSolutionHash] = referenceSolutionHash
            it[ProblemsTable.referenceRuntimeMs] = referenceRuntimeMs
            it[ProblemsTable.referenceMemoryUsedKb] = referenceMemoryUsedKb
            it[ProblemsTable.referenceConsensusNodes] = referenceConsensusNodes
            it[ProblemsTable.validationNodeId] = validationNodeId
            it[ProblemsTable.validationRunHash] = validationRunHash
            it[ProblemsTable.validationResultHash] = validationResultHash
            it[ProblemsTable.validationImageHash] = validationImageHash
            it[ProblemsTable.validatedAt] = java.time.LocalDateTime.ofInstant(
                validatedAt,
                java.time.ZoneOffset.UTC,
            )
            it[ProblemsTable.paymentAssetCode] = paymentAssetCode
            it[ProblemsTable.prizeAmountAtomic] = prizeAmountAtomic
            it[ProblemsTable.entryFeeAmountAtomic] = entryFeeAmountAtomic
            it[ProblemsTable.requiredParticipants] = requiredParticipants
            it[ProblemsTable.onchainCompetitionId] = onchainCompetitionId
            it[ProblemsTable.onchainContractAddress] = onchainContractAddress
            it[ProblemsTable.onchainCreationKey] = onchainCreationKey
            it[ProblemsTable.onchainCreationTxHash] = onchainCreationTxHash
            it[ProblemsTable.onchainCreationFromWallet] = onchainCreationFromWallet
            it[ProblemsTable.onchainCreationConfirmedAt] = onchainCreationConfirmedAt?.let { instant ->
                java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
            }
            it[ProblemsTable.onchainSettlementStatus] = onchainSettlementStatus
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
        validatorLanguage: String,
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
            it[ProblemTestsTable.validatorLanguage] = validatorLanguage
            it[ProblemTestsTable.isHidden] = isHidden
            it[ProblemTestsTable.timeoutMs] = timeoutMs
            it[ProblemTestsTable.memoryLimitMb] = memoryLimitMb
        }
    }
}
