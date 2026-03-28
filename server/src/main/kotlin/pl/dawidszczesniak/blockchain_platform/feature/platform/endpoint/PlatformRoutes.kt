package pl.dawidszczesniak.blockchain_platform.feature.platform.endpoint

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import pl.dawidszczesniak.blockchain_platform.feature.platform.controller.PlatformController

internal fun Route.platformRoutes() {
    val controller by inject<PlatformController>()

    get("/platform/meta") {
        val payload = withContext(Dispatchers.IO) {
            controller.getPlatformConfig()
        }
        call.respond(payload)
    }
}
