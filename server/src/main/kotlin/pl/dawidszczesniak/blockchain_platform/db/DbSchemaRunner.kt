package pl.dawidszczesniak.blockchain_platform.db

import java.sql.DriverManager

internal class DbSchemaRunner(
    private val config: PostgresConfig,
) {
    @Suppress("SqlSourceToSinkFlow")
    fun applySchema() {
        // Trusted source: static schema file bundled with the application.
        val schemaSql = javaClass.classLoader.getResource(SCHEMA_RESOURCE_PATH)?.readText()
            ?: error("Missing SQL resource '$SCHEMA_RESOURCE_PATH'.")
        DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(schemaSql)
            }
        }
    }
}

private const val SCHEMA_RESOURCE_PATH = "db/schema.sql"
