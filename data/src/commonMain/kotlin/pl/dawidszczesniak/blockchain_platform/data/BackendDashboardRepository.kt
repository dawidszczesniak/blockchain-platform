package pl.dawidszczesniak.blockchain_platform.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DashboardMetric(
    val metricDate: String,
    val activeChallenges: Int,
    val prizePoolAmount: Long,
    val submissionsCount: Int,
)

data class DashboardUpdate(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: String,
)

interface DashboardRepository {
    suspend fun fetchMetricsHistory(limit: Int = 30): List<DashboardMetric>
    suspend fun fetchLatestUpdates(limit: Int = 3): List<DashboardUpdate>
}

class BackendDashboardRepository(
    private val apiBaseUrl: String,
    private val fetchText: suspend (String) -> String,
) : DashboardRepository {
    override suspend fun fetchMetricsHistory(limit: Int): List<DashboardMetric> {
        val safeLimit = limit.coerceIn(1, 365)
        val payload = fetchText(dashboardMetricsEndpoint(apiBaseUrl, safeLimit))
        return parseDashboardMetrics(payload)
    }

    override suspend fun fetchLatestUpdates(limit: Int): List<DashboardUpdate> {
        val safeLimit = limit.coerceIn(1, 20)
        val payload = fetchText(dashboardUpdatesEndpoint(apiBaseUrl, safeLimit))
        return parseDashboardUpdates(payload)
    }
}

internal fun dashboardMetricsEndpoint(apiBaseUrl: String, limit: Int): String {
    return endpoint(apiBaseUrl, "/dashboard/metrics?limit=$limit")
}

internal fun dashboardUpdatesEndpoint(apiBaseUrl: String, limit: Int): String {
    return endpoint(apiBaseUrl, "/dashboard/updates?limit=$limit")
}

private fun endpoint(apiBaseUrl: String, path: String): String {
    val base = apiBaseUrl.trimEnd('/')
    return if (base.isBlank()) {
        path
    } else {
        "$base$path"
    }
}

private fun parseDashboardMetrics(payload: String): List<DashboardMetric> {
    val json = Json { ignoreUnknownKeys = true }
    val array = json.parseToJsonElement(payload).jsonArray
    return array.map { item ->
        val obj = item.jsonObject
        DashboardMetric(
            metricDate = obj.requiredString("metricDate"),
            activeChallenges = obj.requiredInt("activeChallenges"),
            prizePoolAmount = obj.requiredLong("prizePoolAmount"),
            submissionsCount = obj.requiredInt("submissionsCount"),
        )
    }
}

private fun parseDashboardUpdates(payload: String): List<DashboardUpdate> {
    val json = Json { ignoreUnknownKeys = true }
    val array = json.parseToJsonElement(payload).jsonArray
    return array.map { item ->
        val obj = item.jsonObject
        DashboardUpdate(
            id = obj.requiredLong("id"),
            title = obj.requiredString("title"),
            body = obj.requiredString("body"),
            createdAt = obj.requiredString("createdAt"),
        )
    }
}

private fun JsonObject.requiredInt(name: String): Int {
    return requiredField(name).jsonPrimitive.int
}

private fun JsonObject.requiredLong(name: String): Long {
    val primitive = requiredField(name).jsonPrimitive
    return primitive.contentOrNull?.toLongOrNull()
        ?: error("Field '$name' must be a JSON long.")
}

private fun JsonObject.requiredString(name: String): String {
    return requiredField(name).jsonPrimitive.contentOrNull
        ?: error("Field '$name' must be a JSON string.")
}

private fun JsonObject.requiredField(name: String): JsonElement {
    return this[name] ?: error("Missing field '$name' in backend payload.")
}
