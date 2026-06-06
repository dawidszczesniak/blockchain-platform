package pl.dawidszczesniak.blockchain_platform.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SessionExpirationNotifier {
    private val _events = MutableSharedFlow<SessionExpirationReason>(extraBufferCapacity = 1)
    val events: SharedFlow<SessionExpirationReason> = _events.asSharedFlow()

    fun notifySessionExpired(reason: SessionExpirationReason) {
        _events.tryEmit(reason)
    }
}

enum class SessionExpirationReason {
    IdleTimeout,
    AbsoluteTimeout,
    Unknown,
}

fun HttpStatusException.isSessionExpiredResponse(): Boolean {
    return sessionExpirationReason() != null
}

fun HttpStatusException.sessionExpirationReason(): SessionExpirationReason? {
    if (statusCode != 401 || !details.contains("session expired", ignoreCase = true)) {
        return null
    }
    return when {
        details.contains("inactivity", ignoreCase = true) -> SessionExpirationReason.IdleTimeout
        details.contains("maximum session lifetime", ignoreCase = true) -> SessionExpirationReason.AbsoluteTimeout
        else -> SessionExpirationReason.Unknown
    }
}
