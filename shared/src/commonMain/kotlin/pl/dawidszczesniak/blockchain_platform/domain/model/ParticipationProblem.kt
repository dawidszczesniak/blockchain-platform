package pl.dawidszczesniak.blockchain_platform.domain.model

enum class ParticipationStatus {
    Submitted,
    NotSubmitted,
}

data class ParticipationProblem(
    val id: Int,
    val title: String,
    val status: ParticipationStatus,
    val timeLeftLabel: String,
    val participants: Int,
    val attemptsCount: Int,
)
