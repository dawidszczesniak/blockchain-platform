package pl.dawidszczesniak.blockchain_platform.feature.login.repository

import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto

interface LoginRepository {
    suspend fun createChallenge(walletAddress: String, chainId: Long): AuthChallengeResponseDto
    suspend fun verifyChallenge(nonce: String, message: String, signature: String)
    suspend fun getSession(): LoginSession?
    suspend fun getSessionWallet(): String?
    suspend fun logout()
}

data class LoginSession(
    val walletAddress: String,
)
