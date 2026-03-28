package pl.dawidszczesniak.blockchain_platform.platform

expect fun getStoredUiLanguageTag(): String?
expect fun setStoredUiLanguageTag(languageTag: String)
expect fun detectBrowserLanguageTag(): String
