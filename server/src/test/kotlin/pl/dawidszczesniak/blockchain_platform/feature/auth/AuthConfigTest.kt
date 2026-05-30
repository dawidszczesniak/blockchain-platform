package pl.dawidszczesniak.blockchain_platform.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthConfigTest {
    @Test
    fun `requires explicit local auth values`() {
        val error = assertFailsWith<IllegalStateException> {
            AuthConfig.fromEnvironment(
                mapOf(
                    "APP_ENV" to "local",
                )
            )
        }

        assertEquals("AUTH_DOMAIN must be configured.", error.message)
    }

    @Test
    fun `uses secure short default session lifetime`() {
        val config = AuthConfig.fromEnvironment(baseAuthEnv())

        assertEquals(60L * 60L * 24L, config.sessionTtlSeconds)
        assertEquals(60L * 60L * 2L, config.sessionIdleTimeoutSeconds)
        assertEquals(1, config.maxActiveSessionsPerUser)
        assertEquals(SessionSameSite.Strict, config.sessionSameSite)
    }

    @Test
    fun `idle timeout cannot outlive absolute session ttl`() {
        val config = AuthConfig.fromEnvironment(
            baseAuthEnv() + mapOf(
                "AUTH_SESSION_TTL_SECONDS" to "600",
                "AUTH_SESSION_IDLE_TIMEOUT_SECONDS" to "3600",
            )
        )

        assertEquals(600L, config.sessionTtlSeconds)
        assertEquals(600L, config.sessionIdleTimeoutSeconds)
    }

    private fun baseAuthEnv(): Map<String, String> {
        return mapOf(
            "APP_ENV" to "local",
            "AUTH_DOMAIN" to "localhost:8081",
            "AUTH_URI" to "http://localhost:8081",
            "AUTH_SESSION_SIGN_KEY" to "local-test-sign-key-at-least-32-chars",
        )
    }
}
