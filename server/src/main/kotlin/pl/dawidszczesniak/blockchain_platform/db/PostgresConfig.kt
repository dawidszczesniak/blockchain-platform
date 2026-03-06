package pl.dawidszczesniak.blockchain_platform.db

internal data class PostgresConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): PostgresConfig {
            val host = env["DB_HOST"]?.takeIf { it.isNotBlank() } ?: "localhost"
            val port = env["DB_PORT"]?.takeIf { it.isNotBlank() } ?: "5432"
            val name = env["DB_NAME"]?.takeIf { it.isNotBlank() } ?: "blockchain_platform"
            val user = env["DB_USER"]?.takeIf { it.isNotBlank() }
                ?: env["POSTGRES_USER"]?.takeIf { it.isNotBlank() }
                ?: "blockchain_user"
            val password = env["DB_PASSWORD"]?.takeIf { it.isNotBlank() }
                ?: env["POSTGRES_PASSWORD"]?.takeIf { it.isNotBlank() }
                ?: "blockchain_pass"
            val jdbcUrl = env["DATABASE_URL"]?.takeIf { it.isNotBlank() }
                ?: "jdbc:postgresql://$host:$port/$name"

            return PostgresConfig(
                jdbcUrl = jdbcUrl,
                user = user,
                password = password,
            )
        }
    }
}
