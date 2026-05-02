package pl.dawidszczesniak.blockchain_platform.feature.login

class WalletSessionStore {
    private var walletId: String? = null
    private var walletAddress: String? = null

    fun setCurrentWallet(walletId: String, walletAddress: String) {
        this.walletId = walletId
        this.walletAddress = walletAddress
    }

    fun currentWalletId(): String? = walletId

    fun currentWalletAddress(): String? = walletAddress

    fun clear() {
        walletId = null
        walletAddress = null
    }
}
