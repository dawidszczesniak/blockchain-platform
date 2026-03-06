package pl.dawidszczesniak.blockchain_platform.domain.model

data class ProblemSummary(
    val id: Int,
    val title: String,
    val description: String,
    val prizeAmount: Long,
    val entryFeeAmount: Long,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val daysToStart: Int,
    val daysToJoinEnd: Int,
    val joinUntilLabel: String,
    val submitUntilLabel: String,
)
