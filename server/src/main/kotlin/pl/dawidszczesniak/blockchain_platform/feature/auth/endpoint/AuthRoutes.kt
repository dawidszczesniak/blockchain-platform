package pl.dawidszczesniak.blockchain_platform.feature.auth.endpoint

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.set
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRequiredException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthValidationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthVerificationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.controller.AuthController
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyRequestDto

internal fun Route.authRoutes() {
    val controller by inject<AuthController>()

    post("/auth/challenge") {
        val request = call.receive<AuthChallengeRequestDto>()
        try {
            val challenge = withContext(Dispatchers.IO) {
                controller.createChallenge(request)
            }
            call.respond(challenge)
        } catch (error: AuthValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Invalid challenge request.")),
            )
        }
    }

    post("/auth/verify") {
        val request = call.receive<AuthVerifyRequestDto>()
        try {
            val (session, response) = withContext(Dispatchers.IO) {
                controller.verifyChallenge(request)
            }
            call.sessions.set(session)
            call.respond(response)
        } catch (error: AuthValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Invalid verify request.")),
            )
        } catch (error: AuthVerificationException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Signature verification failed.")),
            )
        }
    }

    get("/auth/session") {
        try {
            val sessionResponse = withContext(Dispatchers.IO) {
                controller.getSessionWallet(call.sessions.get<AuthSession>())
            }
            call.respond(sessionResponse)
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        }
    }

    post("/auth/logout") {
        call.sessions.clear<AuthSession>()
        call.respond(HttpStatusCode.NoContent)
    }
}
