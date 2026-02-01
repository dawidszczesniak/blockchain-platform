package pl.dawidszczesniak.blockchain_platform.domain.model

data class ProblemSummary(
    val id: Int,
    val createdOrder: Int,
    val titleLetter: Char,
    val prizeAmount: Int,
    val entryFeeAmount: Int,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val daysToStart: Int,
    val daysToJoinEnd: Int,
    val joinUntilLabel: String,
    val submitUntilLabel: String,
)
