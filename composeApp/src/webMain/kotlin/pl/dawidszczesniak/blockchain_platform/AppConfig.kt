package pl.dawidszczesniak.blockchain_platform

// Immutable config container for environment and API base URL.
data class AppConfig(
    val environment: AppEnvironment,
    val apiBaseUrl: String,
)

// Lazily resolves build-time environment config.
object AppConfigProvider {
    val config: AppConfig by lazy {
        val environment = parseAppEnvironment(AppEnvironment.fromId(APP_ENV))
        val apiBaseUrl = API_BASE_URL.ifBlank { defaultApiBaseUrl(environment) }
        AppConfig(environment = environment, apiBaseUrl = apiBaseUrl)
    }

    // Default base URLs by environment.
    private fun defaultApiBaseUrl(environment: AppEnvironment): String {
        return when (environment) {
            AppEnvironment.Local -> "http://localhost:8081"
            AppEnvironment.Staging -> "https://staging-api.your-domain.com"
            AppEnvironment.Prod -> "https://api.your-domain.com"
        }
    }
}
