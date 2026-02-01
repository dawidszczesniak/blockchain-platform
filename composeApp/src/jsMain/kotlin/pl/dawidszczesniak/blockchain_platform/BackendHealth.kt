package pl.dawidszczesniak.blockchain_platform

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.unsafeCast
import org.w3c.fetch.RequestCache
import org.w3c.fetch.RequestInit

// JS implementation uses fetch to call /health.
actual suspend fun checkBackendHealth(apiBaseUrl: String): Boolean {
    val base = apiBaseUrl.trimEnd('/')
    val url = if (base.isBlank()) "/health" else "$base/health"
    return try {
        val response = window.fetch(
            url,
            RequestInit(
                method = "GET",
                cache = "no-store".unsafeCast<RequestCache>(),
            )
        ).await()
        response.ok
    } catch (_: Throwable) {
        false
    }
}
