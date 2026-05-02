package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import pl.dawidszczesniak.blockchain_platform.db.CompetitionSettlementStatus
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.ProblemLifecycleStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemParticipantsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionAttestationsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionTestResultsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemTestsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemWinnersTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.UsersTable
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
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
    private val paymentAssetCatalog: PaymentAssetCatalog,
) : ProblemReadRepository, ProblemWriteRepository {
    override fun findProblemIdByOnchainCreationTxHash(txHash: String): Int? {
        return transactionRunner.inTransaction {
            ProblemsTable
                .selectAll()
                .where { ProblemsTable.onchainCreationTxHash eq txHash }
                .singleOrNull()
                ?.get(ProblemsTable.problemId)
                ?.toInt()
        }
    }

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
                    paymentAsset = paymentAssetCatalog.requireByCode(row[ProblemsTable.paymentAssetCode]).toDto(),
                    prizeAmountAtomic = row[ProblemsTable.prizeAmountAtomic],
                    entryFeeAmountAtomic = row[ProblemsTable.entryFeeAmountAtomic],
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
                referenceSolutionHash = draft.referenceSolutionHash,
                validationNodeId = draft.validationNodeId,
                validationRunHash = draft.validationRunHash,
                validationResultHash = draft.validationResultHash,
                validationImageHash = draft.validationImageHash,
                validatedAt = draft.validatedAt,
                paymentAssetCode = draft.paymentAssetCode,
                prizeAmountAtomic = draft.prizeAmountAtomic,
                entryFeeAmountAtomic = draft.entryFeeAmountAtomic,
                requiredParticipants = draft.requiredParticipants,
                onchainCompetitionId = draft.onchainCompetitionId,
                onchainContractAddress = draft.onchainContractAddress,
                onchainCreationKey = draft.onchainCreationKey,
                onchainCreationTxHash = draft.onchainCreationTxHash,
                onchainCreationConfirmedAt = draft.onchainCreationConfirmedAt,
                onchainSettlementStatus = if (draft.onchainCompetitionId != null) {
                    CompetitionSettlementStatus.Pending.dbValue
                } else {
                    CompetitionSettlementStatus.Disabled.dbValue
                },
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
                    validatorLanguage = test.validatorLanguage,
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

    override fun registerUserForProblemOnChain(
        userId: Long,
        problemId: Int,
        txHash: String,
        joinedAt: Instant,
    ): JoinProblemResult {
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
                        joinTxHash = txHash,
                        joinedOnchainAt = joinedAt,
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
                joined = true,
                registeredParticipants = registeredParticipants,
                requiredParticipants = requiredParticipants,
            )
        }
    }

    override fun fetchOnchainJoinContext(problemId: Int): OnchainJoinContext {
        return transactionRunner.inTransaction {
            val normalizedProblemId = problemId.toLong()
            val problemRow = problemDao.fetchOpenProblemRow(normalizedProblemId)
                ?: throw IllegalArgumentException("Problem not found or not open.")
            val competitionId = problemRow[ProblemsTable.onchainCompetitionId]
                ?: throw IllegalArgumentException("Problem is not linked to an on-chain competition.")
            OnchainJoinContext(
                problemId = problemId,
                competitionId = competitionId,
                paymentAsset = paymentAssetCatalog.requireByCode(problemRow[ProblemsTable.paymentAssetCode]).toDto(),
                entryFeeAmountAtomic = problemRow[ProblemsTable.entryFeeAmountAtomic],
                requiredParticipants = problemRow[ProblemsTable.requiredParticipants],
                registeredParticipants = problemDao.countParticipants(normalizedProblemId),
                joinUntilDate = problemRow[ProblemsTable.joinUntilDate],
            )
        }
    }

    override fun fetchExecutionContextForUser(userId: Long, problemId: Int): ProblemExecutionContext {
        return transactionRunner.inTransaction {
            val normalizedProblemId = problemId.toLong()
            val problemRow = problemDao.fetchOpenProblemRow(normalizedProblemId)
                ?: throw IllegalArgumentException("Problem not found or not open.")
            val onchainCompetitionId = problemRow[ProblemsTable.onchainCompetitionId]
                ?: throw IllegalArgumentException("Problem is not linked to an on-chain competition.")

            val requiredParticipants = problemRow[ProblemsTable.requiredParticipants]
            val submitUntilDate = problemRow[ProblemsTable.submitUntilDate]
            val registeredParticipants = problemDao.countParticipants(normalizedProblemId)

            val isRegistered = problemDao.isUserRegisteredForProblem(
                problemId = normalizedProblemId,
                userId = userId,
            )
            if (!isRegistered) {
                throw IllegalArgumentException("Join this competition to run code.")
            }
            if (registeredParticipants < requiredParticipants) {
                throw IllegalArgumentException("Competition has not started yet.")
            }
            if (LocalDate.now().isAfter(submitUntilDate)) {
                throw IllegalArgumentException("Submission window has ended.")
            }
            val participantWalletAddress = UsersTable
                .selectAll()
                .where { UsersTable.userId eq userId }
                .singleOrNull()
                ?.get(UsersTable.walletAddress)
                ?: throw IllegalArgumentException("Participant wallet was not found.")

            val tests = problemDao.fetchProblemTestRows(normalizedProblemId).map { row ->
                ProblemExecutionTest(
                    id = row[ProblemTestsTable.problemTestId],
                    order = row[ProblemTestsTable.testOrder],
                    inputData = row[ProblemTestsTable.inputData],
                    expectedOutput = row[ProblemTestsTable.expectedOutput],
                    validatorCode = row[ProblemTestsTable.validatorCode],
                    validatorLanguage = row[ProblemTestsTable.validatorLanguage],
                    isHidden = row[ProblemTestsTable.isHidden],
                    timeoutMs = row[ProblemTestsTable.timeoutMs],
                    memoryLimitMb = row[ProblemTestsTable.memoryLimitMb],
                )
            }
            if (tests.isEmpty()) {
                throw IllegalArgumentException("Problem has no tests configured.")
            }

            ProblemExecutionContext(
                problemId = problemId,
                onchainCompetitionId = onchainCompetitionId,
                participantWalletAddress = participantWalletAddress,
                requiredParticipants = requiredParticipants,
                registeredParticipants = registeredParticipants,
                submitUntilDate = submitUntilDate,
                tests = tests,
            )
        }
    }

    override fun createSubmissionRecord(draft: SubmissionRecordDraft): PersistedSubmissionRecord {
        return transactionRunner.inTransaction {
            val insertedSubmission = ProblemSubmissionsTable.insert {
                it[problemId] = draft.problemId.toLong()
                it[userId] = draft.userId
                it[status] = draft.status.dbValue
                it[sourceCode] = draft.sourceCode
                it[language] = draft.language
                it[codeHash] = draft.codeHash
                it[testsHash] = draft.testsHash
                it[resultHash] = draft.resultHash
                it[consensusImageHash] = draft.consensusImageHash
                it[consensusNodes] = draft.consensusNodes
                it[commitmentHash] = draft.commitmentHash
                it[runtimeMs] = draft.runtimeMs
                it[memoryUsedKb] = draft.memoryUsedKb
            }
            val submissionId = insertedSubmission[ProblemSubmissionsTable.submissionId]

            draft.testResults.forEach { test ->
                ProblemSubmissionTestResultsTable.insert {
                    it[ProblemSubmissionTestResultsTable.submissionId] = submissionId
                    it[problemTestId] = test.problemTestId
                    it[resultStatus] = test.status.dbValue
                    it[executionTimeMs] = test.executionTimeMs
                    it[memoryUsedKb] = test.memoryUsedKb
                    it[message] = test.message
                }
            }

            draft.nodeAttestations.forEach { attestation ->
                ProblemSubmissionAttestationsTable.insert {
                    it[ProblemSubmissionAttestationsTable.submissionId] = submissionId
                    it[nodeId] = attestation.nodeId
                    it[nodeUrl] = attestation.nodeUrl
                    it[imageHash] = attestation.imageHash
                    it[runHash] = attestation.runHash
                    it[resultHash] = attestation.resultHash
                    it[attestationPayloadHash] = attestation.attestationPayloadHash
                    it[attestationSignature] = attestation.attestationSignature
                    it[attestationScheme] = attestation.attestationScheme
                    it[isValid] = attestation.isValid
                    it[isConsensus] = attestation.isConsensus
                    it[nodeStatus] = attestation.status.dbValue
                    it[message] = attestation.message
                }
            }

            PersistedSubmissionRecord(
                submissionId = submissionId,
            )
        }
    }

    override fun markSubmissionResultRecorded(
        submissionId: Long,
        proxyAddress: String,
        txHash: String,
        recordedAt: Instant,
    ) {
        transactionRunner.inTransaction {
            ProblemSubmissionsTable.update(
                where = { ProblemSubmissionsTable.submissionId eq submissionId }
            ) {
                it[onchainRecordContractAddress] = proxyAddress
                it[onchainRecordTxHash] = txHash
                it[onchainRecordError] = null
                it[onchainRecordedAt] = recordedAt.toDbDateTime()
            }
        }
    }

    override fun markSubmissionResultFailed(submissionId: Long, error: String) {
        transactionRunner.inTransaction {
            ProblemSubmissionsTable.update(
                where = { ProblemSubmissionsTable.submissionId eq submissionId }
            ) {
                it[status] = SubmissionAttemptStatus.Error.dbValue
                it[onchainRecordError] = error
            }
        }
    }

    override fun fetchCompetitionsPendingSettlement(now: Instant): List<OnchainCompetitionSummary> {
        return transactionRunner.inTransaction {
            val participantCounts = participantCountsByProblem()
            ProblemsTable
                .selectAll()
                .where {
                    (ProblemsTable.onchainCompetitionId.isNotNull()) and
                        (ProblemsTable.onchainSettlementStatus eq CompetitionSettlementStatus.Pending.dbValue)
                }
                .orderBy(ProblemsTable.submitUntilDate to SortOrder.ASC)
                .mapNotNull { row ->
                    val joinDeadline = row[ProblemsTable.joinUntilDate]
                    val submitDeadline = row[ProblemsTable.submitUntilDate]
                    val today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    val problemId = row[ProblemsTable.problemId].toInt()
                    val registeredParticipants = participantCounts[row[ProblemsTable.problemId]] ?: 0
                    val requiredParticipants = row[ProblemsTable.requiredParticipants]
                    val registrationFailed = registeredParticipants < requiredParticipants &&
                        !joinDeadline.isAfter(today)
                    val submitWindowFinished = !submitDeadline.isAfter(today)
                    if (!registrationFailed && !submitWindowFinished) {
                        null
                    } else {
                        OnchainCompetitionSummary(
                            problemId = problemId,
                            title = row[ProblemsTable.title],
                            competitionId = row[ProblemsTable.onchainCompetitionId]
                                ?: return@mapNotNull null,
                            paymentAsset = paymentAssetCatalog.requireByCode(row[ProblemsTable.paymentAssetCode]).toDto(),
                            prizeAmountAtomic = row[ProblemsTable.prizeAmountAtomic],
                            joinUntilDate = joinDeadline,
                            submitUntilDate = submitDeadline,
                            requiredParticipants = requiredParticipants,
                            registeredParticipants = registeredParticipants,
                            settlementStatus = row[ProblemsTable.onchainSettlementStatus],
                        )
                    }
                }
        }
    }

    override fun fetchBestSettlementCandidate(problemId: Int): ProblemSettlementCandidate? {
        return transactionRunner.inTransaction {
            ProblemSubmissionsTable
                .innerJoin(UsersTable, { ProblemSubmissionsTable.userId }, { UsersTable.userId })
                .selectAll()
                .where {
                    (ProblemSubmissionsTable.problemId eq problemId.toLong()) and
                        (ProblemSubmissionsTable.status eq SubmissionAttemptStatus.Accepted.dbValue)
                }
                .orderBy(
                    ProblemSubmissionsTable.runtimeMs to SortOrder.ASC,
                    ProblemSubmissionsTable.memoryUsedKb to SortOrder.ASC,
                    ProblemSubmissionsTable.submissionId to SortOrder.ASC,
                )
                .limit(1)
                .singleOrNull()
                ?.let { row ->
                    ProblemSettlementCandidate(
                        submissionId = row[ProblemSubmissionsTable.submissionId],
                        userId = row[ProblemSubmissionsTable.userId],
                        walletAddress = row[UsersTable.walletAddress],
                        runtimeMs = row[ProblemSubmissionsTable.runtimeMs],
                        memoryUsedKb = row[ProblemSubmissionsTable.memoryUsedKb],
                        submittedAt = row[ProblemSubmissionsTable.submittedAt].toInstant(java.time.ZoneOffset.UTC),
                    )
                }
        }
    }

    override fun recordSettledWinner(
        problemId: Int,
        winnerUserId: Long,
        payoutAmountAtomic: String,
        txHash: String,
        settledAt: Instant,
    ) {
        transactionRunner.inTransaction {
            ProblemWinnersTable.insertIgnore {
                it[ProblemWinnersTable.problemId] = problemId.toLong()
                it[ProblemWinnersTable.winnerUserId] = winnerUserId
                it[ProblemWinnersTable.payoutAmountAtomic] = payoutAmountAtomic
                it[ProblemWinnersTable.settlementTxHash] = txHash
                it[ProblemWinnersTable.wonAt] = settledAt.toDbDateTime()
            }
            ProblemsTable.update(
                where = { ProblemsTable.problemId eq problemId.toLong() }
            ) {
                it[problemStatus] = ProblemLifecycleStatus.Closed.dbValue
                it[onchainSettlementStatus] = CompetitionSettlementStatus.Settled.dbValue
                it[onchainSettlementTxHash] = txHash
                it[onchainSettlementError] = null
                it[onchainSettledAt] = settledAt.toDbDateTime()
            }
        }
    }

    override fun markCompetitionSettlementCancelled(problemId: Int, txHash: String, settledAt: Instant) {
        transactionRunner.inTransaction {
            ProblemsTable.update(
                where = { ProblemsTable.problemId eq problemId.toLong() }
            ) {
                it[problemStatus] = ProblemLifecycleStatus.Closed.dbValue
                it[onchainSettlementStatus] = CompetitionSettlementStatus.Cancelled.dbValue
                it[onchainSettlementTxHash] = txHash
                it[onchainSettlementError] = null
                it[onchainSettledAt] = settledAt.toDbDateTime()
            }
        }
    }

    override fun markCompetitionSettlementFailed(problemId: Int, error: String) {
        transactionRunner.inTransaction {
            ProblemsTable.update(
                where = { ProblemsTable.problemId eq problemId.toLong() }
            ) {
                it[onchainSettlementStatus] = CompetitionSettlementStatus.Failed.dbValue
                it[onchainSettlementError] = error
            }
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

private fun Instant.toDbDateTime(): java.time.LocalDateTime {
    return java.time.LocalDateTime.ofInstant(this, java.time.ZoneOffset.UTC)
}
