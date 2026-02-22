package pl.dawidszczesniak.blockchain_platform

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.HttpMethod

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = LOCAL_HOST, module = Application::module)
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
    return when (env) {
        AppEnvironment.Local -> listOf("$LOCAL_HOST:$FRONTEND_PORT")
        AppEnvironment.Staging,
        AppEnvironment.Prod,
        -> emptyList()
    }
}
