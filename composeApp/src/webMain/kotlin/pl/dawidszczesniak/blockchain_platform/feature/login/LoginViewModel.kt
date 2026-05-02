package pl.dawidszczesniak.blockchain_platform.feature.login

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.dawidszczesniak.blockchain_platform.feature.platform.usecase.GetPlatformConfigUseCase

data class LoginState(
    val isLoadingWallets: Boolean = false,
    val isLoadingNetwork: Boolean = false,
    val wallets: List<LoginWalletOption> = emptyList(),
    val isConnectingWallet: Boolean = false,
    val requiredNetworkLabel: String? = null,
    val errorMessage: String? = null,
)

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val getPlatformConfigUseCase: GetPlatformConfigUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    init {
        refreshNetwork()
        refreshWallets()
    }

    fun refreshWallets() {
        if (state.value.isLoadingWallets || state.value.isConnectingWallet) {
            return
        }
        _state.update { current ->
            current.copy(
                isLoadingWallets = true,
                errorMessage = null,
            )
        }
        scope.launch {
            runCatching {
                loginUseCase.fetchWallets()
            }.onSuccess { wallets ->
                _state.update { current ->
                    current.copy(
                        isLoadingWallets = false,
                        wallets = wallets,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        isLoadingWallets = false,
                        errorMessage = extractReadableErrorMessage(error),
                    )
                }
            }
        }
    }

    fun connectWallet(walletId: String, onSuccess: () -> Unit) {
        if (state.value.wallets.none { it.id == walletId }) {
            _state.update { current ->
                current.copy(errorMessage = "Selected wallet is not available. Refresh wallets.")
            }
            return
        }
        if (state.value.isConnectingWallet || state.value.isLoadingWallets) {
            return
        }
        _state.update { current ->
            current.copy(
                isConnectingWallet = true,
                errorMessage = null,
            )
        }
        scope.launch {
            runCatching {
                loginUseCase.login(walletId)
            }.onSuccess {
                _state.update { current ->
                    current.copy(
                        isConnectingWallet = false,
                        errorMessage = null,
                    )
                }
                onSuccess()
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        isConnectingWallet = false,
                        errorMessage = extractReadableErrorMessage(error),
                    )
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun refreshNetwork() {
        _state.update { current ->
            current.copy(isLoadingNetwork = true)
        }
        scope.launch {
            runCatching { getPlatformConfigUseCase() }
                .onSuccess { platform ->
                    val label = platform.walletNetwork?.let { network ->
                        "${network.networkName} (${network.chainId})"
                    } ?: platform.chainId?.let { chainId ->
                        "${platform.networkName} ($chainId)"
                    } ?: platform.networkName
                    _state.update { current ->
                        current.copy(
                            isLoadingNetwork = false,
                            requiredNetworkLabel = label.ifBlank { null },
                        )
                    }
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            isLoadingNetwork = false,
                            requiredNetworkLabel = null,
                        )
                    }
                }
        }
    }
}

private fun extractReadableErrorMessage(error: Throwable): String {
    val raw = error.message?.trim().orEmpty()
    if (raw.isEmpty()) {
        return "Login failed."
    }
    if (raw == "[object Object]") {
        return ""
    }
    val lowered = raw.lowercase()
    if ("user rejected" in lowered || "user denied" in lowered || "\"code\":4001" in lowered || "eip-1193 user rejected" in lowered) {
        return ""
    }
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start >= 0 && end > start) {
        val jsonPart = raw.substring(start, end + 1)
        val parsedMessage = runCatching {
            Json.parseToJsonElement(jsonPart)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
        }.getOrNull()
        if (!parsedMessage.isNullOrBlank()) {
            val parsedLowered = parsedMessage.lowercase()
            if ("user rejected" in parsedLowered || "user denied" in parsedLowered) {
                return ""
            }
            return parsedMessage
        }
    }
    return raw
}
