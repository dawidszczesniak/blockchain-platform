package pl.dawidszczesniak.blockchain_platform.feature.auth.service

import java.time.Clock
import java.time.Instant
import java.security.MessageDigest
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRateLimitException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthServiceUnavailableException
import redis.clients.jedis.JedisPooled

internal class AuthRateLimiter(
    private val authConfig: AuthConfig,
    private val redis: JedisPooled,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun checkChallenge(clientKey: String) {
        checkLimit(
            scope = "challenge",
            maxRequests = authConfig.rateLimit.challengeRequestsPerMinute,
            clientKey = clientKey,
            message = "Too many auth challenge requests. Try again shortly.",
        )
    }

    fun checkVerify(clientKey: String) {
        checkLimit(
            scope = "verify",
            maxRequests = authConfig.rateLimit.verifyRequestsPerMinute,
            clientKey = clientKey,
            message = "Too many auth verify requests. Try again shortly.",
        )
    }

    fun checkSession(clientKey: String) {
        checkLimit(
            scope = "session",
            maxRequests = authConfig.rateLimit.sessionRequestsPerMinute,
            clientKey = clientKey,
            message = "Too many session requests. Try again shortly.",
        )
    }

    private fun checkLimit(
        scope: String,
        maxRequests: Int,
        clientKey: String,
        message: String,
    ) {
        val normalizedClientKey = clientKey.trim().ifBlank { "unknown-client" }
        val bucket = Instant.now(clock).epochSecond / 60L
        val redisKey = "auth:ratelimit:$scope:${sha256Hex(normalizedClientKey)}:$bucket"
        val requestCount = runCatching {
            val incremented = redis.incr(redisKey)
            if (incremented == 1L) {
                redis.expire(redisKey, RATE_LIMIT_KEY_TTL_SECONDS.toLong())
            }
            incremented
        }.getOrElse {
            throw AuthServiceUnavailableException("Auth rate limiter is temporarily unavailable.")
        }
        if (requestCount > maxRequests.toLong()) {
            throw AuthRateLimitException(message)
        }
    }
}

private fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private const val RATE_LIMIT_KEY_TTL_SECONDS = 120
