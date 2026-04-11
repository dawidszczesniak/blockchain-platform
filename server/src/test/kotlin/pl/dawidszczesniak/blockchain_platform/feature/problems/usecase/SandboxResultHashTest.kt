package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunTestOutput

class SandboxResultHashTest {
    @Test
    fun `ignores execution time when hashing sandbox results`() {
        val fastNodeResults = listOf(
            SandboxRunTestOutput(
                id = 1,
                order = 1,
                status = "OK",
                output = "4",
                passed = true,
                executionTimeMs = 12,
                message = null,
            )
        )
        val slowNodeResults = listOf(
            SandboxRunTestOutput(
                id = 1,
                order = 1,
                status = "OK",
                output = "4",
                passed = true,
                executionTimeMs = 37,
                message = null,
            )
        )

        assertEquals(
            computeSandboxResultHash(fastNodeResults),
            computeSandboxResultHash(slowNodeResults),
        )
    }

    @Test
    fun `changes hash when semantic test result changes`() {
        val passingResults = listOf(
            SandboxRunTestOutput(
                id = 1,
                order = 1,
                status = "OK",
                output = "4",
                passed = true,
                executionTimeMs = 12,
                message = null,
            )
        )
        val failingResults = listOf(
            SandboxRunTestOutput(
                id = 1,
                order = 1,
                status = "OK",
                output = "5",
                passed = false,
                executionTimeMs = 12,
                message = "Wrong answer",
            )
        )

        assertNotEquals(
            computeSandboxResultHash(passingResults),
            computeSandboxResultHash(failingResults),
        )
    }
}
