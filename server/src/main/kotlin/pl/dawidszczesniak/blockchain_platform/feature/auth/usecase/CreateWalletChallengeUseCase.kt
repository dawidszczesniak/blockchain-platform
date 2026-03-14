package pl.dawidszczesniak.blockchain_platform.feature.auth.usecase

import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthChallengeResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.WalletChallengeService

internal interface CreateWalletChallengeUseCase {
    operator fun invoke(request: AuthChallengeRequestDto): AuthChallengeResponseDto
}

internal class CreateWalletChallengeUseCaseImpl(
    private val challengeService: WalletChallengeService,
) : CreateWalletChallengeUseCase {
    override fun invoke(request: AuthChallengeRequestDto): AuthChallengeResponseDto {
        return challengeService.createChallenge(
            walletAddress = request.walletAddress,
            chainId = request.chainId,
        )
    }
}
