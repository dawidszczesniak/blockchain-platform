package pl.dawidszczesniak.blockchain_platform.feature.auth

internal class AuthValidationException(message: String) : IllegalArgumentException(message)

internal class AuthVerificationException(message: String) : IllegalStateException(message)

internal class AuthRequiredException(message: String) : IllegalStateException(message)

internal class AuthRateLimitException(message: String) : IllegalStateException(message)

internal class AuthServiceUnavailableException(message: String) : IllegalStateException(message)
