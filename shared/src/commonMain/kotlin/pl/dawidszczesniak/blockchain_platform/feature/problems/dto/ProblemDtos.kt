package pl.dawidszczesniak.blockchain_platform.feature.problems.dto

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PaymentAssetDto

@Serializable
data class ProblemSummaryDto(
    val id: Int,
    val title: String,
    val description: String,
    val constraints: String = "",
    val examples: List<ProblemExampleDto> = emptyList(),
    val referenceSolutionCode: String = "",
    val referenceRuntimeMs: Int? = null,
    val referenceMemoryUsedKb: Int? = null,
    val referenceConsensusNodes: Int? = null,
    val paymentAsset: PaymentAssetDto,
    val prizeAmountAtomic: String,
    val entryFeeAmountAtomic: String,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val daysToStart: Int,
    val daysToJoinEnd: Int,
    val joinUntilLabel: String,
    val submitUntilLabel: String,
    val onchainCompetitionId: Long? = null,
    val creatorWalletAddress: String? = null,
    val joinDeadlineEpochSeconds: Long? = null,
    val submitDeadlineEpochSeconds: Long? = null,
    val onchainSettlementStatus: String? = null,
)

@Serializable
data class CreatedProblemDto(
    val id: Int,
    val title: String,
    val status: String,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val submissions: Int,
    val startedOn: String?,
    val finishedOn: String?,
    val registrationEnds: String?,
    val timeElapsed: String?,
    val winner: String?,
)

@Serializable
data class ParticipationProblemDto(
    val id: Int,
    val title: String,
    val status: String,
    val timeLeftLabel: String,
    val participants: Int,
    val attemptsCount: Int,
)

@Serializable
data class CreateProblemTestCaseDto(
    val inputData: String,
    val isHidden: Boolean = true,
    val memoryLimitMb: Int = 256,
)

@Serializable
data class ProblemExampleDto(
    val input: String,
    val output: String,
    val explanation: String,
)

@Serializable
data class CreateProblemRequestDto(
    val title: String,
    val description: String,
    val constraints: String = "",
    val examples: List<ProblemExampleDto> = emptyList(),
    val referenceSolutionCode: String = "",
    val referenceSolutionLanguage: String = "kotlin",
    val paymentAssetCode: String,
    val prizeAmountAtomic: String,
    val entryFeeAmountAtomic: String,
    val requiredParticipants: Int,
    val joinUntilDate: LocalDate,
    val submitUntilDate: LocalDate,
    val tests: List<String> = emptyList(),
    val testCases: List<CreateProblemTestCaseDto> = emptyList(),
)

@Serializable
data class ValidateCreateProblemRequestDto(
    val referenceSolutionCode: String,
    val referenceSolutionLanguage: String = "kotlin",
    val testCases: List<CreateProblemTestCaseDto> = emptyList(),
    val validationRunId: String? = null,
)

@Serializable
data class CancelCreateProblemValidationRequestDto(
    val validationRunId: String,
)

@Serializable
data class CreateProblemValidationTestResultDto(
    val index: Int,
    val status: String,
    val output: String? = null,
    val executionTimeMs: Int,
    val memoryUsedKb: Int? = null,
    val message: String? = null,
)

@Serializable
data class ValidateCreateProblemResponseDto(
    val total: Int,
    val successful: Int,
    val allSuccessful: Boolean,
    val results: List<CreateProblemValidationTestResultDto>,
    val runtimeMs: Int = 0,
    val memoryUsedKb: Int? = null,
    val sandboxNodeId: String? = null,
    val sandboxImageHash: String? = null,
    val sandboxRunHash: String? = null,
)

@Serializable
data class CreateProblemResponseDto(
    val id: Int,
)

@Serializable
data class PreparedWalletTransactionDto(
    val to: String,
    val data: String,
    val valueHex: String = "0x0",
)

