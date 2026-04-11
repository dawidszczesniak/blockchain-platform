package pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SandboxConfigTest {
    @Test
    fun `uses local fallback secrets in local environment`() {
        val config = SandboxConfig.fromEnvironment(
            mapOf(
                "APP_ENV" to "local",
            )
        )

        assertEquals(3, config.requiredConsensus)
        assertEquals(
            mapOf(
                "sandbox-node-1" to "local-dev-sandbox-secret-1",
                "sandbox-node-2" to "local-dev-sandbox-secret-2",
                "sandbox-node-3" to "local-dev-sandbox-secret-3",
            ),
            config.nodeAttestationSecrets,
        )
    }

    @Test
    fun `fails fast in staging without sandbox node secrets`() {
        val error = assertFailsWith<IllegalStateException> {
            SandboxConfig.fromEnvironment(
                mapOf(
                    "APP_ENV" to "staging",
                )
            )
        }

        assertEquals(
            "SANDBOX_NODE_SECRETS must be configured in staging/prod.",
            error.message,
        )
    }

    @Test
    fun `fails fast when configured secrets cannot satisfy consensus`() {
        val error = assertFailsWith<IllegalStateException> {
            SandboxConfig.fromEnvironment(
                mapOf(
                    "APP_ENV" to "staging",
                    "SANDBOX_CONSENSUS_THRESHOLD" to "2",
                    "SANDBOX_NODE_SECRETS" to "sandbox-node-1=secret-1",
                )
            )
        }

        assertEquals(
            "SANDBOX_NODE_SECRETS must define at least 2 node secrets to satisfy SANDBOX_CONSENSUS_THRESHOLD.",
            error.message,
        )
    }

    @Test
    fun `fails fast on malformed sandbox node secrets entry`() {
        val error = assertFailsWith<IllegalStateException> {
            SandboxConfig.fromEnvironment(
                mapOf(
                    "APP_ENV" to "local",
                    "SANDBOX_NODE_SECRETS" to "sandbox-node-1",
                )
            )
        }

        assertEquals(
            "SANDBOX_NODE_SECRETS entry 'sandbox-node-1' must use nodeId=secret format.",
            error.message,
        )
    }
}
