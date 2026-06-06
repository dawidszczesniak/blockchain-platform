package pl.dawidszczesniak.blockchain_platform.feature.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthChallengeRequestDto(
    val walletAddress: String,
    val chainId: Long,
)

@Serializable
data class AuthChallengeResponseDto(
    val nonce: String,
    val message: String,
    val issuedAt: String,
    val expiresAt: String,
    val walletAddress: String,
    val chainId: Long,
    val domain: String,
    val uri: String,
)

@Serializable
data class AuthVerifyRequestDto(
    val nonce: String,
    val message: String,
    val signature: String,
)

@Serializable
data class AuthVerifyResponseDto(
    val walletAddress: String,
)
