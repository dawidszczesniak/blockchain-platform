package pl.dawidszczesniak.blockchain_platform.feature.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable

@Serializable
internal data class AuthSession(
    val userId: Long,
    val walletAddress: String,
    val issuedAtEpochSeconds: Long,
)

internal fun ApplicationCall.requireAuthSession(authConfig: AuthConfig): AuthSession {
    val session = sessions.get<AuthSession>() ?: throw AuthRequiredException("Login required.")
    val nowEpochSeconds = System.currentTimeMillis() / 1000L
    if (nowEpochSeconds - session.issuedAtEpochSeconds > authConfig.sessionTtlSeconds) {
        sessions.clear<AuthSession>()
        throw AuthRequiredException("Session expired. Please login again.")
    }
    return session
}
