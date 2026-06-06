package pl.dawidszczesniak.blockchain_platform.feature.login.repository

import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto
import pl.dawidszczesniak.blockchain_platform.network.SessionExpirationReason

interface LoginRepository {
    suspend fun createChallenge(walletAddress: String, chainId: Long): AuthChallengeResponseDto
    suspend fun verifyChallenge(nonce: String, message: String, signature: String)
    suspend fun getSession(): LoginSession?
    suspend fun getSessionWallet(): String?
    suspend fun logout()
}

class SessionExpiredException(
    val reason: SessionExpirationReason,
    message: String,
) : IllegalStateException(message)

data class LoginSession(
    val walletAddress: String,
)
