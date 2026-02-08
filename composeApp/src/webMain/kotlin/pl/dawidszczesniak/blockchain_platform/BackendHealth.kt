@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.JsFun
import kotlin.js.JsBoolean
import kotlin.js.Promise
import kotlin.js.toBoolean
import kotlin.js.toJsBoolean

// Periodically checks backend health and exposes the latest status.
@Composable
fun rememberBackendHealth(
    apiBaseUrl: String,
    intervalMs: Long = 15_000L,
): State<Boolean?> {
    return produceState<Boolean?>(initialValue = null, apiBaseUrl) {
        while (true) {
            value = runCatching { checkBackendHealth(apiBaseUrl) }.getOrDefault(false)
            delay(intervalMs)
        }
    }
}

// TODO(backend): Expose a lightweight `/health` endpoint for this check.
// JS/Wasm health check using JS interop.
suspend fun checkBackendHealth(apiBaseUrl: String): Boolean {
    val base = apiBaseUrl.trimEnd('/')
    val url = if (base.isBlank()) "/health" else "$base/health"
    return suspendCoroutine { continuation ->
        fetchHealthOk(url).then(
            onFulfilled = { ok ->
                continuation.resume(ok.toBoolean())
                ok
            },
            onRejected = {
                continuation.resume(false)
                false.toJsBoolean()
            }
        )
    }
}

@JsFun("(url) => fetch(url, { method: 'GET', cache: 'no-store' }).then(r => r.ok)")
private external fun fetchHealthOk(url: String): Promise<JsBoolean>
