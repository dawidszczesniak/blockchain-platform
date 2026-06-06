package pl.dawidszczesniak.blockchain_platform.feature.dashboard.endpoint

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.controller.DashboardController

internal fun Route.dashboardRoutes() {
    val controller by inject<DashboardController>()

    get("/dashboard/metrics") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 30
        val metrics = withContext(Dispatchers.IO) {
            controller.getMetricsHistory(limit = limit)
        }
        call.respond(metrics)
    }
    get("/dashboard/updates") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 3
        val updates = withContext(Dispatchers.IO) {
            controller.getLatestUpdates(limit = limit)
        }
        call.respond(updates)
    }
}
