package pl.dawidszczesniak.blockchain_platform.config

import pl.dawidszczesniak.blockchain_platform.API_BASE_URL
import pl.dawidszczesniak.blockchain_platform.APP_ENV
import pl.dawidszczesniak.blockchain_platform.AppEnvironment
import pl.dawidszczesniak.blockchain_platform.LOCAL_HOST
import pl.dawidszczesniak.blockchain_platform.SERVER_PORT
import pl.dawidszczesniak.blockchain_platform.parseAppEnvironment

data class AppConfig(
    val environment: AppEnvironment,
    val apiBaseUrl: String,
)

object AppConfigProvider {
    val config: AppConfig by lazy {
        val environment = parseAppEnvironment(AppEnvironment.fromId(APP_ENV))
        val rawApiBaseUrl = API_BASE_URL.ifBlank { defaultApiBaseUrl(environment) }
        val apiBaseUrl = enforceLocalBackend(environment, rawApiBaseUrl)
        AppConfig(environment = environment, apiBaseUrl = apiBaseUrl)
    }

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
