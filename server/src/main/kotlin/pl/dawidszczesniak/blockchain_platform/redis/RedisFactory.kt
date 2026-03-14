package pl.dawidszczesniak.blockchain_platform.redis

import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPooled

internal object RedisFactory {
    fun connect(config: RedisConfig): JedisPooled {
        val clientConfigBuilder = DefaultJedisClientConfig.builder()
            .database(config.database)
            .ssl(config.ssl)

        config.username?.let { username ->
            clientConfigBuilder.user(username)
        }
        config.password?.let { password ->
            clientConfigBuilder.password(password)
        }

        val redisClient = JedisPooled(
            HostAndPort(config.host, config.port),
            clientConfigBuilder.build(),
        )
        runCatching { redisClient.ping() }.getOrElse { error ->
            redisClient.close()
            throw IllegalStateException("Cannot connect to Redis at ${config.host}:${config.port}.", error)
        }
        return redisClient
    }
}
