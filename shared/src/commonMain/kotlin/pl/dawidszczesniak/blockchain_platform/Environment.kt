package pl.dawidszczesniak.blockchain_platform

// Supported runtime environments for the app.
enum class AppEnvironment {
    Local,
    Staging,
    Prod,
}

// Maps a raw string into a known environment, defaulting to Local.
fun parseAppEnvironment(raw: String?): AppEnvironment {
    return when (raw?.lowercase()) {
        "prod", "production" -> AppEnvironment.Prod
        "staging", "stage" -> AppEnvironment.Staging
        else -> AppEnvironment.Local
    }
}
