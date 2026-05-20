package pl.dawidszczesniak.blockchain_platform.feature.problems.domain

import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PaymentAssetDto

data class ProblemExample(
    val input: String,
    val output: String,
    val explanation: String,
)

data class ProblemSummary(
    val id: Int,
    val title: String,
    val description: String,
    val constraints: String = "",
    val examples: List<ProblemExample> = emptyList(),
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
)
