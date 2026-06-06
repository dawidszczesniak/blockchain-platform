package pl.dawidszczesniak.blockchain_platform.feature.auth

internal class AuthValidationException(message: String) : IllegalArgumentException(message)

internal class AuthVerificationException(message: String) : IllegalStateException(message)

internal class AuthRequiredException(message: String) : IllegalStateException(message)

internal enum class AuthSessionExpirationReason {
    IdleTimeout,
    AbsoluteTimeout,
}

internal class AuthSessionExpiredException(
    val reason: AuthSessionExpirationReason,
) : IllegalStateException(reason.authMessage())

internal class AuthRateLimitException(message: String) : IllegalStateException(message)

internal class AuthServiceUnavailableException(message: String) : IllegalStateException(message)

internal class AuthCsrfException(message: String) : IllegalStateException(message)

internal fun AuthSessionExpirationReason.authMessage(): String {
    return when (this) {
        AuthSessionExpirationReason.IdleTimeout ->
            "Session expired due to inactivity. Please login again."
        AuthSessionExpirationReason.AbsoluteTimeout ->
            "Session expired because the maximum session lifetime was reached. Please login again."
    }
}
