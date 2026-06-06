package pl.dawidszczesniak.blockchain_platform.db

import org.jetbrains.exposed.sql.Database

internal object DatabaseFactory {
    fun connect(config: PostgresConfig): Database {
        Class.forName("org.postgresql.Driver")
        return Database.connect(
            url = config.jdbcUrl,
            user = config.user,
            password = config.password,
        )
    }
}
