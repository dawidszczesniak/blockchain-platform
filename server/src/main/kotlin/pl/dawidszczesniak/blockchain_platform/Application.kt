package pl.dawidszczesniak.blockchain_platform

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary

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

    routing {
        get("/") {
            call.respondText("Ktor: OK")
        }
        get("/health") {
            call.respondText("OK")
        }
        get("/problems") {
            val problems = withContext(Dispatchers.IO) {
                problemStore.fetchProblemSummaries()
            }
            call.respondText(problemSummariesAsJson(problems), ContentType.Application.Json)
        }
        get("/problems/created") {
            val problems = withContext(Dispatchers.IO) {
                problemStore.fetchCreatedProblemsForDefaultUser()
            }
            call.respondText(createdProblemsAsJson(problems), ContentType.Application.Json)
        }
        get("/problems/participation") {
            val problems = withContext(Dispatchers.IO) {
                problemStore.fetchParticipationProblemsForDefaultUser()
            }
            call.respondText(participationProblemsAsJson(problems), ContentType.Application.Json)
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

private fun problemSummariesAsJson(problems: List<ProblemSummary>): String {
    return buildJsonArray {
        problems.forEach { problem ->
            add(
                buildJsonObject {
                    put("id", problem.id)
                    put("title", problem.title)
                    put("description", problem.description)
                    put("prizeAmount", problem.prizeAmount)
                    put("entryFeeAmount", problem.entryFeeAmount)
                    put("requiredParticipants", problem.requiredParticipants)
                    put("registeredParticipants", problem.registeredParticipants)
                    put("daysToStart", problem.daysToStart)
                    put("daysToJoinEnd", problem.daysToJoinEnd)
                    put("joinUntilLabel", problem.joinUntilLabel)
                    put("submitUntilLabel", problem.submitUntilLabel)
                }
            )
        }
    }.toString()
}

private fun createdProblemsAsJson(problems: List<CreatedProblem>): String {
    return buildJsonArray {
        problems.forEach { problem ->
            add(
                buildJsonObject {
                    put("id", problem.id)
                    put("title", problem.title)
                    put("status", problem.status.name)
                    put("requiredParticipants", problem.requiredParticipants)
                    put("registeredParticipants", problem.registeredParticipants)
                    put("submissions", problem.submissions)
                    putNullableString("startedOn", problem.startedOn)
                    putNullableString("finishedOn", problem.finishedOn)
                    putNullableString("registrationEnds", problem.registrationEnds)
                    putNullableString("timeElapsed", problem.timeElapsed)
                    putNullableString("winner", problem.winner)
                }
            )
        }
    }.toString()
}

private fun participationProblemsAsJson(problems: List<ParticipationProblem>): String {
    return buildJsonArray {
        problems.forEach { problem ->
            add(
                buildJsonObject {
                    put("id", problem.id)
                    put("title", problem.title)
                    put("status", problem.status.name)
                    put("timeLeftLabel", problem.timeLeftLabel)
                    put("participants", problem.participants)
                    put("attemptsCount", problem.attemptsCount)
                }
            )
        }
    }.toString()
}

private fun JsonObjectBuilder.putNullableString(name: String, value: String?) {
    if (value == null) {
        put(name, JsonNull)
    } else {
        put(name, value)
    }
}
