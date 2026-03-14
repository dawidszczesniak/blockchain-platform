package pl.dawidszczesniak.blockchain_platform.feature.login.repository

import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.login.datasource.LoginRemoteDataSource

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
}
