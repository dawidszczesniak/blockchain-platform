package pl.dawidszczesniak.blockchain_platform.redis

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class RedisConfig(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val database: Int,
    val ssl: Boolean,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String>): RedisConfig {
            val appEnv = env["APP_ENV"]?.trim()?.lowercase().orEmpty().ifBlank { "local" }
            val isProductionLike = appEnv == "staging" || appEnv == "prod"
            val redisUrl = env["REDIS_URL"]?.trim().orEmpty()
            val config = if (redisUrl.isNotBlank()) {
                parseFromUrl(redisUrl)
            } else {
                val host = env["REDIS_HOST"]?.trim().orEmpty().ifBlank {
                    missingRedisConfig("REDIS_URL or REDIS_HOST/REDIS_PORT")
                }
                val port = env["REDIS_PORT"]?.trim().orEmpty().ifBlank {
                    missingRedisConfig("REDIS_URL or REDIS_HOST/REDIS_PORT")
                }.toIntOrNull()?.coerceIn(1, 65535)
                    ?: error("REDIS_PORT must be a valid TCP port.")
                val username = env["REDIS_USERNAME"]?.trim().orEmpty().ifBlank { null }
                val password = env["REDIS_PASSWORD"]?.trim().orEmpty().ifBlank { null }
                val database = env["REDIS_DATABASE"]?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val ssl = env["REDIS_SSL"]?.trim()?.equals("true", ignoreCase = true) == true

                RedisConfig(
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    database = database,
                    ssl = ssl,
                )
            }

            if (isProductionLike) {
                if (config.host.contains("localhost", ignoreCase = true)) {
                    error("Redis host cannot point to localhost in staging/prod.")
                }
                if (config.password.isNullOrBlank()) {
                    error("Redis password must be configured in staging/prod.")
                }
            }

            return config
        }

        private fun parseFromUrl(rawUrl: String): RedisConfig {
            val uri = runCatching { URI(rawUrl) }.getOrElse {
                error("Invalid REDIS_URL format.")
            }
            val scheme = uri.scheme?.lowercase().orEmpty()
            if (scheme != "redis" && scheme != "rediss") {
                error("REDIS_URL must use redis:// or rediss:// scheme.")
            }

            val host = uri.host?.trim().orEmpty()
            if (host.isBlank()) {
                error("REDIS_URL must include a host.")
            }
            val port = if (uri.port > 0) uri.port else 6379

            val userInfo = uri.userInfo.orEmpty()
            val usernameRaw = userInfo.substringBefore(':', missingDelimiterValue = "")
            val passwordRaw = if (userInfo.contains(':')) {
                userInfo.substringAfter(':')
            } else {
                ""
            }
            val username = usernameRaw.urlDecode().ifBlank { null }
            val password = passwordRaw.urlDecode().ifBlank { null }

            val databasePath = uri.path?.trim('/')?.trim().orEmpty()
            val database = databasePath.toIntOrNull()?.coerceAtLeast(0) ?: 0

            return RedisConfig(
                host = host,
                port = port,
                username = username,
                password = password,
                database = database,
                ssl = scheme == "rediss",
            )
        }
    }
}

private fun String.urlDecode(): String {
    return URLDecoder.decode(this, StandardCharsets.UTF_8)
}

private fun missingRedisConfig(name: String): Nothing {
    error("$name must be configured.")
}
