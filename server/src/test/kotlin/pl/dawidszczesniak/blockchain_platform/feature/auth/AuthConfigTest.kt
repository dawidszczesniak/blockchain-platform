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
}
