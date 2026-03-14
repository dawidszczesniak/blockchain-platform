package pl.dawidszczesniak.blockchain_platform.feature.auth.usecase

import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthRequiredException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthSession
import pl.dawidszczesniak.blockchain_platform.feature.auth.repository.AuthRepository

internal interface GetAuthenticatedWalletUseCase {
    operator fun invoke(session: AuthSession?): String
}

internal class GetAuthenticatedWalletUseCaseImpl(
    private val repository: AuthRepository,
) : GetAuthenticatedWalletUseCase {
    override fun invoke(session: AuthSession?): String {
        val authSession = session ?: throw AuthRequiredException("Login required.")
        if (!repository.isUserPresent(authSession.userId)) {
            throw AuthRequiredException("Session user not found.")
        }
        return authSession.walletAddress
    }
}
