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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsBoolean
import kotlin.js.Promise
import kotlin.js.toBoolean
import kotlin.js.toJsBoolean

data class ClientConnectivityState(
    val isOnline: Boolean = true,
)

class ClientConnectivityMonitor(
    private val intervalMs: Long = 2_000L,
) {
    private val listenerId = "bp-client-connectivity-${nextListenerId++}"
    private val _state = MutableStateFlow(ClientConnectivityState(isOnline = isBrowserOnline()))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var browserOnline = isBrowserOnline()
    private var probeInFlight = false
    val state: StateFlow<ClientConnectivityState> = _state.asStateFlow()

    init {
        registerClientConnectivityListener(listenerId) { isOnline ->
            browserOnline = isOnline
            if (isOnline) {
                refreshConnectivityAsync()
            } else {
                _state.value = ClientConnectivityState(isOnline = false)
            }
        }
        scope.launch {
            while (isActive) {
                refreshConnectivity()
                delay(intervalMs)
            }
        }
    }

    private fun refreshConnectivityAsync() {
        scope.launch {
            refreshConnectivity()
        }
    }

    private suspend fun refreshConnectivity() {
        if (probeInFlight) return
        probeInFlight = true
        try {
            if (!browserOnline) {
                _state.value = ClientConnectivityState(isOnline = false)
                return
            }
            val canReachFrontend = runCatching {
                probeFrontendConnectivity()
            }.getOrDefault(false)
            _state.value = ClientConnectivityState(isOnline = canReachFrontend)
        } finally {
            probeInFlight = false
        }
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

private suspend fun probeFrontendConnectivity(): Boolean {
    return suspendCoroutine { continuation ->
        fetchFrontendConnectivityOk().then(
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

@JsFun(
    """
() => {
  const readBrowserOnline = () => {
    try {
      return typeof navigator === "undefined" || navigator.onLine !== false;
    } catch (_) {
      return true;
    }
  };
  if (!readBrowserOnline()) {
    return Promise.resolve(false);
  }

  let url = "/";
  try {
    const href = globalThis.location && globalThis.location.href ? globalThis.location.href : "/";
    const parsed = new URL(href, globalThis.location && globalThis.location.origin ? globalThis.location.origin : undefined);
    parsed.searchParams.set("__bp_connectivity_probe", String(Date.now()));
    url = parsed.toString();
  } catch (_) {
  }

  const request = (method) => fetch(url, {
    method,
    cache: "no-store",
    credentials: "same-origin",
  }).then(() => true);

  return request("HEAD")
    .catch(() => request("GET"))
    .catch(() => false);
}
"""
)
private external fun fetchFrontendConnectivityOk(): Promise<JsBoolean>
