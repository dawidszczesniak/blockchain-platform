package pl.dawidszczesniak.blockchain_platform.feature.auth

import java.net.URI

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
    val trustedOrigins: Set<String>,
    val rateLimit: AuthRateLimitConfig,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String>): AuthConfig {
            val appEnv = env["APP_ENV"]?.trim()?.lowercase().orEmpty().ifBlank { "local" }
            val isProductionLike = appEnv == "staging" || appEnv == "prod"

            val domain = env["AUTH_DOMAIN"]?.trim().orEmpty().ifBlank {
                error("AUTH_DOMAIN must be configured.")
            }
            val uri = env["AUTH_URI"]?.trim().orEmpty().ifBlank {
                error("AUTH_URI must be configured.")
            }
            val ttl = env["AUTH_CHALLENGE_TTL_SECONDS"]?.toLongOrNull()?.coerceIn(30, 900) ?: 300L
            val maxActiveChallengesPerWallet = env["AUTH_MAX_ACTIVE_CHALLENGES_PER_WALLET"]
                ?.toIntOrNull()
                ?.coerceIn(1, 20)
                ?: 5
            val cookieName = env["AUTH_SESSION_COOKIE"]?.trim().orEmpty().ifBlank { "bp_auth_session" }
            val signKey = env["AUTH_SESSION_SIGN_KEY"]?.trim().orEmpty().ifBlank {
                error("AUTH_SESSION_SIGN_KEY must be configured.")
            }
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
            val trustedOrigins = env["AUTH_TRUSTED_ORIGINS"]
                ?.split(',')
                ?.mapNotNull { it.toNormalizedOriginOrNull() }
                ?.toSet()
                .orEmpty()
                .ifEmpty {
                    setOf(
                        uri.toNormalizedOriginOrNull()
                            ?: error("AUTH_URI must contain valid scheme/host origin.")
                    )
                }
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
                if (signKey.length < 32) {
                    error("AUTH_SESSION_SIGN_KEY must be a strong random value (>= 32 chars) in staging/prod.")
                }
                if (domain.contains("localhost", ignoreCase = true)) {
                    error("AUTH_DOMAIN cannot point to localhost in staging/prod.")
                }
                if (!uri.startsWith("https://")) {
                    error("AUTH_URI must use https:// in staging/prod.")
                }
                if (trustedOrigins.any { !it.startsWith("https://") }) {
                    error("AUTH_TRUSTED_ORIGINS must use https:// origins in staging/prod.")
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
                trustedOrigins = trustedOrigins,
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

private fun String.toNormalizedOriginOrNull(): String? {
    val parsed = runCatching { URI(this.trim()) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.trim()?.lowercase().orEmpty()
    val host = parsed.host?.trim()?.lowercase().orEmpty()
    if ((scheme != "http" && scheme != "https") || host.isBlank()) {
        return null
    }
    val port = when {
        parsed.port > 0 -> parsed.port
        scheme == "https" -> 443
        else -> 80
    }
    val includePort = !(scheme == "https" && port == 443) && !(scheme == "http" && port == 80)
    return if (includePort) {
        "$scheme://$host:$port"
    } else {
        "$scheme://$host"
    }
}
