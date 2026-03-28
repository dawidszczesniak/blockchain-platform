package pl.dawidszczesniak.blockchain_platform

import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.serialization.kotlinx.json.json
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import redis.clients.jedis.JedisPooled
import pl.dawidszczesniak.blockchain_platform.db.DatabaseBootstrapper
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.di.serverModules
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSessionCookie
import pl.dawidszczesniak.blockchain_platform.feature.auth.endpoint.authRoutes
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.Eip1271SignatureVerifier
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.endpoint.dashboardRoutes
import pl.dawidszczesniak.blockchain_platform.feature.platform.endpoint.platformRoutes
import pl.dawidszczesniak.blockchain_platform.feature.problems.anchor.BlockchainAnchorClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.endpoint.problemRoutes

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = LOCAL_HOST, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val systemEnv = System.getenv()
    val envId = systemEnv["APP_ENV"] ?: AppEnvironment.Local.id
    val appEnv = parseAppEnvironment(AppEnvironment.fromId(envId))
    val allowedHosts = resolveAllowedCorsHosts(appEnv, systemEnv)

    install(Koin) {
        modules(serverModules())
    }
    val authConfig = get<AuthConfig>()
    validateSecurityConfiguration(appEnv, authConfig, allowedHosts)
    get<DatabaseBootstrapper>().bootstrap()
    val transactionRunner = get<DbTransactionRunner>()
    val redisClient = get<JedisPooled>()
    val eip1271Verifier = get<Eip1271SignatureVerifier>()
    val blockchainAnchorClient = get<BlockchainAnchorClient>()
    monitor.subscribe(ApplicationStopped) {
        redisClient.close()
        eip1271Verifier.close()
        blockchainAnchorClient.close()
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        allowedHosts.forEach { host ->
            allowHost(host.host, schemes = host.schemes)
        }
    }
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        if (appEnv != AppEnvironment.Local) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }
    install(Sessions) {
        cookie<AuthSessionCookie>(authConfig.sessionCookieName) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = authConfig.sessionSecureCookie
            cookie.extensions["SameSite"] = authConfig.sessionSameSite.cookieValue
            cookie.maxAgeInSeconds = authConfig.sessionTtlSeconds
            transform(
                SessionTransportTransformerMessageAuthentication(
                    key = sha256(authConfig.sessionSignKey),
                )
            )
        }
    }
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Ktor OK")
        }
        get("/health") {
            val postgresHealthy = runCatching {
                transactionRunner.inTransaction { 1 }
            }.isSuccess
            val redisHealthy = runCatching {
                redisClient.ping().equals("PONG", ignoreCase = true)
            }.getOrDefault(false)
            val allHealthy = postgresHealthy && redisHealthy

            val payload = HealthResponseDto(
                status = if (allHealthy) "ok" else "degraded",
                timestamp = Instant.now().toString(),
                dependencies = HealthDependenciesDto(
                    postgres = if (postgresHealthy) "up" else "down",
                    redis = if (redisHealthy) "up" else "down",
                ),
            )
            call.respond(
                if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                payload,
            )
        }
        authRoutes()
        problemRoutes()
        dashboardRoutes()
        platformRoutes()
    }
}

private data class CorsHostSpec(
    val host: String,
    val schemes: List<String>,
)

private fun resolveAllowedCorsHosts(
    env: AppEnvironment,
    systemEnv: Map<String, String>,
): List<CorsHostSpec> {
    val configured = systemEnv["CORS_ALLOWED_HOSTS"]
        ?.split(',')
        ?.mapNotNull { parseCorsHostSpec(it) }
        .orEmpty()
    if (configured.isNotEmpty()) {
        return configured
    }
    return when (env) {
        AppEnvironment.Local -> listOf(CorsHostSpec(host = "$LOCAL_HOST:$FRONTEND_PORT", schemes = listOf("http")))
        AppEnvironment.Staging,
        AppEnvironment.Prod,
        -> emptyList()
    }
}

private fun parseCorsHostSpec(raw: String): CorsHostSpec? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return null
    }
    if (!trimmed.contains("://")) {
        return CorsHostSpec(host = trimmed, schemes = listOf("http", "https"))
    }
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val host = uri.host?.trim().orEmpty()
    val scheme = uri.scheme?.trim()?.lowercase().orEmpty()
    if (host.isBlank() || scheme.isBlank()) {
        return null
    }
    val withPort = if (uri.port > 0) "$host:${uri.port}" else host
    return CorsHostSpec(host = withPort, schemes = listOf(scheme))
}

private fun validateSecurityConfiguration(
    env: AppEnvironment,
    authConfig: AuthConfig,
    corsHosts: List<CorsHostSpec>,
) {
    if (env == AppEnvironment.Local) return
    if (corsHosts.isEmpty()) {
        error("CORS_ALLOWED_HOSTS must be configured in staging/prod.")
    }
    val hasInsecureScheme = corsHosts.any { host ->
        host.schemes.any { scheme -> scheme != "https" }
    }
    if (hasInsecureScheme) {
        error("CORS_ALLOWED_HOSTS must use only https:// schemes in staging/prod.")
    }
    if (!authConfig.sessionSecureCookie) {
        error("Session cookie must be secure in staging/prod.")
    }
}

private fun sha256(input: String): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
}

@Serializable
private data class HealthResponseDto(
    val status: String,
    val timestamp: String,
    val dependencies: HealthDependenciesDto,
)

@Serializable
private data class HealthDependenciesDto(
    val postgres: String,
    val redis: String,
)
