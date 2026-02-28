package pl.dawidszczesniak.blockchain_platform.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.domain.repository.ProblemRepository

// Repository implementation that reads UI data from backend API.
class BackendProblemRepository(
    private val apiBaseUrl: String,
    private val fetchText: suspend (String) -> String,
) : ProblemRepository {
    override suspend fun fetchProblems(): List<ProblemSummary> {
        val payload = fetchText(problemsEndpoint(apiBaseUrl))
        return parseProblemSummaries(payload)
    }

    override suspend fun fetchCreatedProblems(): List<CreatedProblem> {
        val payload = fetchText(createdProblemsEndpoint(apiBaseUrl))
        return parseCreatedProblems(payload)
    }

    override suspend fun fetchParticipationProblems(): List<ParticipationProblem> {
        val payload = fetchText(participationProblemsEndpoint(apiBaseUrl))
        return parseParticipationProblems(payload)
    }
}

internal fun problemsEndpoint(apiBaseUrl: String): String {
    return endpoint(apiBaseUrl, "/problems")
}

internal fun createdProblemsEndpoint(apiBaseUrl: String): String {
    return endpoint(apiBaseUrl, "/problems/created")
}

internal fun participationProblemsEndpoint(apiBaseUrl: String): String {
    return endpoint(apiBaseUrl, "/problems/participation")
}

private fun endpoint(apiBaseUrl: String, path: String): String {
    val base = apiBaseUrl.trimEnd('/')
    return if (base.isBlank()) {
        path
    } else {
        "$base$path"
    }
}

private fun parseProblemSummaries(payload: String): List<ProblemSummary> {
    val json = Json { ignoreUnknownKeys = true }
    val array = json.parseToJsonElement(payload).jsonArray
    return array.map { item ->
        val obj = item.jsonObject
        ProblemSummary(
            id = obj.requiredInt("id"),
            title = obj.requiredString("title"),
            description = obj.requiredString("description"),
            prizeAmount = obj.requiredInt("prizeAmount"),
            entryFeeAmount = obj.requiredInt("entryFeeAmount"),
            requiredParticipants = obj.requiredInt("requiredParticipants"),
            registeredParticipants = obj.requiredInt("registeredParticipants"),
            daysToStart = obj.requiredInt("daysToStart"),
            daysToJoinEnd = obj.requiredInt("daysToJoinEnd"),
            joinUntilLabel = obj.requiredString("joinUntilLabel"),
            submitUntilLabel = obj.requiredString("submitUntilLabel"),
        )
    }
}

private fun parseCreatedProblems(payload: String): List<CreatedProblem> {
    val json = Json { ignoreUnknownKeys = true }
    val array = json.parseToJsonElement(payload).jsonArray
    return array.map { item ->
        val obj = item.jsonObject
        CreatedProblem(
            id = obj.requiredInt("id"),
            title = obj.requiredString("title"),
            status = obj.requiredCreatedStatus("status"),
            requiredParticipants = obj.requiredInt("requiredParticipants"),
            registeredParticipants = obj.requiredInt("registeredParticipants"),
            submissions = obj.requiredInt("submissions"),
            startedOn = obj.optionalString("startedOn"),
            finishedOn = obj.optionalString("finishedOn"),
            registrationEnds = obj.optionalString("registrationEnds"),
            timeElapsed = obj.optionalString("timeElapsed"),
            winner = obj.optionalString("winner"),
        )
    }
}

private fun parseParticipationProblems(payload: String): List<ParticipationProblem> {
    val json = Json { ignoreUnknownKeys = true }
    val array = json.parseToJsonElement(payload).jsonArray
    return array.map { item ->
        val obj = item.jsonObject
        ParticipationProblem(
            id = obj.requiredInt("id"),
            title = obj.requiredString("title"),
            status = obj.requiredParticipationStatus("status"),
            timeLeftLabel = obj.requiredString("timeLeftLabel"),
            participants = obj.requiredInt("participants"),
            attemptsCount = obj.requiredInt("attemptsCount"),
        )
    }
}

private fun JsonObject.requiredInt(name: String): Int {
    val value = requiredField(name)
    return value.jsonPrimitive.int
}

private fun JsonObject.requiredString(name: String): String {
    val value = requiredField(name)
    return value.jsonPrimitive.contentOrNull
        ?: error("Field '$name' must be a JSON string.")
}

private fun JsonObject.optionalString(name: String): String? {
    val value = this[name] ?: return null
    return value.jsonPrimitive.contentOrNull
}

private fun JsonObject.requiredCreatedStatus(name: String): CreatedProblemStatus {
    val raw = requiredString(name)
    return try {
        CreatedProblemStatus.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        error("Unknown created status '$raw'.")
    }
}

private fun JsonObject.requiredParticipationStatus(name: String): ParticipationStatus {
    val raw = requiredString(name)
    return try {
        ParticipationStatus.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        error("Unknown participation status '$raw'.")
    }
}

private fun JsonObject.requiredField(name: String): JsonElement {
    return this[name] ?: error("Missing field '$name' in backend payload.")
}
