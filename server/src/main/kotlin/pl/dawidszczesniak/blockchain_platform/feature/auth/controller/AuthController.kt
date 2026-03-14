package pl.dawidszczesniak.blockchain_platform.feature.auth.controller

import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.CreateWalletChallengeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.GetAuthenticatedWalletUseCase
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.VerifyWalletChallengeUseCase

internal class AuthController(
    private val createWalletChallengeUseCase: CreateWalletChallengeUseCase,
    private val verifyWalletChallengeUseCase: VerifyWalletChallengeUseCase,
    private val getAuthenticatedWalletUseCase: GetAuthenticatedWalletUseCase,
) {
    fun createChallenge(request: AuthChallengeRequestDto): AuthChallengeResponseDto {
        return createWalletChallengeUseCase(request)
    }

    fun verifyChallenge(request: AuthVerifyRequestDto): Pair<AuthSession, AuthVerifyResponseDto> {
        val verified = verifyWalletChallengeUseCase(request)
        val session = AuthSession(
            userId = verified.userId,
            walletAddress = verified.walletAddress,
            issuedAtEpochSeconds = System.currentTimeMillis() / 1000L,
        )
        val response = AuthVerifyResponseDto(walletAddress = verified.walletAddress)
        return session to response
    }

    fun getSessionWallet(session: AuthSession): AuthVerifyResponseDto {
        val wallet = getAuthenticatedWalletUseCase(session)
        return AuthVerifyResponseDto(walletAddress = wallet)
    }
}
