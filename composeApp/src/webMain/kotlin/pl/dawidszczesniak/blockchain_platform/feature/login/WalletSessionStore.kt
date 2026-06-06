@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.feature.login

class WalletSessionStore {
    private var walletId: String? = readStorageValue(WALLET_ID_STORAGE_KEY)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    private var walletAddress: String? = normalizeWalletAddress(
        readStorageValue(WALLET_ADDRESS_STORAGE_KEY)
    )

    fun setCurrentWallet(walletId: String, walletAddress: String) {
        val normalizedWalletId = walletId.trim().takeIf { it.isNotBlank() }
            ?: error("Wallet id cannot be blank.")
        val normalizedWalletAddress = normalizeWalletAddress(walletAddress)
            ?: error("Wallet address cannot be blank.")
        this.walletId = normalizedWalletId
        this.walletAddress = normalizedWalletAddress
        writeStorageValue(WALLET_ID_STORAGE_KEY, normalizedWalletId)
        writeStorageValue(WALLET_ADDRESS_STORAGE_KEY, normalizedWalletAddress)
    }

    fun currentWalletId(): String? = walletId

    fun currentWalletAddress(): String? = walletAddress

    suspend fun restoreForSession(sessionWalletAddress: String, walletProvider: WalletProvider): Boolean {
        val normalizedSessionWalletAddress = normalizeWalletAddress(sessionWalletAddress)
            ?: return false
        val connectedWallet = walletProvider.findConnectedWallet(normalizedSessionWalletAddress)
        if (connectedWallet != null) {
            setCurrentWallet(
                walletId = connectedWallet.walletId,
                walletAddress = connectedWallet.walletAddress,
            )
            return true
        }
        val currentWalletId = walletId
        if (
            !currentWalletId.isNullOrBlank() &&
            walletAddress == normalizedSessionWalletAddress
        ) {
            return true
        }
        return false
    }

    fun clear() {
        walletId = null
        walletAddress = null
        removeStorageValue(WALLET_ID_STORAGE_KEY)
        removeStorageValue(WALLET_ADDRESS_STORAGE_KEY)
    }
}

private const val WALLET_ID_STORAGE_KEY = "bp.currentWalletId"
private const val WALLET_ADDRESS_STORAGE_KEY = "bp.currentWalletAddress"

private fun normalizeWalletAddress(value: String?): String? {
    return value?.trim()?.takeIf { it.isNotBlank() }
}

@JsFun(
    """
(key) => {
  try {
    const storage = globalThis.localStorage;
    if (!storage) {
      return "";
    }
    const value = storage.getItem(String(key));
    return value == null ? "" : String(value);
  } catch (_) {
    return "";
  }
}
"""
)
private external fun readLocalStorageValue(key: String): String

@JsFun(
    """
(key, value) => {
  try {
    const storage = globalThis.localStorage;
    if (storage) {
      storage.setItem(String(key), String(value));
    }
  } catch (_) {
  }
}
"""
)
private external fun writeLocalStorageValue(key: String, value: String)

@JsFun(
    """
(key) => {
  try {
    const storage = globalThis.localStorage;
    if (storage) {
      storage.removeItem(String(key));
    }
  } catch (_) {
  }
}
"""
)
private external fun removeLocalStorageValue(key: String)

private fun readStorageValue(key: String): String? {
    return readLocalStorageValue(key).trim().ifBlank { null }
}

private fun writeStorageValue(key: String, value: String) {
    writeLocalStorageValue(key, value)
}

private fun removeStorageValue(key: String) {
    removeLocalStorageValue(key)
}
