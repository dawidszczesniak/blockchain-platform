package pl.dawidszczesniak.blockchain_platform.feature.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable

@Serializable
internal data class AuthSession(
    val userId: Long,
    val walletAddress: String,
    val issuedAtEpochSeconds: Long,
)

internal fun ApplicationCall.requireAuthSession(): AuthSession {
    return sessions.get<AuthSession>() ?: throw AuthRequiredException("Login required.")
}
