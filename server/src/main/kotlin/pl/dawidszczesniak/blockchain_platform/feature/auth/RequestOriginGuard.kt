package pl.dawidszczesniak.blockchain_platform.feature.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import java.net.URI

internal fun ApplicationCall.requireTrustedOrigin(authConfig: AuthConfig) {
    val origin = request.header(HttpHeaders.Origin).toNormalizedOriginOrNull()
        ?: request.header(HttpHeaders.Referrer).toNormalizedOriginOrNull()
        ?: throw AuthCsrfException("Missing Origin/Referer header.")

    if (origin !in authConfig.trustedOrigins) {
        throw AuthCsrfException("Request origin is not allowed.")
    }
}

private fun String?.toNormalizedOriginOrNull(): String? {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) {
        return null
    }
    val parsed = runCatching { URI(raw) }.getOrNull() ?: return null
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
