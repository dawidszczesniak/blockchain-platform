package pl.dawidszczesniak.blockchain_platform.feature.auth.endpoint

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
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
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthCsrfException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRateLimitException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRequiredException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthServiceUnavailableException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSessionCookie
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthValidationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthVerificationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.controller.AuthController
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.requireAuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.requireTrustedOrigin
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.AuthRateLimiter
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.AuthSessionStore

internal fun Route.authRoutes() {
    val controller by inject<AuthController>()
    val authConfig by inject<AuthConfig>()
    val rateLimiter by inject<AuthRateLimiter>()
    val sessionStore by inject<AuthSessionStore>()

    post("/auth/challenge") {
        try {
            call.requireTrustedOrigin(authConfig)
            val clientKey = call.authClientKey(authConfig)
            val request = call.receive<AuthChallengeRequestDto>()
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
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        }
    }

    post("/auth/verify") {
        try {
            call.requireTrustedOrigin(authConfig)
            val clientKey = call.authClientKey(authConfig)
            val request = call.receive<AuthVerifyRequestDto>()
            rateLimiter.checkVerify(clientKey)
            val (session, response) = withContext(Dispatchers.IO) {
                controller.verifyChallenge(request)
            }
            val sessionCookie = withContext(Dispatchers.IO) {
                sessionStore.createSession(session, authConfig.sessionTtlSeconds)
            }
            call.sessions.set(sessionCookie)
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
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        }
    }

    get("/auth/session") {
        val clientKey = call.authClientKey(authConfig)
        try {
            rateLimiter.checkSession(clientKey)
            val authSession = call.requireAuthSession(authConfig, sessionStore)
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
        try {
            call.requireTrustedOrigin(authConfig)
            val sessionCookie = call.sessions.get<AuthSessionCookie>()
            val sessionId = sessionCookie?.sessionId?.trim().orEmpty()
            if (sessionId.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    sessionStore.deleteSession(sessionId)
                }
            }
            call.sessions.clear<AuthSessionCookie>()
            call.respond(HttpStatusCode.NoContent)
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
    }

    post("/auth/logout-all") {
        try {
            call.requireTrustedOrigin(authConfig)
            val authSession = call.requireAuthSession(authConfig, sessionStore)
            withContext(Dispatchers.IO) {
                sessionStore.deleteAllSessionsForUser(authSession.userId)
            }
            call.sessions.clear<AuthSessionCookie>()
            call.respond(HttpStatusCode.NoContent)
        } catch (error: AuthRequiredException) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("message" to (error.message ?: "Login required.")),
            )
        } catch (error: AuthCsrfException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("message" to (error.message ?: "Request origin is not allowed.")),
            )
        } catch (error: AuthServiceUnavailableException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("message" to (error.message ?: "Authentication service is unavailable.")),
            )
        }
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
