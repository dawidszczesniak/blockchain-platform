package pl.dawidszczesniak.blockchain_platform.feature.auth

private const val DEFAULT_LOCAL_SIGN_KEY = "local-dev-sign-key-change-me"

internal enum class SessionSameSite(val cookieValue: String) {
    Lax("Lax"),
    Strict("Strict"),
    None("None"),
}

internal data class AuthRateLimitConfig(
    val challengeRequestsPerMinute: Int,
    val verifyRequestsPerMinute: Int,
    val sessionRequestsPerMinute: Int,
)

internal data class AuthConfig(
    val domain: String,
    val uri: String,
    val challengeTtlSeconds: Long,
    val maxActiveChallengesPerWallet: Int,
    val sessionCookieName: String,
    val sessionSignKey: String,
    val sessionSecureCookie: Boolean,
    val sessionTtlSeconds: Long,
    val sessionSameSite: SessionSameSite,
    val trustProxyHeaders: Boolean,
    val rateLimit: AuthRateLimitConfig,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AuthConfig {
            val appEnv = env["APP_ENV"]?.trim()?.lowercase().orEmpty().ifBlank { "local" }
            val isProductionLike = appEnv == "staging" || appEnv == "prod"

            val domain = env["AUTH_DOMAIN"]?.trim().orEmpty().ifBlank { "localhost:8081" }
            val uri = env["AUTH_URI"]?.trim().orEmpty().ifBlank { "http://localhost:8081" }
            val ttl = env["AUTH_CHALLENGE_TTL_SECONDS"]?.toLongOrNull()?.coerceIn(30, 900) ?: 300L
            val maxActiveChallengesPerWallet = env["AUTH_MAX_ACTIVE_CHALLENGES_PER_WALLET"]
                ?.toIntOrNull()
                ?.coerceIn(1, 20)
                ?: 5
            val cookieName = env["AUTH_SESSION_COOKIE"]?.trim().orEmpty().ifBlank { "bp_auth_session" }
            val signKey = env["AUTH_SESSION_SIGN_KEY"]?.trim().orEmpty()
                .ifBlank { DEFAULT_LOCAL_SIGN_KEY }
            val secureCookie = env["AUTH_SESSION_SECURE"]?.trim()?.equals("true", ignoreCase = true) == true
            val sessionTtlSeconds = env["AUTH_SESSION_TTL_SECONDS"]
                ?.toLongOrNull()
                ?.coerceIn(60, 60L * 60L * 24L * 30L)
                ?: 60L * 60L * 24L * 14L
            val sessionSameSite = env["AUTH_SESSION_SAME_SITE"]
                ?.trim()
                ?.let { parseSameSite(it) }
                ?: SessionSameSite.Lax
            val trustProxyHeaders = (
                env["AUTH_TRUST_PROXY_HEADERS"]
                    ?.trim()
                    ?.equals("true", ignoreCase = true)
                ) == true
            val rateLimit = AuthRateLimitConfig(
                challengeRequestsPerMinute = env["AUTH_RATE_LIMIT_CHALLENGE_PER_MIN"]
                    ?.toIntOrNull()
                    ?.coerceIn(1, 600)
                    ?: 40,
                verifyRequestsPerMinute = env["AUTH_RATE_LIMIT_VERIFY_PER_MIN"]
                    ?.toIntOrNull()
                    ?.coerceIn(1, 600)
                    ?: 80,
                sessionRequestsPerMinute = env["AUTH_RATE_LIMIT_SESSION_PER_MIN"]
                    ?.toIntOrNull()
                    ?.coerceIn(1, 600)
                    ?: 120,
            )

            if (sessionSameSite == SessionSameSite.None && !secureCookie) {
                error("AUTH_SESSION_SAME_SITE=None requires AUTH_SESSION_SECURE=true.")
            }
            if (isProductionLike) {
                if (!secureCookie) {
                    error("AUTH_SESSION_SECURE must be true in staging/prod.")
                }
                if (signKey == DEFAULT_LOCAL_SIGN_KEY || signKey.length < 32) {
                    error("AUTH_SESSION_SIGN_KEY must be a strong random value (>= 32 chars) in staging/prod.")
                }
                if (domain.contains("localhost", ignoreCase = true)) {
                    error("AUTH_DOMAIN cannot point to localhost in staging/prod.")
                }
                if (!uri.startsWith("https://")) {
                    error("AUTH_URI must use https:// in staging/prod.")
                }
            }

            return AuthConfig(
                domain = domain,
                uri = uri,
                challengeTtlSeconds = ttl,
                maxActiveChallengesPerWallet = maxActiveChallengesPerWallet,
                sessionCookieName = cookieName,
                sessionSignKey = signKey,
                sessionSecureCookie = secureCookie,
                sessionTtlSeconds = sessionTtlSeconds,
                sessionSameSite = sessionSameSite,
                trustProxyHeaders = trustProxyHeaders,
                rateLimit = rateLimit,
            )
        }

        private fun parseSameSite(value: String): SessionSameSite {
            return when (value.trim().lowercase()) {
                "strict" -> SessionSameSite.Strict
                "none" -> SessionSameSite.None
                else -> SessionSameSite.Lax
            }
        }
    }
}
