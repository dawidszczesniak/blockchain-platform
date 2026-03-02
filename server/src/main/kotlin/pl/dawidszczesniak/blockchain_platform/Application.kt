package pl.dawidszczesniak.blockchain_platform

import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = LOCAL_HOST, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val envId = System.getenv("APP_ENV") ?: AppEnvironment.Local.id
    val appEnv = parseAppEnvironment(AppEnvironment.fromId(envId))
    val allowedHosts = resolveAllowedCorsHosts(appEnv)
    val problemStore = PostgresProblemStore()
    problemStore.initialize()

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowedHosts.forEach { host ->
            allowHost(host)
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
        get("/problems") {
            val problems = withContext(Dispatchers.IO) {
                problemStore.fetchProblemSummaries()
            }
            call.respond(problems.map { it.toPayload() })
        }
        get("/problems/created") {
            val problems = withContext(Dispatchers.IO) {
                problemStore.fetchCreatedProblemsForDefaultUser()
            }
            call.respond(problems.map { it.toPayload() })
        }
        get("/problems/participation") {
            val problems = withContext(Dispatchers.IO) {
                problemStore.fetchParticipationProblemsForDefaultUser()
            }
            call.respond(problems.map { it.toPayload() })
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
