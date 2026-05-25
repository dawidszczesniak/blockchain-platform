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
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionJudgeJobsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionTestResultsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemTestsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemWinnersTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.UsersTable
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.hasContractDeadlinePassed
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.toContractDeadlineEpochSeconds
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemDao
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemRowColumns
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemExample
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemExampleDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CompetitionSettlementWakeupSignal
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.scheduleCompetitionSettlementJobsInCurrentTransaction

internal interface ProblemReadRepository {
    fun fetchProblemSummaries(): List<ProblemSummary>
    fun fetchProblemSummaryById(problemId: Int): ProblemSummary?
    fun fetchCreatedProblemsForUser(userId: Long): List<CreatedProblem>
    fun fetchParticipationProblemsForUser(userId: Long): List<ParticipationProblem>
}

internal class ProblemReadRepositoryImpl(
    private val problemDao: ProblemDao,
    private val transactionRunner: DbTransactionRunner,
    private val paymentAssetCatalog: PaymentAssetCatalog,
    private val settlementWakeupSignal: CompetitionSettlementWakeupSignal,
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
                row.toProblemSummary(
                    registeredParticipants = participantCounts[row[ProblemsTable.problemId]] ?: 0,
                    today = today,
                    includeReferenceDetails = false,
                )
            }
        }
    }

    override fun fetchProblemSummaryById(problemId: Int): ProblemSummary? {
        return transactionRunner.inTransaction {
            val row = problemDao.fetchProblemRow(problemId.toLong()) ?: return@inTransaction null
            row.toProblemSummary(
                registeredParticipants = problemDao.countParticipants(problemId.toLong()),
                today = LocalDate.now(),
                includeReferenceDetails = true,
            )
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
        val problemId = transactionRunner.inTransaction {
            val problemId = problemDao.insertProblem(
                createdByUserId = userId,
                title = draft.title,
                description = draft.description,
                constraints = draft.constraints,
                examplesJson = serializeProblemExamples(draft.examples),
                referenceSolutionCode = draft.referenceSolutionCode,
                referenceSolutionHash = draft.referenceSolutionHash,
                referenceRuntimeMs = draft.referenceRuntimeMs,
                referenceMemoryUsedKb = draft.referenceMemoryUsedKb,
                referenceConsensusNodes = draft.referenceConsensusNodes,
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
                onchainCreationFromWallet = draft.onchainCreationFromWallet,
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
            draft.onchainCompetitionId?.let { competitionId ->
                scheduleCompetitionSettlementJobsInCurrentTransaction(
                    problemId = problemId,
                    competitionId = competitionId,
                    joinUntilDate = draft.joinUntilDate,
                    submitUntilDate = draft.submitUntilDate,
                    createdAt = draft.onchainCreationConfirmedAt ?: Instant.now(),
                )
            }
            problemId.toInt()
        }
        if (draft.onchainCompetitionId != null) {
            settlementWakeupSignal.notifyWorkScheduled()
        }
        return problemId
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
                if (Instant.now().hasContractDeadlinePassed(joinUntilDate)) {
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
        fromWallet: String,
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
                if (Instant.now().hasContractDeadlinePassed(joinUntilDate)) {
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
                        joinFromWallet = fromWallet,
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
            if (Instant.now().hasContractDeadlinePassed(submitUntilDate)) {
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
                it[onchainSubmissionId] = draft.onchainSubmissionId
                it[problemId] = draft.problemId.toLong()
                it[userId] = draft.userId
                it[status] = draft.status.dbValue
                it[sourceCode] = draft.sourceCode
                it[language] = draft.language
                it[codeHash] = draft.codeHash
                it[challengeHash] = draft.challengeHash
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
                onchainSubmissionId = insertedSubmission[ProblemSubmissionsTable.onchainSubmissionId],
            )
        }
    }

    override fun markSubmissionResultRecorded(
        submissionId: Long,
        proxyAddress: String,
        txHash: String,
        recordedAt: Instant,
        fromWallet: String,
    ) {
        transactionRunner.inTransaction {
            ProblemSubmissionsTable.update(
                where = { ProblemSubmissionsTable.submissionId eq submissionId }
            ) {
                it[status] = SubmissionAttemptStatus.Accepted.dbValue
                it[onchainRecordContractAddress] = proxyAddress
                it[onchainRecordTxHash] = txHash
                it[onchainRecordFromWallet] = fromWallet
                it[onchainRecordError] = null
                it[onchainRecordedAt] = recordedAt.toDbDateTime()
            }
        }
    }

    override fun markSubmissionResultPendingConfirmation(
        submissionId: Long,
        proxyAddress: String,
        txHash: String,
        fromWallet: String,
    ) {
        transactionRunner.inTransaction {
            ProblemSubmissionsTable.update(
                where = { ProblemSubmissionsTable.submissionId eq submissionId }
            ) {
                it[onchainRecordContractAddress] = proxyAddress
                it[onchainRecordTxHash] = txHash
                it[onchainRecordFromWallet] = fromWallet
                it[onchainRecordError] = null
                it[onchainRecordedAt] = null
            }
        }
    }

    override fun markSubmissionResultPendingError(submissionId: Long, error: String, txHash: String?) {
        transactionRunner.inTransaction {
            ProblemSubmissionsTable.update(
                where = { ProblemSubmissionsTable.submissionId eq submissionId }
            ) {
                if (!txHash.isNullOrBlank()) {
                    it[onchainRecordTxHash] = txHash
                }
                it[onchainRecordError] = error
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

    override fun fetchSubmissionReceiptRetryContext(submissionId: Long): SubmissionReceiptRetryContext? {
        return transactionRunner.inTransaction {
            ProblemSubmissionsTable
                .selectAll()
                .where { ProblemSubmissionsTable.submissionId eq submissionId }
                .singleOrNull()
                ?.let { row ->
                    SubmissionReceiptRetryContext(
                        submissionId = row[ProblemSubmissionsTable.submissionId],
                        txHash = row[ProblemSubmissionsTable.onchainRecordTxHash],
                        recordedAt = row[ProblemSubmissionsTable.onchainRecordedAt]?.toInstant(java.time.ZoneOffset.UTC),
                        contractAddress = row[ProblemSubmissionsTable.onchainRecordContractAddress],
                        fromWallet = row[ProblemSubmissionsTable.onchainRecordFromWallet],
                        currentError = row[ProblemSubmissionsTable.onchainRecordError],
                    )
                }
        }
    }

    override fun fetchSubmissionOnchainConfirmationContext(
        userId: Long,
        submissionId: Long,
    ): SubmissionOnchainConfirmationContext? {
        return transactionRunner.inTransaction {
            val resultPayloadJson = ProblemSubmissionJudgeJobsTable
                .select(ProblemSubmissionJudgeJobsTable.resultPayloadJson)
                .where { ProblemSubmissionJudgeJobsTable.submissionId eq submissionId }
                .orderBy(ProblemSubmissionJudgeJobsTable.completedAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.get(ProblemSubmissionJudgeJobsTable.resultPayloadJson)

            ProblemSubmissionsTable
                .innerJoin(ProblemsTable, { ProblemSubmissionsTable.problemId }, { ProblemsTable.problemId })
                .innerJoin(UsersTable, { ProblemSubmissionsTable.userId }, { UsersTable.userId })
                .selectAll()
                .where {
                    (ProblemSubmissionsTable.submissionId eq submissionId) and
                        (ProblemSubmissionsTable.userId eq userId)
                }
                .singleOrNull()
                ?.let { row ->
                    val competitionId = row[ProblemsTable.onchainCompetitionId] ?: return@let null
                    val consensusImageHash = row[ProblemSubmissionsTable.consensusImageHash] ?: return@let null
                    val memoryUsedKb = row[ProblemSubmissionsTable.memoryUsedKb] ?: return@let null
                    SubmissionOnchainConfirmationContext(
                        submissionId = row[ProblemSubmissionsTable.submissionId],
                        onchainSubmissionId = row[ProblemSubmissionsTable.onchainSubmissionId],
                        problemId = row[ProblemSubmissionsTable.problemId].toInt(),
                        competitionId = competitionId,
                        participantWalletAddress = row[UsersTable.walletAddress],
                        codeHash = row[ProblemSubmissionsTable.codeHash],
                        challengeHash = row[ProblemSubmissionsTable.challengeHash],
                        resultHash = row[ProblemSubmissionsTable.resultHash],
                        consensusImageHash = consensusImageHash,
                        consensusNodes = row[ProblemSubmissionsTable.consensusNodes],
                        commitmentHash = row[ProblemSubmissionsTable.commitmentHash],
                        runtimeMs = row[ProblemSubmissionsTable.runtimeMs],
                        memoryUsedKb = memoryUsedKb,
                        resultPayloadJson = resultPayloadJson,
                        onchainRecordTxHash = row[ProblemSubmissionsTable.onchainRecordTxHash],
                        onchainRecordedAt = row[ProblemSubmissionsTable.onchainRecordedAt]
                            ?.toInstant(java.time.ZoneOffset.UTC),
                    )
                }
        }
    }

    override fun updateSubmissionAcceptedResultPayload(submissionId: Long, payloadJson: String) {
        transactionRunner.inTransaction {
            ProblemSubmissionJudgeJobsTable.update(
                where = { ProblemSubmissionJudgeJobsTable.submissionId eq submissionId }
            ) {
                it[ProblemSubmissionJudgeJobsTable.resultPayloadJson] = payloadJson
            }
        }
    }

    override fun fetchCompetitionSettlementSnapshot(problemId: Int): CompetitionSettlementSnapshot? {
        return transactionRunner.inTransaction {
            ProblemsTable
                .selectAll()
                .where {
                    ProblemsTable.problemId eq problemId.toLong()
                }
                .singleOrNull()
                ?.let { row ->
                    CompetitionSettlementSnapshot(
                        problemId = row[ProblemsTable.problemId].toInt(),
                        competitionId = row[ProblemsTable.onchainCompetitionId] ?: return@let null,
                        prizeAmountAtomic = row[ProblemsTable.prizeAmountAtomic],
                        requiredParticipants = row[ProblemsTable.requiredParticipants],
                        registeredParticipants = problemDao.countParticipants(row[ProblemsTable.problemId]),
                        problemStatus = row[ProblemsTable.problemStatus],
                        settlementStatus = row[ProblemsTable.onchainSettlementStatus],
                    )
                }
        }
    }

    override fun fetchCompetitionLifecycleContext(problemId: Int): CompetitionLifecycleContext? {
        return transactionRunner.inTransaction {
            ProblemsTable
                .innerJoin(UsersTable, { ProblemsTable.createdByUserId }, { UsersTable.userId })
                .selectAll()
                .where { ProblemsTable.problemId eq problemId.toLong() }
                .singleOrNull()
                ?.let { row ->
                    val competitionId = row[ProblemsTable.onchainCompetitionId] ?: return@let null
                    CompetitionLifecycleContext(
                        problemId = row[ProblemsTable.problemId].toInt(),
                        competitionId = competitionId,
                        creatorWalletAddress = row[UsersTable.walletAddress],
                        prizeAmountAtomic = row[ProblemsTable.prizeAmountAtomic],
                        requiredParticipants = row[ProblemsTable.requiredParticipants],
                        registeredParticipants = problemDao.countParticipants(row[ProblemsTable.problemId]),
                        joinUntilDate = row[ProblemsTable.joinUntilDate],
                        submitUntilDate = row[ProblemsTable.submitUntilDate],
                        settlementStatus = row[ProblemsTable.onchainSettlementStatus],
                        existingTxHash = row[ProblemsTable.onchainSettlementTxHash],
                    )
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
                        (ProblemSubmissionsTable.status eq SubmissionAttemptStatus.Accepted.dbValue) and
                        ProblemSubmissionsTable.onchainRecordedAt.isNotNull()
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

    override fun findUserIdByWalletAddress(walletAddress: String): Long? {
        return transactionRunner.inTransaction {
            UsersTable
                .select(UsersTable.userId)
                .where { UsersTable.walletAddress eq walletAddress }
                .singleOrNull()
                ?.get(UsersTable.userId)
        }
    }

    override fun recordSettledWinner(
        problemId: Int,
        winnerUserId: Long,
        payoutAmountAtomic: String,
        txHash: String,
        settledAt: Instant,
        fromWallet: String,
    ) {
        transactionRunner.inTransaction {
            ProblemWinnersTable.insertIgnore {
                it[ProblemWinnersTable.problemId] = problemId.toLong()
                it[ProblemWinnersTable.winnerUserId] = winnerUserId
                it[ProblemWinnersTable.payoutAmountAtomic] = payoutAmountAtomic
                it[ProblemWinnersTable.settlementTxHash] = txHash
                it[ProblemWinnersTable.settlementFromWallet] = fromWallet
                it[ProblemWinnersTable.wonAt] = settledAt.toDbDateTime()
            }
            ProblemsTable.update(
                where = { ProblemsTable.problemId eq problemId.toLong() }
            ) {
                it[problemStatus] = ProblemLifecycleStatus.Closed.dbValue
                it[onchainSettlementStatus] = CompetitionSettlementStatus.Settled.dbValue
                it[onchainSettlementTxHash] = txHash
                it[onchainSettlementFromWallet] = fromWallet
                it[onchainSettlementError] = null
                it[onchainSettledAt] = settledAt.toDbDateTime()
            }
        }
    }

    override fun markCompetitionSettlementCancelled(problemId: Int, txHash: String, settledAt: Instant, fromWallet: String) {
        transactionRunner.inTransaction {
            ProblemsTable.update(
                where = { ProblemsTable.problemId eq problemId.toLong() }
            ) {
                it[problemStatus] = ProblemLifecycleStatus.Closed.dbValue
                it[onchainSettlementStatus] = CompetitionSettlementStatus.Cancelled.dbValue
                it[onchainSettlementTxHash] = txHash
                it[onchainSettlementFromWallet] = fromWallet
                it[onchainSettlementError] = null
                it[onchainSettledAt] = settledAt.toDbDateTime()
            }
        }
    }

    override fun markCompetitionSettlementPendingError(problemId: Int, error: String) {
        transactionRunner.inTransaction {
            ProblemsTable.update(
                where = { ProblemsTable.problemId eq problemId.toLong() }
            ) {
                it[onchainSettlementError] = error
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

    private fun org.jetbrains.exposed.sql.ResultRow.toProblemSummary(
        registeredParticipants: Int,
        today: LocalDate,
        includeReferenceDetails: Boolean,
    ): ProblemSummary {
        val daysToJoinEnd = daysBetween(today, this[ProblemsTable.joinUntilDate]).coerceAtLeast(0)
        val createdByUserId = this[ProblemsTable.createdByUserId]
        val creatorWalletAddress = UsersTable
            .select(UsersTable.walletAddress)
            .where { UsersTable.userId eq createdByUserId }
            .singleOrNull()
            ?.get(UsersTable.walletAddress)
        return ProblemSummary(
            id = this[ProblemsTable.problemId].toInt(),
            title = this[ProblemsTable.title],
            description = this[ProblemsTable.description],
            constraints = this[ProblemsTable.constraintsText],
            examples = parseProblemExamples(this[ProblemsTable.examplesJson]),
            referenceSolutionCode = if (includeReferenceDetails) this[ProblemsTable.referenceSolutionCode] else "",
            referenceRuntimeMs = if (includeReferenceDetails) this[ProblemsTable.referenceRuntimeMs] else null,
            referenceMemoryUsedKb = if (includeReferenceDetails) this[ProblemsTable.referenceMemoryUsedKb] else null,
            referenceConsensusNodes = if (includeReferenceDetails) this[ProblemsTable.referenceConsensusNodes] else null,
            paymentAsset = paymentAssetCatalog.requireByCode(this[ProblemsTable.paymentAssetCode]).toDto(),
            prizeAmountAtomic = this[ProblemsTable.prizeAmountAtomic],
            entryFeeAmountAtomic = this[ProblemsTable.entryFeeAmountAtomic],
            requiredParticipants = this[ProblemsTable.requiredParticipants],
            registeredParticipants = registeredParticipants,
            daysToStart = daysToJoinEnd,
            daysToJoinEnd = daysToJoinEnd,
            joinUntilLabel = this[ProblemsTable.joinUntilDate].toString(),
            submitUntilLabel = this[ProblemsTable.submitUntilDate].toString(),
            onchainCompetitionId = this[ProblemsTable.onchainCompetitionId],
            creatorWalletAddress = creatorWalletAddress,
            joinDeadlineEpochSeconds = this[ProblemsTable.joinUntilDate].toContractDeadlineEpochSeconds(),
            submitDeadlineEpochSeconds = this[ProblemsTable.submitUntilDate].toContractDeadlineEpochSeconds(),
            onchainSettlementStatus = this[ProblemsTable.onchainSettlementStatus],
        )
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
