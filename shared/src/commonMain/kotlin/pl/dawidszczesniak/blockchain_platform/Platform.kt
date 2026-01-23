package pl.dawidszczesniak.blockchain_platform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform