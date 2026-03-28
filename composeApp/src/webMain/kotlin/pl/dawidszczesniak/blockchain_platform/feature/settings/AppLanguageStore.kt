package pl.dawidszczesniak.blockchain_platform.feature.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pl.dawidszczesniak.blockchain_platform.platform.detectBrowserLanguageTag
import pl.dawidszczesniak.blockchain_platform.platform.getStoredUiLanguageTag
import pl.dawidszczesniak.blockchain_platform.platform.setStoredUiLanguageTag

enum class UiLanguage(
    val tag: String,
) {
    Polish("pl"),
    English("en");

    companion object {
        fun fromTag(raw: String?): UiLanguage {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return when {
                normalized.startsWith("pl") -> Polish
                else -> English
            }
        }
    }
}

class AppLanguageStore {
    private val initialLanguage = UiLanguage.fromTag(getStoredUiLanguageTag() ?: detectBrowserLanguageTag())
    private val _language = MutableStateFlow(initialLanguage)
    val language: StateFlow<UiLanguage> = _language.asStateFlow()

    init {
        setStoredUiLanguageTag(initialLanguage.tag)
    }

    fun setLanguage(language: UiLanguage) {
        setStoredUiLanguageTag(language.tag)
        _language.update { language }
    }
}
