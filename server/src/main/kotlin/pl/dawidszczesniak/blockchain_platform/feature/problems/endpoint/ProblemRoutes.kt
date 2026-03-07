package pl.dawidszczesniak.blockchain_platform.feature.problems.endpoint

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemController

internal fun Route.problemRoutes() {
    val controller by inject<ProblemController>()

    get("/problems") {
        val problems = withContext(Dispatchers.IO) {
            controller.getProblemSummaries()
        }
        call.respond(problems)
    }
    get("/problems/created") {
        val createdProblems = withContext(Dispatchers.IO) {
            controller.getCreatedProblems()
        }
        call.respond(createdProblems)
    }
    get("/problems/participation") {
        val participationProblems = withContext(Dispatchers.IO) {
            controller.getParticipationProblems()
        }
        call.respond(participationProblems)
    }
}
