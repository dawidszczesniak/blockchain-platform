package pl.dawidszczesniak.blockchain_platform.feature.dashboard.datasource

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto.DashboardMetricDto
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dto.DashboardUpdateDto
import pl.dawidszczesniak.blockchain_platform.network.HttpTextClient

interface DashboardRemoteDataSource {
    suspend fun fetchMetricsHistory(limit: Int): List<DashboardMetricDto>
    suspend fun fetchLatestUpdates(limit: Int): List<DashboardUpdateDto>
}

class DashboardRemoteDataSourceImpl(
    private val apiBaseUrl: String,
    private val httpTextClient: HttpTextClient,
) : DashboardRemoteDataSource {
    override suspend fun fetchMetricsHistory(limit: Int): List<DashboardMetricDto> {
        val safeLimit = limit.coerceIn(1, 365)
        val payload = httpTextClient.get(endpoint(apiBaseUrl, "/dashboard/metrics?limit=$safeLimit"))
        val json = Json { ignoreUnknownKeys = true }
        val array = json.parseToJsonElement(payload).jsonArray
        return array.map { item ->
            val obj = item.jsonObject
            DashboardMetricDto(
                metricDate = obj.requiredString("metricDate"),
                activeChallenges = obj.requiredInt("activeChallenges"),
                prizePoolLabel = obj.requiredString("prizePoolLabel"),
                submissionsCount = obj.requiredInt("submissionsCount"),
            )
        }
    }

    override suspend fun fetchLatestUpdates(limit: Int): List<DashboardUpdateDto> {
        val safeLimit = limit.coerceIn(1, 20)
        val payload = httpTextClient.get(endpoint(apiBaseUrl, "/dashboard/updates?limit=$safeLimit"))
        val json = Json { ignoreUnknownKeys = true }
        val array = json.parseToJsonElement(payload).jsonArray
        return array.map { item ->
            val obj = item.jsonObject
            DashboardUpdateDto(
                id = obj.requiredLong("id"),
                title = obj.requiredString("title"),
                body = obj.requiredString("body"),
                createdAt = obj.requiredString("createdAt"),
            )
        }
    }
}

private fun endpoint(apiBaseUrl: String, path: String): String {
    val base = apiBaseUrl.trimEnd('/')
    return if (base.isBlank()) {
        path
    } else {
        "$base$path"
    }
}

private fun JsonObject.requiredString(name: String): String {
    return this[name]?.jsonPrimitive?.contentOrNull
        ?: error("Missing or invalid '$name' in backend response.")
}

private fun JsonObject.requiredInt(name: String): Int {
    return this[name]?.jsonPrimitive?.intOrNull
        ?: error("Missing or invalid '$name' in backend response.")
}

private fun JsonObject.requiredLong(name: String): Long {
    return this[name]?.jsonPrimitive?.longOrNull
        ?: error("Missing or invalid '$name' in backend response.")
}
