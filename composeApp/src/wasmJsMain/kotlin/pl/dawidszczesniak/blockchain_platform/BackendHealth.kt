package pl.dawidszczesniak.blockchain_platform

// TODO(backend): Implement health check for Wasm when fetch interop is available.
actual suspend fun checkBackendHealth(apiBaseUrl: String): Boolean = true
