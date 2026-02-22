package pl.dawidszczesniak.blockchain_platform.data

import kotlin.math.min
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.domain.repository.ProblemRepository

// TODO(backend): Replace mock flags and generators with real API calls.
class MockProblemRepository(
    private val config: MockDataConfig,
) : ProblemRepository {
    override suspend fun fetchProblems(): List<ProblemSummary> {
        return if (config.showEmptyProblemList) {
            emptyList()
        } else {
            generateProblems(
                startId = 1,
                startCreatedOrder = 1000,
                count = config.totalProblems.coerceAtLeast(0)
            )
        }
    }

    override suspend fun fetchCreatedProblems(): List<CreatedProblem> {
        return if (config.showEmptyCreatedList) {
            emptyList()
        } else {
            generateCreatedProblems(config.totalCreated.coerceAtLeast(0))
        }
    }

    override suspend fun fetchParticipationProblems(): List<ParticipationProblem> {
        return if (config.showEmptyParticipationList) {
            emptyList()
        } else {
            generateParticipationProblems(config.totalParticipations.coerceAtLeast(0))
        }
    }
}

private fun generateProblems(
    startId: Int,
    startCreatedOrder: Int,
    count: Int,
): List<ProblemSummary> {
    return List(count) { i ->
        val id = startId + i
        val createdOrder = startCreatedOrder - i
        val required = 10 + ((id + createdOrder) % 11)
        val registered = min(required, (id * 3 + createdOrder) % (required + 1))
        val prize = 10 + ((id + 2) % 10)
        val entry = 1 + ((id + 1) % 5)
        val daysToJoinEnd = 1 + ((id + createdOrder) % 14)
        val joinDay = (10 + (id % 18)).toString().padStart(2, '0')
        val submitDay = (1 + (id % 20)).toString().padStart(2, '0')

        ProblemSummary(
            id = id,
            createdOrder = createdOrder,
            titleLetter = 'A' + (id % 26),
            prizeAmount = prize,
            entryFeeAmount = entry,
            requiredParticipants = required,
            registeredParticipants = registered,
            daysToStart = daysToJoinEnd,
            daysToJoinEnd = daysToJoinEnd,
            joinUntilLabel = "2026-02-$joinDay",
            submitUntilLabel = "2026-03-$submitDay",
        )
    }
}

private fun generateCreatedProblems(count: Int): List<CreatedProblem> {
    return List(count) { index ->
        val status = CreatedProblemStatus.entries[index % CreatedProblemStatus.entries.size]
        val required = 20 + (index % 5)
        val registered = when (status) {
            CreatedProblemStatus.Started,
            CreatedProblemStatus.Completed -> required
            CreatedProblemStatus.Waiting -> (required - 1 - (index % 4)).coerceAtLeast(1)
            CreatedProblemStatus.Expired -> 0
        }
        val submissions = when (status) {
            CreatedProblemStatus.Started -> (index * 3 % required).coerceAtLeast(1)
            CreatedProblemStatus.Completed -> (required - (index % 4)).coerceAtLeast(1)
            CreatedProblemStatus.Waiting -> 0
            CreatedProblemStatus.Expired -> 0
        }
        val day = (index % 28) + 1
        val dayLabel = day.toString().padStart(2, '0')
        val title = when (status) {
            CreatedProblemStatus.Started -> "Started #${index + 1} — Neural net compression"
            CreatedProblemStatus.Waiting -> "Waiting #${index + 1} — Proof batching"
            CreatedProblemStatus.Completed -> "Completed #${index + 1} — Oracle aggregation"
            CreatedProblemStatus.Expired -> "Expired #${index + 1} — ZK proof relay"
        }

        CreatedProblem(
            id = index + 1,
            title = title,
            status = status,
            requiredParticipants = required,
            registeredParticipants = registered,
            submissions = submissions,
            startedOn = if (status == CreatedProblemStatus.Started) "2026-01-$dayLabel" else null,
            finishedOn = if (status == CreatedProblemStatus.Completed) "2026-01-$dayLabel" else null,
            registrationEnds = if (status == CreatedProblemStatus.Waiting) "2026-02-$dayLabel" else null,
            timeElapsed = if (status == CreatedProblemStatus.Expired) "2026-01-$dayLabel" else null,
            winner = if (status == CreatedProblemStatus.Completed) {
                "0x${(index + 10).toString(16)}…${(index * 7 + 3).toString(16)}"
            } else {
                null
            },
        )
    }
}

private fun generateParticipationProblems(count: Int): List<ParticipationProblem> {
    return List(count) { index ->
        val status = if (index % 2 == 0) {
            ParticipationStatus.Submitted
        } else {
            ParticipationStatus.NotSubmitted
        }
        val dayLeft = (index % 14) + 1
        val participants = 5 + (index % 20)
        val title = if (status == ParticipationStatus.Submitted) {
            "Joined #${index + 1} — Consensus tuning"
        } else {
            "Joined #${index + 1} — Proof batching"
        }

        ParticipationProblem(
            id = index + 1,
            title = title,
            status = status,
            timeLeftLabel = "${dayLeft}d",
            participants = participants,
        )
    }
}
