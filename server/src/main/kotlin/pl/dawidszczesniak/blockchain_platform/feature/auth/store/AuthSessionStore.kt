package pl.dawidszczesniak.blockchain_platform.feature.auth.store

import java.security.SecureRandom
import java.util.Base64
import kotlin.math.max
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthServiceUnavailableException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSessionCookie
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSessionExpirationReason
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSessionExpiredException
import redis.clients.jedis.JedisPooled

internal interface AuthSessionStore {
    fun createSession(session: AuthSession, ttlSeconds: Long, maxActiveSessionsPerUser: Int): AuthSessionCookie
    fun fetchActiveSession(sessionId: String, nowEpochSeconds: Long, idleTimeoutSeconds: Long): AuthSession?
    fun deleteSession(sessionId: String)
    fun deleteAllSessionsForUser(userId: Long)
}

internal class RedisAuthSessionStore(
    private val redis: JedisPooled,
    private val random: SecureRandom = SecureRandom(),
) : AuthSessionStore {
    override fun createSession(
        session: AuthSession,
        ttlSeconds: Long,
        maxActiveSessionsPerUser: Int,
    ): AuthSessionCookie {
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
                        "lastSeenAt" to session.lastSeenAtEpochSeconds.toString(),
                        "expiresAt" to expiresAtEpochSeconds.toString(),
                    ),
                )
                redis.expire(key, effectiveTtl)

                val userSetKey = userSessionsKey(session.userId)
                redis.zremrangeByScore(userSetKey, Double.NEGATIVE_INFINITY, session.issuedAtEpochSeconds.toDouble())
                redis.zadd(userSetKey, expiresAtEpochSeconds.toDouble(), sessionId)
                evictOverflowSessions(
                    userSetKey = userSetKey,
                    maxActiveSessionsPerUser = maxActiveSessionsPerUser,
                    protectedSessionId = sessionId,
                )
                redis.expire(userSetKey, max(effectiveTtl * 2L, 120L))
            }.getOrElse {
                throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
            }
            return AuthSessionCookie(sessionId = sessionId)
        }
        error("Could not generate unique session identifier.")
    }

    override fun fetchActiveSession(
        sessionId: String,
        nowEpochSeconds: Long,
        idleTimeoutSeconds: Long,
    ): AuthSession? {
        val key = sessionKey(sessionId)
        val values = runCatching {
            redis.hmget(key, "userId", "walletAddress", "issuedAt", "lastSeenAt", "expiresAt")
        }.getOrElse {
            throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
        }
        val userId = values.getOrNull(0)?.toLongOrNull() ?: return null
        val walletAddress = values.getOrNull(1)?.trim().orEmpty()
        val issuedAt = values.getOrNull(2)?.toLongOrNull() ?: return null
        val lastSeenAt = values.getOrNull(3)?.toLongOrNull() ?: issuedAt
        val expiresAt = values.getOrNull(4)?.toLongOrNull() ?: return null
        if (walletAddress.isBlank()) return null

        if (nowEpochSeconds > expiresAt) {
            deleteSession(sessionId)
            throw AuthSessionExpiredException(AuthSessionExpirationReason.AbsoluteTimeout)
        }
        if (nowEpochSeconds - lastSeenAt > idleTimeoutSeconds) {
            deleteSession(sessionId)
            throw AuthSessionExpiredException(AuthSessionExpirationReason.IdleTimeout)
        }
        runCatching {
            redis.hset(key, "lastSeenAt", nowEpochSeconds.toString())
        }.getOrElse {
            throw AuthServiceUnavailableException("Session store is temporarily unavailable.")
        }
        return AuthSession(
            userId = userId,
            walletAddress = walletAddress,
            issuedAtEpochSeconds = issuedAt,
            lastSeenAtEpochSeconds = nowEpochSeconds,
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

    private fun evictOverflowSessions(
        userSetKey: String,
        maxActiveSessionsPerUser: Int,
        protectedSessionId: String,
    ) {
        val sessionIds = redis.zrange(userSetKey, 0, -1)
        val overflowCount = sessionIds.size - maxActiveSessionsPerUser
        if (overflowCount <= 0) {
            return
        }
        val sessionIdsToEvict = sessionIds
            .filterNot { sessionId -> sessionId == protectedSessionId }
            .take(overflowCount)
        if (sessionIdsToEvict.isEmpty()) {
            return
        }
        redis.del(*sessionIdsToEvict.map(::sessionKey).toTypedArray())
        redis.zrem(userSetKey, *sessionIdsToEvict.toTypedArray())
    }
}

private const val MAX_SESSION_ID_GENERATION_RETRIES = 5
