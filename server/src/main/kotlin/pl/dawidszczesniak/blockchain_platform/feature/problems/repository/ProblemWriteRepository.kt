package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import java.time.Instant
import java.time.LocalDate
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttestationStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PaymentAssetDto

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
    val referenceSolutionCode: String,
    val referenceSolutionHash: String,
    val referenceRuntimeMs: Int,
    val referenceMemoryUsedKb: Int? = null,
    val referenceConsensusNodes: Int,
    val validationNodeId: String?,
    val validationRunHash: String?,
    val validationResultHash: String?,
    val validationImageHash: String?,
    val validatedAt: Instant,
    val paymentAssetCode: String,
    val prizeAmountAtomic: String,
    val entryFeeAmountAtomic: String,
    val requiredParticipants: Int,
    val joinUntilDate: LocalDate,
    val submitUntilDate: LocalDate,
    val tests: List<NewProblemTestDraft>,
    val onchainCompetitionId: Long? = null,
    val onchainContractAddress: String? = null,
    val onchainCreationKey: String? = null,
    val onchainCreationTxHash: String? = null,
    val onchainCreationFromWallet: String? = null,
    val onchainCreationConfirmedAt: Instant? = null,
)

internal data class JoinProblemResult(
    val joined: Boolean,
    val registeredParticipants: Int,
    val requiredParticipants: Int,
)

internal data class OnchainJoinContext(
    val problemId: Int,
    val competitionId: Long,
    val paymentAsset: PaymentAssetDto,
    val entryFeeAmountAtomic: String,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val joinUntilDate: LocalDate,
)

internal data class CompetitionSettlementSnapshot(
    val problemId: Int,
    val competitionId: Long,
    val prizeAmountAtomic: String,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val problemStatus: String,
    val settlementStatus: String,
)

internal data class ProblemSettlementCandidate(
    val submissionId: Long,
    val userId: Long,
    val walletAddress: String,
    val runtimeMs: Int,
    val memoryUsedKb: Int?,
    val submittedAt: Instant,
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
    val onchainCompetitionId: Long,
    val participantWalletAddress: String,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val submitUntilDate: LocalDate,
    val tests: List<ProblemExecutionTest>,
)

internal data class SubmissionPersistedTestResult(
    val problemTestId: Long,
    val status: SubmissionTestResultStatus,
    val executionTimeMs: Int,
    val memoryUsedKb: Int? = null,
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
    val runtimeMs: Int,
    val memoryUsedKb: Int? = null,
    val testResults: List<SubmissionPersistedTestResult>,
    val nodeAttestations: List<SubmissionNodeAttestationDraft>,
)

internal data class PersistedSubmissionRecord(
    val submissionId: Long,
)

internal interface ProblemWriteRepository {
    fun findProblemIdByOnchainCreationTxHash(txHash: String): Int?
    fun createProblemForUser(userId: Long, draft: NewProblemDraft): Int
    fun registerUserForProblem(userId: Long, problemId: Int): JoinProblemResult
    fun registerUserForProblemOnChain(
        userId: Long,
        problemId: Int,
        txHash: String,
        joinedAt: Instant,
        fromWallet: String,
    ): JoinProblemResult
    fun fetchOnchainJoinContext(problemId: Int): OnchainJoinContext
    fun fetchExecutionContextForUser(userId: Long, problemId: Int): ProblemExecutionContext
    fun createSubmissionRecord(draft: SubmissionRecordDraft): PersistedSubmissionRecord
    fun markSubmissionResultRecorded(
        submissionId: Long,
        proxyAddress: String,
        txHash: String,
        recordedAt: Instant,
        fromWallet: String,
    )
    fun markSubmissionResultFailed(submissionId: Long, error: String)
    fun fetchCompetitionSettlementSnapshot(problemId: Int): CompetitionSettlementSnapshot?
    fun fetchBestSettlementCandidate(problemId: Int): ProblemSettlementCandidate?
    fun recordSettledWinner(
        problemId: Int,
        winnerUserId: Long,
        payoutAmountAtomic: String,
        txHash: String,
        settledAt: Instant,
        fromWallet: String,
    )
    fun markCompetitionSettlementCancelled(problemId: Int, txHash: String, settledAt: Instant, fromWallet: String)
    fun markCompetitionSettlementPendingError(problemId: Int, error: String)
    fun markCompetitionSettlementFailed(problemId: Int, error: String)
}
