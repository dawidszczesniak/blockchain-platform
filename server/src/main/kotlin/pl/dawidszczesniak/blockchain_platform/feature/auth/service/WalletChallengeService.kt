package pl.dawidszczesniak.blockchain_platform.feature.auth.service

import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthValidationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthVerificationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto

internal data class PendingWalletChallenge(
    val nonce: String,
    val message: String,
    val walletAddress: String,
    val chainId: Long,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

internal class WalletChallengeService(
    private val authConfig: AuthConfig,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    private val challengesByNonce = ConcurrentHashMap<String, PendingWalletChallenge>()

    fun createChallenge(walletAddress: String, chainId: Long): AuthChallengeResponseDto {
        val normalizedAddress = normalizeWalletAddress(walletAddress)
        if (chainId <= 0L) {
            throw AuthValidationException("chainId must be greater than 0.")
        }
        cleanupExpiredChallenges()

        val issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)
        val expiresAt = issuedAt.plusSeconds(authConfig.challengeTtlSeconds)
        val nonce = generateNonce()
        val message = buildSiweMessage(
            domain = authConfig.domain,
            walletAddress = normalizedAddress,
            uri = authConfig.uri,
            chainId = chainId,
            nonce = nonce,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )

        challengesByNonce[nonce] = PendingWalletChallenge(
            nonce = nonce,
            message = message,
            walletAddress = normalizedAddress,
            chainId = chainId,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )

        return AuthChallengeResponseDto(
            nonce = nonce,
            message = message,
            issuedAt = issuedAt.toString(),
            expiresAt = expiresAt.toString(),
            walletAddress = normalizedAddress,
            chainId = chainId,
            domain = authConfig.domain,
            uri = authConfig.uri,
        )
    }

    fun consumeForVerification(nonce: String, message: String): PendingWalletChallenge {
        cleanupExpiredChallenges()
        val normalizedNonce = nonce.trim()
        val challenge = challengesByNonce.remove(normalizedNonce)
            ?: throw AuthVerificationException("Challenge not found or already used.")

        if (challenge.message != message) {
            throw AuthVerificationException("Challenge message mismatch.")
        }

        val now = Instant.now(clock)
        if (now.isAfter(challenge.expiresAt)) {
            throw AuthVerificationException("Challenge has expired.")
        }

        return challenge
    }

    private fun cleanupExpiredChallenges() {
        val now = Instant.now(clock)
        challengesByNonce.entries.removeIf { now.isAfter(it.value.expiresAt) }
    }

    private fun generateNonce(length: Int = 18): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val builder = StringBuilder(length)
        repeat(length) {
            builder.append(chars[random.nextInt(chars.length)])
        }
        return builder.toString()
    }

    private fun normalizeWalletAddress(walletAddress: String): String {
        val normalized = walletAddress.trim().lowercase()
        if (!WALLET_ADDRESS_REGEX.matches(normalized)) {
            throw AuthValidationException("walletAddress must be a valid 0x-prefixed Ethereum address.")
        }
        return normalized
    }

    private fun buildSiweMessage(
        domain: String,
        walletAddress: String,
        uri: String,
        chainId: Long,
        nonce: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String {
        return buildString {
            appendLine("$domain wants you to sign in with your Ethereum account:")
            appendLine(walletAddress)
            appendLine()
            appendLine("Sign in to Blockchain Platform.")
            appendLine()
            appendLine("URI: $uri")
            appendLine("Version: 1")
            appendLine("Chain ID: $chainId")
            appendLine("Nonce: $nonce")
            appendLine("Issued At: $issuedAt")
            append("Expiration Time: $expiresAt")
        }
    }
}

private val WALLET_ADDRESS_REGEX = Regex("^0x[0-9a-f]{40}$")
