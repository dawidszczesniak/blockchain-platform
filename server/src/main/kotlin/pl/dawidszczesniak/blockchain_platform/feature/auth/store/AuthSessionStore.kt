package pl.dawidszczesniak.blockchain_platform.feature.auth.store

import java.security.SecureRandom
import java.util.Base64
import kotlin.math.max
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthServiceUnavailableException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSessionCookie
import redis.clients.jedis.JedisPooled

internal interface AuthSessionStore {
    fun createSession(session: AuthSession, ttlSeconds: Long): AuthSessionCookie
    fun fetchActiveSession(sessionId: String, nowEpochSeconds: Long): AuthSession?
    fun deleteSession(sessionId: String)
    fun deleteAllSessionsForUser(userId: Long)
}

internal class RedisAuthSessionStore(
    private val redis: JedisPooled,
    private val random: SecureRandom = SecureRandom(),
) : AuthSessionStore {
    override fun createSession(session: AuthSession, ttlSeconds: Long): AuthSessionCookie {
        val effectiveTtl = max(60L, ttlSeconds)
        val expiresAtEpochSeconds = session.issuedAtEpochSeconds + effectiveTtl
        repeat(MAX_SESSION_ID_GENERATION_RETRIES) {
            val sessionId = generateSessionId()
            val key = sessionKey(sessionId)
            val exists = runCatching { redis.exists(key) }.getOrElse {
                throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
            }
            if (exists) {
                return@repeat
            }

            runCatching {
                redis.hset(
                    key,
                    mapOf(
                        "userId" to session.userId.toString(),
                        "walletAddress" to session.walletAddress,
                        "issuedAt" to session.issuedAtEpochSeconds.toString(),
                        "expiresAt" to expiresAtEpochSeconds.toString(),
                    ),
                )
                redis.expire(key, effectiveTtl)

                val userSetKey = userSessionsKey(session.userId)
                redis.zremrangeByScore(userSetKey, Double.NEGATIVE_INFINITY, session.issuedAtEpochSeconds.toDouble())
                redis.zadd(userSetKey, expiresAtEpochSeconds.toDouble(), sessionId)
                redis.expire(userSetKey, max(effectiveTtl * 2L, 120L))
            }.getOrElse {
                throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
            }
            return AuthSessionCookie(sessionId = sessionId)
        }
        error("Could not generate unique session identifier.")
    }

    override fun fetchActiveSession(sessionId: String, nowEpochSeconds: Long): AuthSession? {
        val key = sessionKey(sessionId)
        val values = runCatching {
            redis.hmget(key, "userId", "walletAddress", "issuedAt", "expiresAt")
        }.getOrElse {
            throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
        }
        val userId = values.getOrNull(0)?.toLongOrNull() ?: return null
        val walletAddress = values.getOrNull(1)?.trim().orEmpty()
        val issuedAt = values.getOrNull(2)?.toLongOrNull() ?: return null
        val expiresAt = values.getOrNull(3)?.toLongOrNull() ?: return null
        if (walletAddress.isBlank()) return null

        if (nowEpochSeconds > expiresAt) {
            deleteSession(sessionId)
            return null
        }
        return AuthSession(
            userId = userId,
            walletAddress = walletAddress,
            issuedAtEpochSeconds = issuedAt,
        )
    }

    override fun deleteSession(sessionId: String) {
        val key = sessionKey(sessionId)
        val userId = runCatching {
            redis.hget(key, "userId")?.toLongOrNull()
        }.getOrElse {
            throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
        }
        runCatching { redis.del(key) }.getOrElse {
            throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
        }
        if (userId != null) {
            runCatching { redis.zrem(userSessionsKey(userId), sessionId) }.getOrElse {
                throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
            }
        }
    }

    override fun deleteAllSessionsForUser(userId: Long) {
        val userSetKey = userSessionsKey(userId)
        val sessionIds = runCatching {
            redis.zrange(userSetKey, 0, -1)
        }.getOrElse {
            throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
        }

        if (sessionIds.isNotEmpty()) {
            val sessionKeys = sessionIds.map { sessionId -> sessionKey(sessionId) }
            runCatching {
                redis.del(*sessionKeys.toTypedArray())
            }.getOrElse {
                throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
            }
        }
        runCatching { redis.del(userSetKey) }.getOrElse {
            throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
        }
    }

    private fun generateSessionId(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sessionKey(sessionId: String): String = "auth:session:$sessionId"

    private fun userSessionsKey(userId: Long): String = "auth:user:$userId:sessions"
}

private const val MAX_SESSION_ID_GENERATION_RETRIES = 5
