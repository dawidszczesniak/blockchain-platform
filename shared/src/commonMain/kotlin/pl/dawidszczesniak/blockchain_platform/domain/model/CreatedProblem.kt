package pl.dawidszczesniak.blockchain_platform.domain.model

enum class CreatedProblemStatus {
    Started,
    Waiting,
    Completed,
    Expired,
}

data class CreatedProblem(
    val id: Int,
    val title: String,
    val status: CreatedProblemStatus,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val submissions: Int,
    val startedOn: String?,
    val finishedOn: String?,
    val registrationEnds: String?,
    val timeElapsed: String?,
    val winner: String?,
)
