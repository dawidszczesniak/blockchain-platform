package pl.dawidszczesniak.blockchain_platform.feature.dashboard.endpoint

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.controller.DashboardController
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.mapper.toDto

internal fun Route.dashboardRoutes(controller: DashboardController) {
    get("/dashboard/metrics") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 30
        val metrics = withContext(Dispatchers.IO) {
            controller.getMetricsHistory(limit = limit)
        }
        call.respond(metrics.map { it.toDto() })
    }
    get("/dashboard/updates") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 3
        val updates = withContext(Dispatchers.IO) {
            controller.getLatestUpdates(limit = limit)
        }
        call.respond(updates.map { it.toDto() })
    }
}
