package pl.dawidszczesniak.blockchain_platform.feature.platform.datasource

import kotlinx.serialization.json.Json
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PlatformConfigDto
import pl.dawidszczesniak.blockchain_platform.network.HttpTextClient

interface PlatformRemoteDataSource {
    suspend fun fetchPlatformConfig(): PlatformConfigDto
}

class PlatformRemoteDataSourceImpl(
    private val apiBaseUrl: String,
    private val httpTextClient: HttpTextClient,
) : PlatformRemoteDataSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchPlatformConfig(): PlatformConfigDto {
        val payload = httpTextClient.get(endpoint(apiBaseUrl, "/platform/meta"))
        return json.decodeFromString<PlatformConfigDto>(payload)
    }
}

private fun endpoint(apiBaseUrl: String, path: String): String {
    val base = apiBaseUrl.trimEnd('/')
    return if (base.isBlank()) path else "$base$path"
}
