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
        val rawApiBaseUrl = API_BASE_URL.ifBlank { defaultApiBaseUrl(environment) }
        val apiBaseUrl = enforceLocalBackend(environment, rawApiBaseUrl)
        AppConfig(environment = environment, apiBaseUrl = apiBaseUrl)
    }

    // Default base URLs by environment.
    private fun defaultApiBaseUrl(environment: AppEnvironment): String {
        return when (environment) {
            AppEnvironment.Local -> "http://$LOCAL_HOST:$SERVER_PORT"
            AppEnvironment.Staging -> "https://staging-api.your-domain.com"
            AppEnvironment.Prod -> "https://api.your-domain.com"
        }
    }

    private fun enforceLocalBackend(environment: AppEnvironment, apiBaseUrl: String): String {
        if (environment != AppEnvironment.Local) {
            return apiBaseUrl
        }
        val expected = "http://$LOCAL_HOST:$SERVER_PORT"
        require(apiBaseUrl == expected) {
            "Local frontend can connect only to $expected."
        }
        return apiBaseUrl
    }
}
