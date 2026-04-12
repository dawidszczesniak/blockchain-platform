package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import pl.dawidszczesniak.blockchain_platform.db.AnchorBatchStatus
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.ProblemLifecycleStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAnchorStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemParticipantsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionAttestationsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionTestResultsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemTestsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemWinnersTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.SubmissionAnchorBatchesTable
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
                referenceSolutionHash = draft.referenceSolutionHash,
                validationNodeId = draft.validationNodeId,
                validationRunHash = draft.validationRunHash,
                validationResultHash = draft.validationResultHash,
                validationImageHash = draft.validationImageHash,
                validatedAt = draft.validatedAt,
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

    override fun fetchExecutionContextForUser(userId: Long, problemId: Int): ProblemExecutionContext {
        return transactionRunner.inTransaction {
            val normalizedProblemId = problemId.toLong()
            val problemRow = problemDao.fetchOpenProblemRow(normalizedProblemId)
                ?: throw IllegalArgumentException("Problem not found or not open.")

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
                it[anchorStatus] = draft.anchor.status.dbValue
                it[anchorBatchId] = draft.anchor.batchId
                it[anchorMerkleRoot] = draft.anchor.merkleRoot
                it[anchorMerkleProofJson] = anchorProofJson.encodeToString(draft.anchor.merkleProof)
                it[anchorTxHash] = draft.anchor.txHash
                it[anchorError] = draft.anchor.error
                it[anchoredAt] = draft.anchor.anchoredAt?.toDbDateTime()
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
                anchorStatus = draft.anchor.status,
            )
        }
    }

    override fun createAnchorBatch(
        rootHash: String,
        submissionIds: List<Long>,
        status: AnchorBatchStatus,
        txHash: String?,
        chainId: Long?,
        contractAddress: String?,
        failureReason: String?,
        anchoredAt: Instant?,
    ): SubmissionAnchorBatchRecord {
        require(submissionIds.isNotEmpty()) { "Anchor batch requires at least one submission." }
        return transactionRunner.inTransaction {
            val sorted = submissionIds.sorted()
            val insertedBatch = SubmissionAnchorBatchesTable.insert {
                it[merkleRootHash] = rootHash
                it[leavesCount] = sorted.size
                it[fromSubmissionId] = sorted.first()
                it[toSubmissionId] = sorted.last()
                it[SubmissionAnchorBatchesTable.chainId] = chainId
                it[SubmissionAnchorBatchesTable.contractAddress] = contractAddress
                it[SubmissionAnchorBatchesTable.txHash] = txHash
                it[SubmissionAnchorBatchesTable.status] = status.dbValue
                it[SubmissionAnchorBatchesTable.failureReason] = failureReason
                it[SubmissionAnchorBatchesTable.anchoredAt] = anchoredAt?.toDbDateTime()
            }
            SubmissionAnchorBatchRecord(
                batchId = insertedBatch[SubmissionAnchorBatchesTable.batchId],
                rootHash = rootHash,
                submissionIds = sorted,
            )
        }
    }

    override fun updateSubmissionAnchors(
        submissionIds: List<Long>,
        status: SubmissionAnchorStatus,
        batchId: Long,
        merkleRoot: String,
        proofBySubmission: Map<Long, List<String>>,
        txHash: String?,
        error: String?,
        anchoredAt: Instant?,
    ) {
        if (submissionIds.isEmpty()) {
            return
        }
        transactionRunner.inTransaction {
            submissionIds.forEach { submissionId ->
                ProblemSubmissionsTable.update(
                    where = { ProblemSubmissionsTable.submissionId eq submissionId }
                ) {
                    it[anchorStatus] = status.dbValue
                    it[anchorBatchId] = batchId
                    it[anchorMerkleRoot] = merkleRoot
                    it[anchorMerkleProofJson] = anchorProofJson.encodeToString(
                        proofBySubmission[submissionId].orEmpty()
                    )
                    it[anchorTxHash] = txHash
                    it[anchorError] = error
                    it[ProblemSubmissionsTable.anchoredAt] = anchoredAt?.toDbDateTime()
                }
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

private val anchorProofJson = Json {
    ignoreUnknownKeys = true
}

private fun Instant.toDbDateTime(): java.time.LocalDateTime {
    return java.time.LocalDateTime.ofInstant(this, java.time.ZoneOffset.UTC)
}
