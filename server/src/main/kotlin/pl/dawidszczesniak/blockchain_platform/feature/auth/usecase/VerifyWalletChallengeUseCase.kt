package pl.dawidszczesniak.blockchain_platform.feature.auth.usecase

import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthVerificationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.dto.AuthVerifyRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.auth.repository.AuthRepository
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.EthereumSignatureVerifier
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.WalletChallengeService

internal data class VerifiedWalletLogin(
    val userId: Long,
    val walletAddress: String,
)

internal interface VerifyWalletChallengeUseCase {
    operator fun invoke(request: AuthVerifyRequestDto): VerifiedWalletLogin
}

internal class VerifyWalletChallengeUseCaseImpl(
    private val challengeService: WalletChallengeService,
    private val signatureVerifier: EthereumSignatureVerifier,
    private val repository: AuthRepository,
) : VerifyWalletChallengeUseCase {
    override fun invoke(request: AuthVerifyRequestDto): VerifiedWalletLogin {
        val challenge = challengeService.consumeForVerification(
            nonce = request.nonce,
            message = request.message,
        )
        val recoveredAddress = signatureVerifier.recoverAddressFromPersonalSign(
            message = request.message,
            signatureHex = request.signature,
        )
        if (recoveredAddress != challenge.walletAddress) {
            throw AuthVerificationException("Signed address does not match challenge wallet.")
        }

        val userId = repository.loginByWallet(recoveredAddress)
        return VerifiedWalletLogin(
            userId = userId,
            walletAddress = recoveredAddress,
        )
    }
}
