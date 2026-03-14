package pl.dawidszczesniak.blockchain_platform.feature.problems.endpoint

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRequiredException
import pl.dawidszczesniak.blockchain_platform.feature.auth.requireAuthSession
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemController
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemValidationException

internal fun Route.problemRoutes() {
    val controller by inject<ProblemController>()

    get("/problems") {
        val problems = withContext(Dispatchers.IO) {
            controller.getProblemSummaries()
        }
        call.respond(problems)
    }
    get("/problems/created") {
        try {
            val session = call.requireAuthSession()
            val createdProblems = withContext(Dispatchers.IO) {
                controller.getCreatedProblems(session.userId)
            }
            call.respond(createdProblems)
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        }
    }
    get("/problems/participation") {
        try {
            val session = call.requireAuthSession()
            val participationProblems = withContext(Dispatchers.IO) {
                controller.getParticipationProblems(session.userId)
            }
            call.respond(participationProblems)
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        }
    }
    post("/problems") {
        val request = call.receive<CreateProblemRequestDto>()
        try {
            val session = call.requireAuthSession()
            val created = withContext(Dispatchers.IO) {
                controller.createProblem(session.userId, request)
            }
            call.respond(HttpStatusCode.Created, created)
        } catch (error: CreateProblemValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Invalid create problem payload.")),
            )
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        }
    }
}
