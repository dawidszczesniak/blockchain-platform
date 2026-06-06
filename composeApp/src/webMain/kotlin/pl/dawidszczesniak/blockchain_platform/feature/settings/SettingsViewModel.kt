package pl.dawidszczesniak.blockchain_platform.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dawidszczesniak.blockchain_platform.feature.login.repository.LoginRepository
import pl.dawidszczesniak.blockchain_platform.feature.platform.usecase.GetPlatformConfigUseCase

data class SettingsState(
    val walletAddress: String? = null,
    val selectedLanguage: UiLanguage = UiLanguage.Polish,
    val availableLanguages: List<UiLanguage> = UiLanguage.entries,
    val isWalletLoading: Boolean = true,
    val isPlatformLoading: Boolean = true,
    val networkLabel: String? = null,
    val proxyAddress: String? = null,
)

sealed interface SettingsIntent {
    data object Refresh : SettingsIntent
    data class SelectLanguage(val language: UiLanguage) : SettingsIntent
}

class SettingsViewModel(
    private val loginRepository: LoginRepository,
    private val appLanguageStore: AppLanguageStore,
    private val getPlatformConfigUseCase: GetPlatformConfigUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        SettingsState(
            selectedLanguage = appLanguageStore.language.value,
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        scope.launch {
            appLanguageStore.language.collect { language ->
                _state.update { current -> current.copy(selectedLanguage = language) }
            }
        }
        onIntent(SettingsIntent.Refresh)
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Refresh -> refresh()
            is SettingsIntent.SelectLanguage -> appLanguageStore.setLanguage(intent.language)
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun refresh() {
        _state.update { current ->
            current.copy(
                isWalletLoading = true,
                isPlatformLoading = true,
            )
        }
        scope.launch {
            runCatching { loginRepository.getSession() }
                .onSuccess { session ->
                    _state.update { current ->
                        current.copy(
                            walletAddress = session?.walletAddress,
                            isWalletLoading = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            walletAddress = null,
                            isWalletLoading = false,
                        )
                    }
                }
        }
        scope.launch {
            runCatching { getPlatformConfigUseCase() }
                .onSuccess { platform ->
                    val networkLabel = platform.walletNetwork?.let { network ->
                        "${network.networkName} (${network.chainId})"
                    } ?: platform.chainId?.let { chainId ->
                        "${platform.networkName} ($chainId)"
                    } ?: platform.networkName
                    _state.update { current ->
                        current.copy(
                            isPlatformLoading = false,
                            networkLabel = networkLabel.ifBlank { null },
                            proxyAddress = platform.proxyAddress?.takeIf { it.isNotBlank() },
                        )
                    }
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            isPlatformLoading = false,
                            networkLabel = null,
                            proxyAddress = null,
                        )
                    }
                }
        }
    }
}
