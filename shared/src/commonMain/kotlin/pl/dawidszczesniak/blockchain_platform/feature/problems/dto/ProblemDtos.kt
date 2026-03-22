package pl.dawidszczesniak.blockchain_platform.feature.problems.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProblemSummaryDto(
    val id: Int,
    val title: String,
    val description: String,
    val constraints: String = "",
    val examples: List<ProblemExampleDto> = emptyList(),
    val prizeAmount: Long,
    val entryFeeAmount: Long,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val daysToStart: Int,
    val daysToJoinEnd: Int,
    val joinUntilLabel: String,
    val submitUntilLabel: String,
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
    val timeoutMs: Int = 1000,
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
    val title: String = "",
    val description: String,
    val constraints: String = "",
    val examples: List<ProblemExampleDto> = emptyList(),
    val referenceSolutionCode: String = "",
    val referenceSolutionLanguage: String = "kotlin",
    val prizeAmount: Long,
    val entryFeeAmount: Long,
    val requiredParticipants: Int,
    val joinUntilDate: String,
    val submitUntilDate: String,
    val tests: List<String> = emptyList(),
    val testCases: List<CreateProblemTestCaseDto> = emptyList(),
)

@Serializable
data class ValidateCreateProblemRequestDto(
    val referenceSolutionCode: String,
    val referenceSolutionLanguage: String = "kotlin",
    val testCases: List<CreateProblemTestCaseDto> = emptyList(),
)

@Serializable
data class CreateProblemValidationTestResultDto(
    val index: Int,
    val status: String,
    val output: String? = null,
    val executionTimeMs: Int,
    val message: String? = null,
)

@Serializable
data class ValidateCreateProblemResponseDto(
    val total: Int,
    val successful: Int,
    val allSuccessful: Boolean,
    val results: List<CreateProblemValidationTestResultDto>,
    val sandboxNodeId: String? = null,
    val sandboxImageHash: String? = null,
    val sandboxRunHash: String? = null,
)

@Serializable
data class CreateProblemResponseDto(
    val id: Int,
)

@Serializable
data class JoinProblemResponseDto(
    val joined: Boolean,
    val registeredParticipants: Int,
    val requiredParticipants: Int,
)

@Serializable
data class RunProblemRequestDto(
    val sourceCode: String,
    val language: String = "kotlin",
)

@Serializable
data class RunProblemTestResultDto(
    val index: Int,
    val status: String,
    val passed: Boolean,
    val hidden: Boolean,
    val executionTimeMs: Int,
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
    val results: List<RunProblemTestResultDto>,
    val consensusRequired: Int,
    val consensusReached: Int,
    val sandboxImageHash: String? = null,
    val sandboxResultHash: String,
    val commitmentHash: String,
    val anchorStatus: String,
    val anchorBatchId: Long? = null,
    val anchorMerkleRoot: String? = null,
    val anchorProof: List<String> = emptyList(),
    val anchorTxHash: String? = null,
    val anchorExplorerUrl: String? = null,
    val anchorError: String? = null,
)
