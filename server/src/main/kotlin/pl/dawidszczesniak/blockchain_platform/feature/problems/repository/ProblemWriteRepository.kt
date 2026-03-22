package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import java.time.Instant
import java.time.LocalDate
import pl.dawidszczesniak.blockchain_platform.db.AnchorBatchStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAnchorStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttestationStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus

internal data class NewProblemTestDraft(
    val inputData: String,
    val expectedOutput: String,
    val validatorCode: String,
    val validatorLanguage: String,
    val isHidden: Boolean,
    val timeoutMs: Int,
    val memoryLimitMb: Int,
)

internal data class NewProblemExampleDraft(
    val input: String,
    val output: String,
    val explanation: String,
)

internal data class NewProblemDraft(
    val title: String,
    val description: String,
    val constraints: String,
    val examples: List<NewProblemExampleDraft>,
    val referenceSolutionHash: String,
    val validationNodeId: String?,
    val validationRunHash: String?,
    val validationResultHash: String?,
    val validationImageHash: String?,
    val validatedAt: Instant,
    val prizeAmount: Long,
    val entryFeeAmount: Long,
    val requiredParticipants: Int,
    val joinUntilDate: LocalDate,
    val submitUntilDate: LocalDate,
    val tests: List<NewProblemTestDraft>,
)

internal data class JoinProblemResult(
    val joined: Boolean,
    val registeredParticipants: Int,
    val requiredParticipants: Int,
)

internal data class ProblemExecutionTest(
    val id: Long,
    val order: Int,
    val inputData: String,
    val expectedOutput: String,
    val validatorCode: String,
    val validatorLanguage: String,
    val isHidden: Boolean,
    val timeoutMs: Int,
    val memoryLimitMb: Int,
)

internal data class ProblemExecutionContext(
    val problemId: Int,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val submitUntilDate: LocalDate,
    val tests: List<ProblemExecutionTest>,
)

internal data class SubmissionPersistedTestResult(
    val problemTestId: Long,
    val status: SubmissionTestResultStatus,
    val executionTimeMs: Int,
    val message: String? = null,
)

internal data class SubmissionNodeAttestationDraft(
    val nodeId: String,
    val nodeUrl: String,
    val imageHash: String?,
    val runHash: String?,
    val resultHash: String?,
    val attestationPayloadHash: String?,
    val attestationSignature: String?,
    val attestationScheme: String,
    val isValid: Boolean,
    val isConsensus: Boolean,
    val status: SubmissionAttestationStatus,
    val message: String? = null,
)

internal data class SubmissionAnchorDraft(
    val status: SubmissionAnchorStatus,
    val batchId: Long? = null,
    val merkleRoot: String? = null,
    val merkleProof: List<String> = emptyList(),
    val txHash: String? = null,
    val error: String? = null,
    val anchoredAt: Instant? = null,
)

internal data class SubmissionRecordDraft(
    val problemId: Int,
    val userId: Long,
    val status: SubmissionAttemptStatus,
    val sourceCode: String,
    val language: String,
    val codeHash: String,
    val testsHash: String,
    val resultHash: String,
    val consensusImageHash: String?,
    val consensusNodes: Int,
    val commitmentHash: String,
    val testResults: List<SubmissionPersistedTestResult>,
    val nodeAttestations: List<SubmissionNodeAttestationDraft>,
    val anchor: SubmissionAnchorDraft,
)

internal data class PersistedSubmissionRecord(
    val submissionId: Long,
    val anchorStatus: SubmissionAnchorStatus,
)

internal data class PendingSubmissionAnchorRecord(
    val submissionId: Long,
    val commitmentHash: String,
)

internal data class SubmissionAnchorBatchRecord(
    val batchId: Long,
    val rootHash: String,
    val submissionIds: List<Long>,
)

internal interface ProblemWriteRepository {
    fun createProblemForUser(userId: Long, draft: NewProblemDraft): Int
    fun registerUserForProblem(userId: Long, problemId: Int): JoinProblemResult
    fun fetchExecutionContextForUser(userId: Long, problemId: Int): ProblemExecutionContext
    fun createSubmissionRecord(draft: SubmissionRecordDraft): PersistedSubmissionRecord
    fun fetchPendingSubmissionAnchors(limit: Int): List<PendingSubmissionAnchorRecord>
    fun createAnchorBatch(
        rootHash: String,
        submissionIds: List<Long>,
        status: AnchorBatchStatus,
        txHash: String?,
        chainId: Long?,
        contractAddress: String?,
        failureReason: String?,
        anchoredAt: Instant?,
    ): SubmissionAnchorBatchRecord
    fun updateSubmissionAnchors(
        submissionIds: List<Long>,
        status: SubmissionAnchorStatus,
        batchId: Long,
        merkleRoot: String,
        proofBySubmission: Map<Long, List<String>>,
        txHash: String?,
        error: String?,
        anchoredAt: Instant?,
    )
}
