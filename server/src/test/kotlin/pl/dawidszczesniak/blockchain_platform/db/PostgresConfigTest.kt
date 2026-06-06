package pl.dawidszczesniak.blockchain_platform.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PostgresConfigTest {
    @Test
    fun `uses explicit database url and credentials`() {
        val config = PostgresConfig.fromEnvironment(
            mapOf(
                "DATABASE_URL" to "jdbc:postgresql://localhost:5432/blockchain_platform",
                "DB_USER" to "postgres-user",
                "DB_PASSWORD" to "postgres-password",
            )
        )

        assertEquals("jdbc:postgresql://localhost:5432/blockchain_platform", config.jdbcUrl)
        assertEquals("postgres-user", config.user)
        assertEquals("postgres-password", config.password)
    }

    @Test
    fun `fails fast without db credentials`() {
        val error = assertFailsWith<IllegalStateException> {
            PostgresConfig.fromEnvironment(
                mapOf(
                    "DATABASE_URL" to "jdbc:postgresql://localhost:5432/blockchain_platform",
                )
            )
        }

        assertEquals("DB_USER or POSTGRES_USER must be configured.", error.message)
    }
}
