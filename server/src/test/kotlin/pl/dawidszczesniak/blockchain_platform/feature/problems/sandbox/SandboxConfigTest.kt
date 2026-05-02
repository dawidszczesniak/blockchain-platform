package pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SandboxConfigTest {
    @Test
    fun `builds local secrets from indexed node env values`() {
        val config = SandboxConfig.fromEnvironment(
            mapOf(
                "APP_ENV" to "local",
                "SANDBOX_NODES" to "http://127.0.0.1:8091,http://127.0.0.1:8092,http://127.0.0.1:8093",
                "SANDBOX_IMAGE_HASH" to "local-sandbox-image-hash",
                "SANDBOX_NODE_1_SECRET" to "local-secret-1",
                "SANDBOX_NODE_2_SECRET" to "local-secret-2",
                "SANDBOX_NODE_3_SECRET" to "local-secret-3",
            )
        )

        assertEquals(3, config.requiredConsensus)
        assertEquals(
            mapOf(
                "sandbox-node-1" to "local-secret-1",
                "sandbox-node-2" to "local-secret-2",
                "sandbox-node-3" to "local-secret-3",
            ),
            config.nodeAttestationSecrets,
        )
    }

    @Test
    fun `fails fast without sandbox image hash`() {
        val error = assertFailsWith<IllegalStateException> {
            SandboxConfig.fromEnvironment(
                mapOf(
                    "APP_ENV" to "local",
                    "SANDBOX_NODES" to "http://127.0.0.1:8091,http://127.0.0.1:8092,http://127.0.0.1:8093",
                    "SANDBOX_NODE_1_SECRET" to "local-secret-1",
                    "SANDBOX_NODE_2_SECRET" to "local-secret-2",
                    "SANDBOX_NODE_3_SECRET" to "local-secret-3",
                )
            )
        }

        assertEquals("SANDBOX_IMAGE_HASH must be configured.", error.message)
    }

    @Test
    fun `fails fast without sandbox node secrets`() {
        val error = assertFailsWith<IllegalStateException> {
            SandboxConfig.fromEnvironment(
                mapOf(
                    "APP_ENV" to "staging",
                    "SANDBOX_NODES" to "http://sandbox-1,http://sandbox-2,http://sandbox-3",
                    "SANDBOX_IMAGE_HASH" to "staging-sandbox-image-hash",
                )
            )
        }

        assertEquals(
            "SANDBOX_NODE_SECRETS or SANDBOX_NODE_<N>_SECRET must be configured.",
            error.message,
        )
    }

    @Test
    fun `fails fast when configured secrets cannot satisfy consensus`() {
        val error = assertFailsWith<IllegalStateException> {
            SandboxConfig.fromEnvironment(
                mapOf(
                    "APP_ENV" to "staging",
                    "SANDBOX_NODES" to "http://sandbox-1,http://sandbox-2,http://sandbox-3",
                    "SANDBOX_IMAGE_HASH" to "staging-sandbox-image-hash",
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
                    "SANDBOX_NODES" to "http://127.0.0.1:8091,http://127.0.0.1:8092,http://127.0.0.1:8093",
                    "SANDBOX_IMAGE_HASH" to "local-sandbox-image-hash",
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
