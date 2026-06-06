package pl.dawidszczesniak.blockchain_platform.feature.auth.service

import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthValidationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthVerificationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.ChallengeConsumeResult
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.ChallengeInsertResult
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.StoredWalletChallenge
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.WalletChallengeStore

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
    private val blockchainConfig: BlockchainConfig,
    private val challengeStore: WalletChallengeStore,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    fun createChallenge(walletAddress: String, chainId: Long): AuthChallengeResponseDto {
        val normalizedAddress = normalizeWalletAddress(walletAddress)
        runCatching {
            blockchainConfig.validateChainIdForEnvironment(chainId)
        }.onFailure { error ->
            throw AuthValidationException(
                error.message?.ifBlank { null } ?: "Unsupported chainId for current environment."
            )
        }

        val issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)
        val expiresAt = issuedAt.plusSeconds(authConfig.challengeTtlSeconds)
        repeat(MAX_NONCE_INSERT_RETRIES) {
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

            val storedChallenge = StoredWalletChallenge(
                nonce = nonce,
                message = message,
                walletAddress = normalizedAddress,
                chainId = chainId,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
            )
            when (
                challengeStore.insertChallenge(
                    challenge = storedChallenge,
                    maxActiveChallengesPerWallet = authConfig.maxActiveChallengesPerWallet,
                    now = issuedAt,
                )
            ) {
                ChallengeInsertResult.Created -> {
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
                ChallengeInsertResult.TooManyActive -> {
                    throw AuthValidationException("Too many active login challenges for this wallet.")
                }
                ChallengeInsertResult.AlreadyExists -> {
                    // Retry with a fresh nonce if collision happens.
                }
            }
        }
        throw AuthVerificationException("Could not create wallet challenge. Please retry.")
    }

    fun consumeForVerification(nonce: String, message: String): PendingWalletChallenge {
        val normalizedNonce = nonce.trim()
        if (normalizedNonce.isBlank()) {
            throw AuthValidationException("nonce cannot be blank.")
        }

        val now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)
        return when (
            val consumed = challengeStore.consumeChallenge(
                nonce = normalizedNonce,
                expectedMessage = message,
                now = now,
            )
        ) {
            is ChallengeConsumeResult.Success -> {
                val challenge = consumed.challenge
                if (now.isAfter(challenge.expiresAt)) {
                    throw AuthVerificationException("Challenge has expired.")
                }
                PendingWalletChallenge(
                    nonce = challenge.nonce,
                    message = challenge.message,
                    walletAddress = challenge.walletAddress,
                    chainId = challenge.chainId,
                    issuedAt = challenge.issuedAt,
                    expiresAt = challenge.expiresAt,
                )
            }
            ChallengeConsumeResult.NotFound -> {
                throw AuthVerificationException("Challenge not found or already used.")
            }
            ChallengeConsumeResult.MessageMismatch -> {
                throw AuthVerificationException("Challenge message mismatch.")
            }
        }
    }

    private fun generateNonce(length: Int = 32): String {
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

private const val MAX_NONCE_INSERT_RETRIES = 3

private val WALLET_ADDRESS_REGEX = Regex("^0x[0-9a-f]{40}$")
