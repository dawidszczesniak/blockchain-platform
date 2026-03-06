package pl.dawidszczesniak.blockchain_platform.feature.problems.endpoint

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemController
import pl.dawidszczesniak.blockchain_platform.feature.problems.mapper.toDto

internal fun Route.problemRoutes(controller: ProblemController) {
    get("/problems") {
        val problems = withContext(Dispatchers.IO) {
            controller.getProblemSummaries()
        }
        call.respond(problems.map { it.toDto() })
    }
    get("/problems/created") {
        val createdProblems = withContext(Dispatchers.IO) {
            controller.getCreatedProblems()
        }
        call.respond(createdProblems.map { it.toDto() })
    }
    get("/problems/participation") {
        val participationProblems = withContext(Dispatchers.IO) {
            controller.getParticipationProblems()
        }
        call.respond(participationProblems.map { it.toDto() })
    }
}
