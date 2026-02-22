package pl.dawidszczesniak.blockchain_platform.domain.usecase

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.domain.repository.ProblemRepository

class ProblemUseCasesTest {
    @Test
    fun getProblemSummaries_delegatesToRepository() {
        val expected = listOf(
            ProblemSummary(
                id = 1,
                createdOrder = 100,
                titleLetter = 'A',
                prizeAmount = 10,
                entryFeeAmount = 2,
                requiredParticipants = 8,
                registeredParticipants = 3,
                daysToStart = 2,
                daysToJoinEnd = 2,
                joinUntilLabel = "2026-02-10",
                submitUntilLabel = "2026-02-12",
            )
        )
        val repository = FakeProblemRepository(problemSummaries = expected)
        val useCase = GetProblemSummaries(repository)

        val actual = runSuspend { useCase() }

        assertEquals(expected, actual)
        assertEquals(1, repository.fetchProblemsCalls)
    }

    @Test
    fun getCreatedProblems_delegatesToRepository() {
        val expected = listOf(
            CreatedProblem(
                id = 7,
                title = "Started #7",
                status = CreatedProblemStatus.Started,
                requiredParticipants = 20,
                registeredParticipants = 15,
                submissions = 4,
                startedOn = "2026-01-07",
                finishedOn = null,
                registrationEnds = null,
                timeElapsed = null,
                winner = null,
            )
        )
        val repository = FakeProblemRepository(createdProblems = expected)
        val useCase = GetCreatedProblems(repository)

        val actual = runSuspend { useCase() }

        assertEquals(expected, actual)
        assertEquals(1, repository.fetchCreatedProblemsCalls)
    }

    @Test
    fun getParticipationProblems_delegatesToRepository() {
        val expected = listOf(
            ParticipationProblem(
                id = 3,
                title = "Joined #3",
                status = ParticipationStatus.Submitted,
                timeLeftLabel = "4d",
                participants = 11,
            )
        )
        val repository = FakeProblemRepository(participationProblems = expected)
        val useCase = GetParticipationProblems(repository)

        val actual = runSuspend { useCase() }

        assertEquals(expected, actual)
        assertEquals(1, repository.fetchParticipationProblemsCalls)
    }
}

private class FakeProblemRepository(
    private val problemSummaries: List<ProblemSummary> = emptyList(),
    private val createdProblems: List<CreatedProblem> = emptyList(),
    private val participationProblems: List<ParticipationProblem> = emptyList(),
) : ProblemRepository {
    var fetchProblemsCalls: Int = 0
        private set
    var fetchCreatedProblemsCalls: Int = 0
        private set
    var fetchParticipationProblemsCalls: Int = 0
        private set

    override suspend fun fetchProblems(): List<ProblemSummary> {
        fetchProblemsCalls += 1
        return problemSummaries
    }

    override suspend fun fetchCreatedProblems(): List<CreatedProblem> {
        fetchCreatedProblemsCalls += 1
        return createdProblems
    }

    override suspend fun fetchParticipationProblems(): List<ParticipationProblem> {
        fetchParticipationProblemsCalls += 1
        return participationProblems
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        }
    )
    return outcome!!.getOrThrow()
}
