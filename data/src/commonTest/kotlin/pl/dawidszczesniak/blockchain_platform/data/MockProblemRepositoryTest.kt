package pl.dawidszczesniak.blockchain_platform.data

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockProblemRepositoryTest {
    @Test
    fun returnsEmptyLists_whenFlagsAreEnabled() {
        val repository = MockProblemRepository(
            MockDataConfig(
                showEmptyProblemList = true,
                showEmptyCreatedList = true,
                showEmptyParticipationList = true,
                totalProblems = 50,
                totalCreated = 50,
                totalParticipations = 50,
            )
        )

        val problems = runSuspend { repository.fetchProblems() }
        val created = runSuspend { repository.fetchCreatedProblems() }
        val participation = runSuspend { repository.fetchParticipationProblems() }

        assertTrue(problems.isEmpty())
        assertTrue(created.isEmpty())
        assertTrue(participation.isEmpty())
    }

    @Test
    fun returnsConfiguredCounts_whenFlagsAreDisabled() {
        val repository = MockProblemRepository(
            MockDataConfig(
                showEmptyProblemList = false,
                showEmptyCreatedList = false,
                showEmptyParticipationList = false,
                totalProblems = 12,
                totalCreated = 7,
                totalParticipations = 9,
            )
        )

        val problems = runSuspend { repository.fetchProblems() }
        val created = runSuspend { repository.fetchCreatedProblems() }
        val participation = runSuspend { repository.fetchParticipationProblems() }

        assertEquals(12, problems.size)
        assertEquals(7, created.size)
        assertEquals(9, participation.size)
    }

    @Test
    fun coercesNegativeCounts_toZero() {
        val repository = MockProblemRepository(
            MockDataConfig(
                showEmptyProblemList = false,
                showEmptyCreatedList = false,
                showEmptyParticipationList = false,
                totalProblems = -10,
                totalCreated = -1,
                totalParticipations = -5,
            )
        )

        val problems = runSuspend { repository.fetchProblems() }
        val created = runSuspend { repository.fetchCreatedProblems() }
        val participation = runSuspend { repository.fetchParticipationProblems() }

        assertTrue(problems.isEmpty())
        assertTrue(created.isEmpty())
        assertTrue(participation.isEmpty())
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
