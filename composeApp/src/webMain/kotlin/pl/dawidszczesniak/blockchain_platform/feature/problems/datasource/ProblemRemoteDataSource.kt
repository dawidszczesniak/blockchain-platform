package pl.dawidszczesniak.blockchain_platform.feature.problems.datasource

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.encodeToString
import pl.dawidszczesniak.blockchain_platform.network.HttpTextClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreatedProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ParticipationProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemExampleDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemSummaryDto

interface ProblemRemoteDataSource {
    suspend fun fetchProblems(): List<ProblemSummaryDto>
    suspend fun fetchCreatedProblems(): List<CreatedProblemDto>
    suspend fun fetchParticipationProblems(): List<ParticipationProblemDto>
    suspend fun createProblem(request: CreateProblemRequestDto): CreateProblemResponseDto
    suspend fun joinProblem(problemId: Int): JoinProblemResponseDto
}

class ProblemRemoteDataSourceImpl(
    private val apiBaseUrl: String,
    private val httpTextClient: HttpTextClient,
) : ProblemRemoteDataSource {
    override suspend fun fetchProblems(): List<ProblemSummaryDto> {
        val payload = httpTextClient.get(endpoint(apiBaseUrl, "/problems"))
        val json = Json { ignoreUnknownKeys = true }
        val array = json.parseToJsonElement(payload).jsonArray
        return array.map { item ->
            val obj = item.jsonObject
            ProblemSummaryDto(
                id = obj.requiredInt("id"),
                title = obj.requiredString("title"),
                description = obj.requiredString("description"),
                constraints = obj.optionalString("constraints").orEmpty(),
                examples = obj.optionalArray("examples").map { item ->
                    val exampleObj = item.jsonObject
                    ProblemExampleDto(
                        input = exampleObj.requiredString("input"),
                        output = exampleObj.requiredString("output"),
                        explanation = exampleObj.requiredString("explanation"),
                    )
                },
                prizeAmount = obj.requiredLong("prizeAmount"),
                entryFeeAmount = obj.requiredLong("entryFeeAmount"),
                requiredParticipants = obj.requiredInt("requiredParticipants"),
                registeredParticipants = obj.requiredInt("registeredParticipants"),
                daysToStart = obj.requiredInt("daysToStart"),
                daysToJoinEnd = obj.requiredInt("daysToJoinEnd"),
                joinUntilLabel = obj.requiredString("joinUntilLabel"),
                submitUntilLabel = obj.requiredString("submitUntilLabel"),
            )
        }
    }

    override suspend fun fetchCreatedProblems(): List<CreatedProblemDto> {
        val payload = httpTextClient.get(endpoint(apiBaseUrl, "/problems/created"))
        val json = Json { ignoreUnknownKeys = true }
        val array = json.parseToJsonElement(payload).jsonArray
        return array.map { item ->
            val obj = item.jsonObject
            CreatedProblemDto(
                id = obj.requiredInt("id"),
                title = obj.requiredString("title"),
                status = obj.requiredString("status"),
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

    override suspend fun fetchParticipationProblems(): List<ParticipationProblemDto> {
        val payload = httpTextClient.get(endpoint(apiBaseUrl, "/problems/participation"))
        val json = Json { ignoreUnknownKeys = true }
        val array = json.parseToJsonElement(payload).jsonArray
        return array.map { item ->
            val obj = item.jsonObject
            ParticipationProblemDto(
                id = obj.requiredInt("id"),
                title = obj.requiredString("title"),
                status = obj.requiredString("status"),
                timeLeftLabel = obj.requiredString("timeLeftLabel"),
                participants = obj.requiredInt("participants"),
                attemptsCount = obj.requiredInt("attemptsCount"),
            )
        }
    }

    override suspend fun createProblem(request: CreateProblemRequestDto): CreateProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(CreateProblemRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(endpoint(apiBaseUrl, "/problems"), body)
        val obj = json.parseToJsonElement(payload).jsonObject
        return CreateProblemResponseDto(
            id = obj.requiredInt("id"),
        )
    }

    override suspend fun joinProblem(problemId: Int): JoinProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val payload = httpTextClient.postJson(
            endpoint(apiBaseUrl, "/problems/$problemId/join"),
            "{}",
        )
        val obj = json.parseToJsonElement(payload).jsonObject
        return JoinProblemResponseDto(
            joined = obj.requiredBoolean("joined"),
            registeredParticipants = obj.requiredInt("registeredParticipants"),
            requiredParticipants = obj.requiredInt("requiredParticipants"),
        )
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

private fun JsonObject.optionalString(name: String): String? {
    return this[name]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.optionalArray(name: String): List<JsonElement> {
    return this[name]?.jsonArray ?: emptyList()
}

private fun JsonObject.requiredInt(name: String): Int {
    return this[name]?.jsonPrimitive?.intOrNull
        ?: error("Missing or invalid '$name' in backend response.")
}

private fun JsonObject.requiredLong(name: String): Long {
    return this[name]?.jsonPrimitive?.longOrNull
        ?: error("Missing or invalid '$name' in backend response.")
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    return this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
        ?: error("Missing or invalid '$name' in backend response.")
}
