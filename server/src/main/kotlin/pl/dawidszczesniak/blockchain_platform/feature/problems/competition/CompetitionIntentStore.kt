package pl.dawidszczesniak.blockchain_platform.feature.problems.competition

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPooled
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig

@Serializable
internal data class StoredProblemExampleDraft(
    val input: String,
    val output: String,
    val explanation: String,
)

@Serializable
internal data class StoredProblemTestDraft(
    val inputData: String,
    val expectedOutput: String,
    val validatorCode: String,
    val validatorLanguage: String,
    val isHidden: Boolean,
    val timeoutMs: Int,
    val memoryLimitMb: Int,
)

@Serializable
internal data class PreparedCreateProblemIntent(
    val intentId: String,
    val userId: Long,
    val walletAddress: String,
    val title: String,
    val description: String,
    val constraints: String,
    val examples: List<StoredProblemExampleDraft>,
    val referenceSolutionHash: String,
    val validationNodeId: String? = null,
    val validationRunHash: String? = null,
    val validationResultHash: String? = null,
    val validationImageHash: String? = null,
    val validatedAt: String,
    val paymentAssetCode: String,
    val prizeAmountAtomic: String,
    val entryFeeAmountAtomic: String,
    val requiredParticipants: Int,
    val joinUntilDate: String,
    val submitUntilDate: String,
    val tests: List<StoredProblemTestDraft>,
    val competitionKey: String,
    val expiresAt: String,
)

@Serializable
internal data class PreparedJoinProblemIntent(
    val intentId: String,
    val userId: Long,
    val walletAddress: String,
    val problemId: Int,
    val competitionId: Long,
    val paymentAssetCode: String,
    val entryFeeAmountAtomic: String,
    val expiresAt: String,
)

internal interface CompetitionIntentStore {
    fun createCreateProblemIntent(payload: PreparedCreateProblemIntent): PreparedCreateProblemIntent
    fun getCreateProblemIntent(intentId: String): PreparedCreateProblemIntent?
    fun deleteCreateProblemIntent(intentId: String)

    fun createJoinProblemIntent(payload: PreparedJoinProblemIntent): PreparedJoinProblemIntent
    fun getJoinProblemIntent(intentId: String): PreparedJoinProblemIntent?
    fun deleteJoinProblemIntent(intentId: String)
}

internal class RedisCompetitionIntentStore(
    private val redis: JedisPooled,
    private val contractConfig: BlockchainPlatformContractConfig,
) : CompetitionIntentStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun createCreateProblemIntent(payload: PreparedCreateProblemIntent): PreparedCreateProblemIntent {
        val intentId = newIntentId()
        val expiresAt = Instant.now().plusSeconds(contractConfig.prepareIntentTtlSeconds.toLong())
        val stored = payload.copy(intentId = intentId, expiresAt = expiresAt.toString())
        redis.setex(
            createProblemKey(intentId),
            contractConfig.prepareIntentTtlSeconds.toLong(),
            json.encodeToString(PreparedCreateProblemIntent.serializer(), stored),
        )
        return stored
    }

    override fun getCreateProblemIntent(intentId: String): PreparedCreateProblemIntent? {
        val payload = redis.get(createProblemKey(intentId)) ?: return null
        return runCatching {
            json.decodeFromString(PreparedCreateProblemIntent.serializer(), payload)
        }.getOrNull()
    }

    override fun deleteCreateProblemIntent(intentId: String) {
        redis.del(createProblemKey(intentId))
    }

    override fun createJoinProblemIntent(payload: PreparedJoinProblemIntent): PreparedJoinProblemIntent {
        val intentId = newIntentId()
        val expiresAt = Instant.now().plusSeconds(contractConfig.prepareIntentTtlSeconds.toLong())
        val stored = payload.copy(intentId = intentId, expiresAt = expiresAt.toString())
        redis.setex(
            joinProblemKey(intentId),
            contractConfig.prepareIntentTtlSeconds.toLong(),
            json.encodeToString(PreparedJoinProblemIntent.serializer(), stored),
        )
        return stored
    }

    override fun getJoinProblemIntent(intentId: String): PreparedJoinProblemIntent? {
        val payload = redis.get(joinProblemKey(intentId)) ?: return null
        return runCatching {
            json.decodeFromString(PreparedJoinProblemIntent.serializer(), payload)
        }.getOrNull()
    }

    override fun deleteJoinProblemIntent(intentId: String) {
        redis.del(joinProblemKey(intentId))
    }

    private fun createProblemKey(intentId: String): String = "competition:create:intent:$intentId"

    private fun joinProblemKey(intentId: String): String = "competition:join:intent:$intentId"

    private fun newIntentId(): String = UUID.randomUUID().toString()
}
