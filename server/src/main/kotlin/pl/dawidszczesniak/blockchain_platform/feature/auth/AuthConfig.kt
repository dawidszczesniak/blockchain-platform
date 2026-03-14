package pl.dawidszczesniak.blockchain_platform.feature.auth

internal data class AuthConfig(
    val domain: String,
    val uri: String,
    val challengeTtlSeconds: Long,
    val sessionCookieName: String,
    val sessionSignKey: String,
    val sessionSecureCookie: Boolean,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AuthConfig {
            val domain = env["AUTH_DOMAIN"]?.trim().orEmpty().ifBlank { "localhost:8081" }
            val uri = env["AUTH_URI"]?.trim().orEmpty().ifBlank { "http://localhost:8081" }
            val ttl = env["AUTH_CHALLENGE_TTL_SECONDS"]?.toLongOrNull()?.coerceAtLeast(30) ?: 300L
            val cookieName = env["AUTH_SESSION_COOKIE"]?.trim().orEmpty().ifBlank { "bp_auth_session" }
            val signKey = env["AUTH_SESSION_SIGN_KEY"]?.trim().orEmpty()
                .ifBlank { "local-dev-sign-key-change-me" }
            val secureCookie = env["AUTH_SESSION_SECURE"]?.trim()?.equals("true", ignoreCase = true) == true

            return AuthConfig(
                domain = domain,
                uri = uri,
                challengeTtlSeconds = ttl,
                sessionCookieName = cookieName,
                sessionSignKey = signKey,
                sessionSecureCookie = secureCookie,
            )
        }
    }
}
