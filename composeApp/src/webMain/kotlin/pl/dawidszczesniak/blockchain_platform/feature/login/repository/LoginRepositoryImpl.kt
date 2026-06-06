package pl.dawidszczesniak.blockchain_platform.feature.login.repository

import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.login.datasource.LoginRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.network.HttpStatusException
import pl.dawidszczesniak.blockchain_platform.network.SessionExpirationReason
import pl.dawidszczesniak.blockchain_platform.network.isSessionExpiredResponse
import pl.dawidszczesniak.blockchain_platform.network.sessionExpirationReason

class LoginRepositoryImpl(
    private val remoteDataSource: LoginRemoteDataSource,
) : LoginRepository {
    override suspend fun createChallenge(walletAddress: String, chainId: Long): AuthChallengeResponseDto {
        return remoteDataSource.requestChallenge(
            AuthChallengeRequestDto(
                walletAddress = walletAddress,
                chainId = chainId,
            )
        )
    }

    override suspend fun verifyChallenge(nonce: String, message: String, signature: String) {
        remoteDataSource.verifyChallenge(
            AuthVerifyRequestDto(
                nonce = nonce,
                message = message,
                signature = signature,
            )
        )
    }

    override suspend fun getSession(): LoginSession? {
        val session = try {
            remoteDataSource.getSession().let { session ->
                LoginSession(
                    walletAddress = session.walletAddress.trim(),
                )
            }
        } catch (error: HttpStatusException) {
            if (error.statusCode == 401) {
                if (error.isSessionExpiredResponse()) {
                    throw SessionExpiredException(
                        reason = error.sessionExpirationReason()
                            ?: SessionExpirationReason.Unknown,
                        message = error.message ?: "Session expired. Please login again.",
                    )
                }
                null
            } else {
                throw error
            }
        }
        return session?.takeIf { it.walletAddress.isNotBlank() }
    }

    override suspend fun getSessionWallet(): String? {
        return getSession()?.walletAddress
    }

    override suspend fun logout() {
        runCatching {
            remoteDataSource.logout()
        }
    }
}
