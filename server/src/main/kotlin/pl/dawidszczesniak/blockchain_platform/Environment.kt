package pl.dawidszczesniak.blockchain_platform

const val LOCAL_HOST = "localhost"
const val FRONTEND_PORT = 8081
const val SERVER_PORT = 8080

sealed class AppEnvironment(val id: String) {
    data object Local : AppEnvironment("local")
    data object Staging : AppEnvironment("staging")
    data object Prod : AppEnvironment("prod")

    companion object {
        fun fromId(id: String): AppEnvironment = when (id) {
            Local.id -> Local
            Staging.id -> Staging
            Prod.id -> Prod
            else -> Local
        }
    }
}

fun parseAppEnvironment(environment: AppEnvironment): AppEnvironment {
    return when (environment) {
        AppEnvironment.Local -> AppEnvironment.Local
        AppEnvironment.Staging -> AppEnvironment.Staging
        AppEnvironment.Prod -> AppEnvironment.Prod
    }
}
