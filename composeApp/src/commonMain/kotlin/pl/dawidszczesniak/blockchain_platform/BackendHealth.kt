package pl.dawidszczesniak.blockchain_platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

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
// Platform-specific health check (JS/Wasm).
expect suspend fun checkBackendHealth(apiBaseUrl: String): Boolean
