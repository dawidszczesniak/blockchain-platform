package pl.dawidszczesniak.blockchain_platform.feature.problems.mapper

import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemExample
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreatedProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ParticipationProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemSummaryDto

internal fun ProblemSummaryDto.toDomain(): ProblemSummary {
    return ProblemSummary(
        id = id,
        title = title,
        description = description,
        constraints = constraints,
        examples = examples.map { example ->
            ProblemExample(
                input = example.input,
                output = example.output,
                explanation = example.explanation,
            )
        },
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

internal fun CreatedProblemDto.toDomain(): CreatedProblem {
    return CreatedProblem(
        id = id,
        title = title,
        status = parseCreatedStatus(status),
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

internal fun ParticipationProblemDto.toDomain(): ParticipationProblem {
    return ParticipationProblem(
        id = id,
        title = title,
        status = parseParticipationStatus(status),
        timeLeftLabel = timeLeftLabel,
        participants = participants,
        attemptsCount = attemptsCount,
    )
}

private fun parseCreatedStatus(raw: String): CreatedProblemStatus {
    return try {
        CreatedProblemStatus.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        error("Unknown created status '$raw'.")
    }
}

private fun parseParticipationStatus(raw: String): ParticipationStatus {
    return try {
        ParticipationStatus.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        error("Unknown participation status '$raw'.")
    }
}
