package pl.dawidszczesniak.blockchain_platform.feature.auth.store

import java.time.Instant
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthServiceUnavailableException
import redis.clients.jedis.JedisPooled

internal data class StoredWalletChallenge(
    val nonce: String,
    val message: String,
    val walletAddress: String,
    val chainId: Long,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

internal enum class ChallengeInsertResult {
    Created,
    TooManyActive,
    AlreadyExists,
}

internal sealed interface ChallengeConsumeResult {
    data class Success(val challenge: StoredWalletChallenge) : ChallengeConsumeResult
    data object NotFound : ChallengeConsumeResult
    data object MessageMismatch : ChallengeConsumeResult
}

internal interface WalletChallengeStore {
    fun insertChallenge(challenge: StoredWalletChallenge, maxActiveChallengesPerWallet: Int, now: Instant): ChallengeInsertResult
    fun consumeChallenge(nonce: String, expectedMessage: String, now: Instant): ChallengeConsumeResult
}

internal class RedisWalletChallengeStore(
    private val redis: JedisPooled,
) : WalletChallengeStore {
    override fun insertChallenge(
        challenge: StoredWalletChallenge,
        maxActiveChallengesPerWallet: Int,
        now: Instant,
    ): ChallengeInsertResult {
        val raw = runCatching {
            val ttlSeconds = (challenge.expiresAt.epochSecond - now.epochSecond).coerceAtLeast(1L)
            redis.eval(
                INSERT_CHALLENGE_SCRIPT,
                listOf(walletChallengesKey(challenge.walletAddress), challengeKey(challenge.nonce)),
                listOf(
                    now.epochSecond.toString(),
                    maxActiveChallengesPerWallet.toString(),
                    challenge.expiresAt.epochSecond.toString(),
                    ttlSeconds.toString(),
                    challenge.nonce,
                    challenge.message,
                    challenge.walletAddress,
                    challenge.chainId.toString(),
                    challenge.issuedAt.epochSecond.toString(),
                ),
            )
        }.getOrElse {
            throw AuthServiceUnavailableException("Challenge store is temporarily unavailable.")
        }

        return when (raw.toResultCode()) {
            1L -> ChallengeInsertResult.Created
            -1L -> ChallengeInsertResult.TooManyActive
            else -> ChallengeInsertResult.AlreadyExists
        }
    }

    override fun consumeChallenge(nonce: String, expectedMessage: String, now: Instant): ChallengeConsumeResult {
        val key = challengeKey(nonce)
        val raw = runCatching {
            redis.eval(
                CONSUME_CHALLENGE_SCRIPT,
                listOf(key),
                listOf(expectedMessage),
            )
        }.getOrElse {
            throw AuthServiceUnavailableException("Challenge store is temporarily unavailable.")
        }
        val payload = raw as? List<*> ?: return ChallengeConsumeResult.NotFound
        val status = payload.getOrNull(0).toResultCode()
        if (status == 0L) {
            return ChallengeConsumeResult.NotFound
        }
        if (status == 2L) {
            return ChallengeConsumeResult.MessageMismatch
        }

        val challenge = StoredWalletChallenge(
            nonce = payload.getOrNull(1).toPayloadValue(),
            message = payload.getOrNull(2).toPayloadValue(),
            walletAddress = payload.getOrNull(3).toPayloadValue(),
            chainId = payload.getOrNull(4).toPayloadValue().toLong(),
            issuedAt = Instant.ofEpochSecond(payload.getOrNull(5).toPayloadValue().toLong()),
            expiresAt = Instant.ofEpochSecond(payload.getOrNull(6).toPayloadValue().toLong()),
        )
        val walletSetKey = walletChallengesKey(challenge.walletAddress)
        runCatching {
            redis.zrem(walletSetKey, challenge.nonce)
            redis.zremrangeByScore(walletSetKey, Double.NEGATIVE_INFINITY, now.epochSecond.toDouble())
        }.getOrElse {
            throw AuthServiceUnavailableException("Challenge store is temporarily unavailable.")
        }
        return ChallengeConsumeResult.Success(challenge)
    }

    private fun challengeKey(nonce: String): String = "auth:challenge:$nonce"

    private fun walletChallengesKey(walletAddress: String): String = "auth:wallet:$walletAddress:challenges"
}

private fun Any?.toResultCode(): Long {
    return when (this) {
        is Long -> this
        is Int -> this.toLong()
        else -> this?.toString()?.toLongOrNull() ?: 0L
    }
}

private fun Any?.toPayloadValue(): String {
    return this?.toString()?.trim().orEmpty()
}

private const val INSERT_CHALLENGE_SCRIPT = """
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
local active = redis.call('ZCARD', KEYS[1])
if active >= tonumber(ARGV[2]) then
  return -1
end
if redis.call('EXISTS', KEYS[2]) == 1 then
  return 0
end
redis.call('HSET', KEYS[2],
  'nonce', ARGV[5],
  'message', ARGV[6],
  'walletAddress', ARGV[7],
  'chainId', ARGV[8],
  'issuedAt', ARGV[9],
  'expiresAt', ARGV[3]
)
redis.call('EXPIRE', KEYS[2], ARGV[4])
redis.call('ZADD', KEYS[1], ARGV[3], ARGV[5])
local walletTtl = tonumber(ARGV[4]) * 2
if walletTtl < 120 then
  walletTtl = 120
end
redis.call('EXPIRE', KEYS[1], walletTtl)
return 1
"""

private const val CONSUME_CHALLENGE_SCRIPT = """
local storedMessage = redis.call('HGET', KEYS[1], 'message')
if not storedMessage then
  return {0}
end
if storedMessage ~= ARGV[1] then
  return {2}
end
local payload = redis.call('HMGET', KEYS[1], 'nonce', 'message', 'walletAddress', 'chainId', 'issuedAt', 'expiresAt')
redis.call('DEL', KEYS[1])
return {1, payload[1], payload[2], payload[3], payload[4], payload[5], payload[6]}
"""
