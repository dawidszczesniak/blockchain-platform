package pl.dawidszczesniak.blockchain_platform.db

internal data class PostgresConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String>): PostgresConfig {
            val user = env["DB_USER"]?.takeIf { it.isNotBlank() }
                ?: env["POSTGRES_USER"]?.takeIf { it.isNotBlank() }
                ?: missingDatabaseConfig("DB_USER or POSTGRES_USER")
            val password = env["DB_PASSWORD"]?.takeIf { it.isNotBlank() }
                ?: env["POSTGRES_PASSWORD"]?.takeIf { it.isNotBlank() }
                ?: missingDatabaseConfig("DB_PASSWORD or POSTGRES_PASSWORD")
            val jdbcUrl = env["DATABASE_URL"]?.takeIf { it.isNotBlank() }
                ?: buildJdbcUrl(env)

            return PostgresConfig(
                jdbcUrl = jdbcUrl,
                user = user,
                password = password,
            )
        }
    }
}

private fun buildJdbcUrl(env: Map<String, String>): String {
    val host = env["DB_HOST"]?.trim().orEmpty().ifBlank {
        missingDatabaseConfig("DATABASE_URL or DB_HOST/DB_PORT/DB_NAME")
    }
    val port = env["DB_PORT"]?.trim().orEmpty().ifBlank {
        missingDatabaseConfig("DATABASE_URL or DB_HOST/DB_PORT/DB_NAME")
    }.toIntOrNull()?.coerceIn(1, 65535)
        ?: error("DB_PORT must be a valid TCP port.")
    val name = env["DB_NAME"]?.trim().orEmpty().ifBlank {
        missingDatabaseConfig("DATABASE_URL or DB_HOST/DB_PORT/DB_NAME")
    }
    return "jdbc:postgresql://$host:$port/$name"
}

private fun missingDatabaseConfig(name: String): Nothing {
    error("$name must be configured.")
}