@Serializable
data class PrepareCreateProblemResponseDto(
    val intentId: String,
    val chainId: Long,
    val proxyAddress: String,
    val explorerBaseUrl: String? = null,
    val expiresAt: String,
    val paymentAsset: PaymentAssetDto,
    val approvalTransaction: PreparedWalletTransactionDto? = null,
    val transaction: PreparedWalletTransactionDto,
)

@Serializable
data class ConfirmCreateProblemRequestDto(
    val intentId: String,
    val txHash: String,
)

@Serializable
data class JoinProblemResponseDto(
    val joined: Boolean,
    val registeredParticipants: Int,
    val requiredParticipants: Int,
)

@Serializable
data class PrepareJoinProblemResponseDto(
    val intentId: String,
    val chainId: Long,
    val proxyAddress: String,
    val explorerBaseUrl: String? = null,
    val expiresAt: String,
    val paymentAsset: PaymentAssetDto,
    val approvalTransaction: PreparedWalletTransactionDto? = null,
    val transaction: PreparedWalletTransactionDto,
)

@Serializable
data class PrepareCompetitionLifecycleActionResponseDto(
    val chainId: Long,
    val proxyAddress: String,
    val explorerBaseUrl: String? = null,
    val transaction: PreparedWalletTransactionDto,
)

@Serializable
data class ConfirmCompetitionLifecycleActionRequestDto(
    val txHash: String,
)

@Serializable
data class CompetitionLifecycleActionResponseDto(
    val competitionId: Long,
    val settlementStatus: String,
    val txHash: String,
    val explorerUrl: String? = null,
    val winnerWalletAddress: String? = null,
)

@Serializable
data class ConfirmJoinProblemRequestDto(
    val intentId: String,
    val txHash: String,
)

@Serializable
data class RunProblemRequestDto(
    val sourceCode: String,
    val language: String = "kotlin",
)

@Serializable
data class ConfirmSubmissionResultRequestDto(
    val txHash: String,
)

@Serializable
data class RunProblemTestResultDto(
    val index: Int,
    val status: String,
    val passed: Boolean,
    val hidden: Boolean,
    val executionTimeMs: Int,
    val memoryUsedKb: Int? = null,
    val input: String? = null,
    val expectedOutput: String? = null,
    val actualOutput: String? = null,
    val message: String? = null,
)

@Serializable
data class RunProblemResponseDto(
    val total: Int,
    val passed: Int,
    val allPassed: Boolean,
    val runtimeMs: Int = 0,
    val memoryUsedKb: Int? = null,
    val results: List<RunProblemTestResultDto>,
    val sandboxNodeId: String? = null,
    val sandboxImageHash: String? = null,
    val sandboxRunHash: String? = null,
)

@Serializable
data class SubmitProblemResponseDto(
    val submissionId: Long,
    val total: Int,
    val passed: Int,
    val allPassed: Boolean,
    val runtimeMs: Int,
    val memoryUsedKb: Int? = null,
    val results: List<RunProblemTestResultDto>,
    val consensusRequired: Int,
    val consensusReached: Int,
    val sandboxImageHash: String? = null,
    val sandboxResultHash: String,
    val commitmentHash: String,
    val chainId: Long,
    val proxyAddress: String,
    val walletTransaction: PreparedWalletTransactionDto? = null,
    val signature: String? = null,
    val signerWalletAddress: String? = null,
    val onchainSimulationError: String? = null,
    val onchainRecorded: Boolean = false,
    val txHash: String,
    val explorerUrl: String? = null,
)

@Serializable
data class SubmissionJudgeJobDto(
    val jobId: Long,
    val status: String,
    val language: String,
    val queuePosition: Int? = null,
    val message: String? = null,
    val retryAllowed: Boolean = false,
    val submissionId: Long? = null,
    val runPreview: RunProblemResponseDto? = null,
    val submissionResult: SubmitProblemResponseDto? = null,
)
