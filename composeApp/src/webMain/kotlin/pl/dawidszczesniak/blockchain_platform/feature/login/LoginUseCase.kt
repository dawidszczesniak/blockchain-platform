package pl.dawidszczesniak.blockchain_platform.feature.login

import pl.dawidszczesniak.blockchain_platform.feature.login.repository.LoginRepository

interface LoginUseCase {
    suspend fun fetchWallets(): List<LoginWalletOption>
    suspend fun login(walletId: String)
}

data class LoginWalletOption(
    val id: String,
    val name: String,
    val rdns: String?,
    val iconUri: String?,
)

class LoginUseCaseImpl(
    private val repository: LoginRepository,
    private val walletProvider: WalletProvider,
) : LoginUseCase {
    override suspend fun fetchWallets(): List<LoginWalletOption> {
        return walletProvider.discoverWallets().map { wallet ->
            LoginWalletOption(
                id = wallet.id,
                name = wallet.name,
                rdns = wallet.rdns,
                iconUri = wallet.iconUri,
            )
        }
    }

    override suspend fun login(walletId: String) {
        val wallet = walletProvider.requestLoginContext(walletId)
        val challenge = repository.createChallenge(
            walletAddress = wallet.walletAddress,
            chainId = wallet.chainId,
        )
        val signature = walletProvider.signMessage(
            walletId = walletId,
            walletAddress = wallet.walletAddress,
            message = challenge.message,
        )
        repository.verifyChallenge(
            nonce = challenge.nonce,
            message = challenge.message,
            signature = signature,
        )
    }
}
