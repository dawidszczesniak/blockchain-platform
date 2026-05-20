package pl.dawidszczesniak.blockchain_platform.feature.problems.mapper

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreatedProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ParticipationProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemExampleDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemSummaryDto

internal fun ProblemSummary.toDto(): ProblemSummaryDto {
    return ProblemSummaryDto(
        id = id,
        title = title,
        description = description,
        constraints = constraints,
        examples = examples.map { example ->
            ProblemExampleDto(
                input = example.input,
                output = example.output,
                explanation = example.explanation,
            )
        },
        referenceSolutionCode = referenceSolutionCode,
        referenceRuntimeMs = referenceRuntimeMs,
        referenceMemoryUsedKb = referenceMemoryUsedKb,
        referenceConsensusNodes = referenceConsensusNodes,
        paymentAsset = paymentAsset,
        prizeAmountAtomic = prizeAmountAtomic,
        entryFeeAmountAtomic = entryFeeAmountAtomic,
        requiredParticipants = requiredParticipants,
        registeredParticipants = registeredParticipants,
        daysToStart = daysToStart,
        daysToJoinEnd = daysToJoinEnd,
        joinUntilLabel = joinUntilLabel,
        submitUntilLabel = submitUntilLabel,
    )
}

internal fun CreatedProblem.toDto(): CreatedProblemDto {
    return CreatedProblemDto(
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

internal fun ParticipationProblem.toDto(): ParticipationProblemDto {
    return ParticipationProblemDto(
        id = id,
        title = title,
        status = status.name,
        timeLeftLabel = timeLeftLabel,
        participants = participants,
        attemptsCount = attemptsCount,
    )
}
