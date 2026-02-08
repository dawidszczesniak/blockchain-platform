package pl.dawidszczesniak.blockchain_platform

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.HttpMethod

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val envId = System.getenv("APP_ENV") ?: AppEnvironment.Local.id
    val appEnv = parseAppEnvironment(AppEnvironment.fromId(envId))
    val allowedHosts = resolveAllowedCorsHosts(appEnv)

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowedHosts.forEach { host ->
            allowHost(host)
        }
    }

    routing {
        get("/") {
            call.respondText("Ktor: OK")
        }
        get("/health") {
            call.respondText("OK")
        }
    }
}

private fun resolveAllowedCorsHosts(env: AppEnvironment): List<String> {
    val configured = System.getenv("CORS_ALLOWED_HOSTS")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    if (configured.isNotEmpty()) {
        return configured
    }

    return when (env) {
        AppEnvironment.Local -> listOf(
            "localhost:8080",
            "127.0.0.1:8080",
        )
        AppEnvironment.Staging,
        AppEnvironment.Prod,
        -> emptyList()
    }
}
