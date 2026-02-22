@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform

import kotlin.JsFun
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsBoolean
import kotlin.js.Promise
import kotlin.js.toBoolean
import kotlin.js.toJsBoolean

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
