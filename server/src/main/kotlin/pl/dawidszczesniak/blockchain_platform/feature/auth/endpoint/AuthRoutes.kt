package pl.dawidszczesniak.blockchain_platform.feature.auth.endpoint

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.set
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRateLimitException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRequiredException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthServiceUnavailableException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthValidationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthVerificationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.controller.AuthController
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.requireAuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.AuthRateLimiter

internal fun Route.authRoutes() {
    val controller by inject<AuthController>()
    val authConfig by inject<AuthConfig>()
    val rateLimiter by inject<AuthRateLimiter>()

    post("/auth/challenge") {
        val clientKey = call.authClientKey(authConfig)
        val request = call.receive<AuthChallengeRequestDto>()
        try {
            rateLimiter.checkChallenge(clientKey)
            val challenge = withContext(Dispatchers.IO) {
                controller.createChallenge(request)
            }
            call.respond(challenge)
        } catch (error: AuthValidationException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("message" to (error.message ?: "Invalid challenge request.")),
            )
        } catch (error: AuthRateLimitException) {
            call.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("message" to (error.message ?: "Too many requests.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }

    post("/auth/verify") {
        val clientKey = call.authClientKey(authConfig)
        val request = call.receive<AuthVerifyRequestDto>()
        try {
            rateLimiter.checkVerify(clientKey)
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
        } catch (error: AuthRateLimitException) {
            call.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("message" to (error.message ?: "Too many requests.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }

    get("/auth/session") {
        val clientKey = call.authClientKey(authConfig)
        try {
            rateLimiter.checkSession(clientKey)
            val authSession = call.requireAuthSession(authConfig)
            val sessionResponse = withContext(Dispatchers.IO) {
                controller.getSessionWallet(authSession)
            }
            call.respond(sessionResponse)
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthRateLimitException) {
            call.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("message" to (error.message ?: "Too many requests.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }

    post("/auth/logout") {
        call.sessions.clear<AuthSession>()
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun io.ktor.server.application.ApplicationCall.authClientKey(authConfig: AuthConfig): String {
    val forwardedFor = request.header("X-Forwarded-For")
        ?.split(',')
        ?.firstOrNull()
        ?.trim()
        .orEmpty()
    val directHost = request.local.remoteHost.orEmpty().trim()
    return when {
        authConfig.trustProxyHeaders && forwardedFor.isNotBlank() -> forwardedFor
        directHost.isNotBlank() -> directHost
        else -> "unknown-client"
    }
}
