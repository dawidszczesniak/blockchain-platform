@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.feature.maintenance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.JsFun

data class ClientConnectivityState(
    val isOnline: Boolean = true,
)

class ClientConnectivityMonitor(
    private val intervalMs: Long = 2_000L,
) {
    private val listenerId = "bp-client-connectivity-${nextListenerId++}"
    private val _state = MutableStateFlow(ClientConnectivityState(isOnline = isBrowserOnline()))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val state: StateFlow<ClientConnectivityState> = _state.asStateFlow()

    init {
        registerClientConnectivityListener(listenerId) { isOnline ->
            updateBrowserConnectivity(isOnline)
        }
        scope.launch {
            while (isActive) {
                updateBrowserConnectivity(isBrowserOnline())
                delay(intervalMs)
            }
        }
    }

    private fun updateBrowserConnectivity(isOnline: Boolean) {
        _state.value = ClientConnectivityState(isOnline = isOnline)
    }
}

private var nextListenerId = 1L

@JsFun(
    """
() => {
  try {
    return typeof navigator === "undefined" || navigator.onLine !== false;
  } catch (_) {
    return true;
  }
}
"""
)
private external fun isBrowserOnline(): Boolean

@JsFun(
    """
(listenerId, listener) => {
  const registry = globalThis.__bpClientConnectivityListeners ?? (globalThis.__bpClientConnectivityListeners = {});
  const key = String(listenerId);
  if (registry[key]) {
    return;
  }
  const readOnline = () => {
    try {
      return typeof navigator === "undefined" || navigator.onLine !== false;
    } catch (_) {
      return true;
    }
  };
  const emit = () => {
    try {
      listener(readOnline());
    } catch (_) {
    }
  };
  const onOnline = () => emit();
  const onOffline = () => emit();
  if (typeof globalThis.addEventListener === "function") {
    globalThis.addEventListener("online", onOnline);
    globalThis.addEventListener("offline", onOffline);
  }
  registry[key] = { onOnline, onOffline };
  emit();
}
"""
)
private external fun registerClientConnectivityListener(
    listenerId: String,
    listener: (Boolean) -> Unit,
)
