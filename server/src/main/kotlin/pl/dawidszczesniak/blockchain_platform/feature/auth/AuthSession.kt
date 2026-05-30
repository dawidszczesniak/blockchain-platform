package pl.dawidszczesniak.blockchain_platform.feature.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.AuthSessionStore

@Serializable
internal data class AuthSessionCookie(
    val sessionId: String,
)

internal data class AuthSession(
    val userId: Long,
    val walletAddress: String,
    val issuedAtEpochSeconds: Long,
    val lastSeenAtEpochSeconds: Long,
)

internal fun ApplicationCall.requireAuthSession(
    authConfig: AuthConfig,
    sessionStore: AuthSessionStore,
): AuthSession {
    val sessionCookie = sessions.get<AuthSessionCookie>() ?: throw AuthRequiredException("Login required.")
    val sessionId = sessionCookie.sessionId.trim()
    if (sessionId.isBlank()) {
        sessions.clear<AuthSessionCookie>()
        throw AuthRequiredException("Login required.")
    }

    val nowEpochSeconds = System.currentTimeMillis() / 1000L
    val session = try {
        sessionStore.fetchActiveSession(
            sessionId = sessionId,
            nowEpochSeconds = nowEpochSeconds,
            idleTimeoutSeconds = authConfig.sessionIdleTimeoutSeconds,
        )
    } catch (error: AuthSessionExpiredException) {
        sessions.clear<AuthSessionCookie>()
        throw AuthRequiredException(error.message ?: error.reason.authMessage())
    }
    if (session == null) {
        sessions.clear<AuthSessionCookie>()
        throw AuthRequiredException("Session expired. Please login again.")
    }
    if (nowEpochSeconds - session.issuedAtEpochSeconds > authConfig.sessionTtlSeconds) {
        sessionStore.deleteSession(sessionId)
        sessions.clear<AuthSessionCookie>()
        throw AuthRequiredException(AuthSessionExpirationReason.AbsoluteTimeout.authMessage())
    }
    return session
}
