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
    val expectedOutput: String,
    val validatorCode: String = "",
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
    val description: String,
    val constraints: String = "",
    val examples: List<ProblemExampleDto> = emptyList(),
    val prizeAmount: Long,
    val entryFeeAmount: Long,
    val requiredParticipants: Int,
    val joinUntilDate: String,
    val submitUntilDate: String,
    val tests: List<String> = emptyList(),
    val testCases: List<CreateProblemTestCaseDto> = emptyList(),
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
