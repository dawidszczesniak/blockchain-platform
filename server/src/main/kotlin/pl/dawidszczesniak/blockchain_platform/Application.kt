package pl.dawidszczesniak.blockchain_platform

import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.serialization.kotlinx.json.json
import java.security.MessageDigest
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import pl.dawidszczesniak.blockchain_platform.db.DatabaseBootstrapper
import pl.dawidszczesniak.blockchain_platform.di.serverModules
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.endpoint.authRoutes
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.endpoint.dashboardRoutes
import pl.dawidszczesniak.blockchain_platform.feature.problems.endpoint.problemRoutes

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = LOCAL_HOST, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val envId = System.getenv("APP_ENV") ?: AppEnvironment.Local.id
    val appEnv = parseAppEnvironment(AppEnvironment.fromId(envId))
    val allowedHosts = resolveAllowedCorsHosts(appEnv)

    install(Koin) {
        modules(serverModules())
    }
    val authConfig = get<AuthConfig>()
    get<DatabaseBootstrapper>().bootstrap()

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        allowedHosts.forEach { host ->
            allowHost(host)
        }
    }
    install(Sessions) {
        cookie<AuthSession>(authConfig.sessionCookieName) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = authConfig.sessionSecureCookie
            cookie.extensions["SameSite"] = "Lax"
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 14
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
            call.respondText("OK")
        }
        authRoutes()
        problemRoutes()
        dashboardRoutes()
    }
}

private fun resolveAllowedCorsHosts(env: AppEnvironment): List<String> {
    return when (env) {
        AppEnvironment.Local -> listOf("$LOCAL_HOST:$FRONTEND_PORT")
        AppEnvironment.Staging,
        AppEnvironment.Prod,
        -> emptyList()
    }
}

private fun sha256(input: String): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
}
