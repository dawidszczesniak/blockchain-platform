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

data class LoginState(
    val isConnectingWallet: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun connectWallet(onSuccess: () -> Unit) {
        if (state.value.isConnectingWallet) {
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
                loginUseCase()
            }.onSuccess {
                _state.update { LoginState() }
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
}

private fun extractReadableErrorMessage(error: Throwable): String {
    return error.message?.trim().orEmpty().ifBlank {
        "Login failed."
    }
}
