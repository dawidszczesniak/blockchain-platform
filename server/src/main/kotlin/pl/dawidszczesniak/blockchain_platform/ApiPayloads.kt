package pl.dawidszczesniak.blockchain_platform

import kotlinx.serialization.Serializable
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary

@Serializable
internal data class ProblemSummaryPayload(
    val id: Int,
    val title: String,
    val description: String,
    val prizeAmount: Int,
    val entryFeeAmount: Int,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val daysToStart: Int,
    val daysToJoinEnd: Int,
    val joinUntilLabel: String,
    val submitUntilLabel: String,
)

@Serializable
internal data class CreatedProblemPayload(
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
internal data class ParticipationProblemPayload(
    val id: Int,
    val title: String,
    val status: String,
    val timeLeftLabel: String,
    val participants: Int,
    val attemptsCount: Int,
)

internal fun ProblemSummary.toPayload(): ProblemSummaryPayload {
    return ProblemSummaryPayload(
        id = id,
        title = title,
        description = description,
        prizeAmount = prizeAmount,
        entryFeeAmount = entryFeeAmount,
        requiredParticipants = requiredParticipants,
        registeredParticipants = registeredParticipants,
        daysToStart = daysToStart,
        daysToJoinEnd = daysToJoinEnd,
        joinUntilLabel = joinUntilLabel,
        submitUntilLabel = submitUntilLabel,
    )
}

internal fun CreatedProblem.toPayload(): CreatedProblemPayload {
    return CreatedProblemPayload(
        id = id,
        title = title,
        status = status.name,
        requiredParticipants = requiredParticipants,
        registeredParticipants = registeredParticipants,
        submissions = submissions,
        startedOn = startedOn,
        finishedOn = finishedOn,
        registrationEnds = registrationEnds,
        timeElapsed = timeElapsed,
        winner = winner,
    )
}

internal fun ParticipationProblem.toPayload(): ParticipationProblemPayload {
    return ParticipationProblemPayload(
        id = id,
        title = title,
        status = status.name,
        timeLeftLabel = timeLeftLabel,
        participants = participants,
        attemptsCount = attemptsCount,
    )
}
