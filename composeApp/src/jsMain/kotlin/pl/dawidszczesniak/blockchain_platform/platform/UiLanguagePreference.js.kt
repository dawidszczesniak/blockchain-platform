@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.platform

private const val UI_LANGUAGE_STORAGE_KEY = "bp.ui.language"

@JsFun("(key) => window.localStorage.getItem(key)")
private external fun localStorageGet(key: String): String?

@JsFun("(key, value) => window.localStorage.setItem(key, value)")
private external fun localStorageSet(key: String, value: String)

@JsFun(
    """() => {
        const documentLang = globalThis.document?.documentElement?.lang;
        return documentLang || globalThis.window?.navigator?.language || "en";
    }"""
)
private external fun browserLanguageTag(): String

@JsFun(
    """(languageTag) => {
        try {
            Object.defineProperty(globalThis.window.navigator, "language", {
                configurable: true,
                get: () => languageTag
            });
        } catch (_) {}
        try {
            Object.defineProperty(globalThis.window.navigator, "languages", {
                configurable: true,
                get: () => [languageTag]
            });
        } catch (_) {}
        try {
            globalThis.document.documentElement.lang = languageTag;
        } catch (_) {}
    }"""
)
private external fun applyBrowserLanguage(languageTag: String)

actual fun getStoredUiLanguageTag(): String? = localStorageGet(UI_LANGUAGE_STORAGE_KEY)

actual fun setStoredUiLanguageTag(languageTag: String) {
    localStorageSet(UI_LANGUAGE_STORAGE_KEY, languageTag)
    applyBrowserLanguage(languageTag)
}

actual fun detectBrowserLanguageTag(): String = browserLanguageTag()
