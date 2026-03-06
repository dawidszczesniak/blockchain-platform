package pl.dawidszczesniak.blockchain_platform.feature.login

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginState(
    val isConnectingWallet: Boolean = false,
)

sealed interface LoginIntent {
    data object ConnectWalletClicked : LoginIntent
    data object Reset : LoginIntent
}

class LoginViewModel {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            LoginIntent.ConnectWalletClicked -> {
                _state.update { current ->
                    current.copy(isConnectingWallet = true)
                }
            }

            LoginIntent.Reset -> {
                _state.update { LoginState() }
            }
        }
    }
}
