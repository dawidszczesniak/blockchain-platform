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
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemValidationTestResultDto

interface ProblemRemoteDataSource {
    suspend fun fetchProblems(): List<ProblemSummaryDto>
    suspend fun fetchCreatedProblems(): List<CreatedProblemDto>
    suspend fun fetchParticipationProblems(): List<ParticipationProblemDto>
    suspend fun createProblem(request: CreateProblemRequestDto): CreateProblemResponseDto
    suspend fun validateCreateProblem(request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto
    suspend fun joinProblem(problemId: Int): JoinProblemResponseDto
    suspend fun runProblemCode(problemId: Int, request: RunProblemRequestDto): RunProblemResponseDto
    suspend fun submitProblemCode(problemId: Int, request: RunProblemRequestDto): SubmitProblemResponseDto
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

    override suspend fun validateCreateProblem(request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(ValidateCreateProblemRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(endpoint(apiBaseUrl, "/problems/create/validate"), body)
        val obj = json.parseToJsonElement(payload).jsonObject
        val results = obj.optionalArray("results").map { item ->
            val resultObj = item.jsonObject
            CreateProblemValidationTestResultDto(
                index = resultObj.requiredInt("index"),
                status = resultObj.requiredString("status"),
                output = resultObj.optionalString("output"),
                executionTimeMs = resultObj.requiredInt("executionTimeMs"),
                message = resultObj.optionalString("message"),
            )
        }
        return ValidateCreateProblemResponseDto(
            total = obj.requiredInt("total"),
            successful = obj.requiredInt("successful"),
            allSuccessful = obj.requiredBoolean("allSuccessful"),
            results = results,
            sandboxNodeId = obj.optionalString("sandboxNodeId"),
            sandboxImageHash = obj.optionalString("sandboxImageHash"),
            sandboxRunHash = obj.optionalString("sandboxRunHash"),
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

    override suspend fun runProblemCode(
        problemId: Int,
        request: RunProblemRequestDto,
    ): RunProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(RunProblemRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(
            endpoint(apiBaseUrl, "/problems/$problemId/run"),
            body,
        )
        val obj = json.parseToJsonElement(payload).jsonObject
        val results = obj.optionalArray("results").map { item ->
            val resultObj = item.jsonObject
            RunProblemTestResultDto(
                index = resultObj.requiredInt("index"),
                status = resultObj.requiredString("status"),
                passed = resultObj.requiredBoolean("passed"),
                hidden = resultObj.requiredBoolean("hidden"),
                executionTimeMs = resultObj.requiredInt("executionTimeMs"),
                input = resultObj.optionalString("input"),
                expectedOutput = resultObj.optionalString("expectedOutput"),
                actualOutput = resultObj.optionalString("actualOutput"),
                message = resultObj.optionalString("message"),
            )
        }
        return RunProblemResponseDto(
            total = obj.requiredInt("total"),
            passed = obj.requiredInt("passed"),
            allPassed = obj.requiredBoolean("allPassed"),
            results = results,
            sandboxNodeId = obj.optionalString("sandboxNodeId"),
            sandboxImageHash = obj.optionalString("sandboxImageHash"),
            sandboxRunHash = obj.optionalString("sandboxRunHash"),
        )
    }

    override suspend fun submitProblemCode(
        problemId: Int,
        request: RunProblemRequestDto,
    ): SubmitProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(RunProblemRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(
            endpoint(apiBaseUrl, "/problems/$problemId/submit"),
            body,
        )
        val obj = json.parseToJsonElement(payload).jsonObject
        val results = obj.optionalArray("results").map { item ->
            val resultObj = item.jsonObject
            RunProblemTestResultDto(
                index = resultObj.requiredInt("index"),
                status = resultObj.requiredString("status"),
                passed = resultObj.requiredBoolean("passed"),
                hidden = resultObj.requiredBoolean("hidden"),
                executionTimeMs = resultObj.requiredInt("executionTimeMs"),
                input = resultObj.optionalString("input"),
                expectedOutput = resultObj.optionalString("expectedOutput"),
                actualOutput = resultObj.optionalString("actualOutput"),
                message = resultObj.optionalString("message"),
            )
        }
        return SubmitProblemResponseDto(
            submissionId = obj.requiredLong("submissionId"),
            total = obj.requiredInt("total"),
            passed = obj.requiredInt("passed"),
            allPassed = obj.requiredBoolean("allPassed"),
            results = results,
            consensusRequired = obj.requiredInt("consensusRequired"),
            consensusReached = obj.requiredInt("consensusReached"),
            sandboxImageHash = obj.optionalString("sandboxImageHash"),
            sandboxResultHash = obj.requiredString("sandboxResultHash"),
            commitmentHash = obj.requiredString("commitmentHash"),
            anchorStatus = obj.requiredString("anchorStatus"),
            anchorBatchId = obj.optionalLong("anchorBatchId"),
            anchorMerkleRoot = obj.optionalString("anchorMerkleRoot"),
            anchorProof = obj.optionalArray("anchorProof").mapNotNull { proofItem ->
                proofItem.jsonPrimitive.contentOrNull
            },
            anchorTxHash = obj.optionalString("anchorTxHash"),
            anchorExplorerUrl = obj.optionalString("anchorExplorerUrl"),
            anchorError = obj.optionalString("anchorError"),
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

private fun JsonObject.optionalLong(name: String): Long? {
    return this[name]?.jsonPrimitive?.longOrNull
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    return this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
        ?: error("Missing or invalid '$name' in backend response.")
}
