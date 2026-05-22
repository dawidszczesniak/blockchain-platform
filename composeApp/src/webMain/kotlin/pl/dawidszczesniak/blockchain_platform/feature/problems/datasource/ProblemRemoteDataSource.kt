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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import pl.dawidszczesniak.blockchain_platform.network.HttpTextClient
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PaymentAssetDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreatedProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CancelCreateProblemValidationRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ParticipationProblemDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareJoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemExampleDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemSummaryDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemValidationTestResultDto

interface ProblemRemoteDataSource {
    suspend fun fetchProblems(): List<ProblemSummaryDto>
    suspend fun fetchProblemById(problemId: Int): ProblemSummaryDto
    suspend fun fetchCreatedProblems(): List<CreatedProblemDto>
    suspend fun fetchParticipationProblems(): List<ParticipationProblemDto>
    suspend fun prepareCreateProblemOnChain(request: CreateProblemRequestDto): PrepareCreateProblemResponseDto
    suspend fun confirmCreateProblemOnChain(request: ConfirmCreateProblemRequestDto): CreateProblemResponseDto
    suspend fun validateCreateProblem(request: ValidateCreateProblemRequestDto): ValidateCreateProblemResponseDto
    suspend fun cancelCreateProblemValidation(runId: String)
    suspend fun prepareJoinProblemOnChain(problemId: Int): PrepareJoinProblemResponseDto
    suspend fun confirmJoinProblemOnChain(problemId: Int, request: ConfirmJoinProblemRequestDto): JoinProblemResponseDto
    suspend fun runProblemCode(problemId: Int, request: RunProblemRequestDto): RunProblemResponseDto
    suspend fun submitProblemCode(problemId: Int, request: RunProblemRequestDto): SubmissionJudgeJobDto
    suspend fun fetchSubmissionJudgeJob(jobId: Long): SubmissionJudgeJobDto
    suspend fun retrySubmissionJudgeJob(jobId: Long): SubmissionJudgeJobDto
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
                paymentAsset = json.decodeFromJsonElement(
                    PaymentAssetDto.serializer(),
                    obj.requiredObject("paymentAsset"),
                ),
                prizeAmountAtomic = obj.requiredString("prizeAmountAtomic"),
                entryFeeAmountAtomic = obj.requiredString("entryFeeAmountAtomic"),
                requiredParticipants = obj.requiredInt("requiredParticipants"),
                registeredParticipants = obj.requiredInt("registeredParticipants"),
                daysToStart = obj.requiredInt("daysToStart"),
                daysToJoinEnd = obj.requiredInt("daysToJoinEnd"),
                joinUntilLabel = obj.requiredString("joinUntilLabel"),
                submitUntilLabel = obj.requiredString("submitUntilLabel"),
            )
        }
    }

    override suspend fun fetchProblemById(problemId: Int): ProblemSummaryDto {
        val payload = httpTextClient.get(endpoint(apiBaseUrl, "/problems/$problemId"))
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(ProblemSummaryDto.serializer(), payload)
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

    override suspend fun prepareCreateProblemOnChain(request: CreateProblemRequestDto): PrepareCreateProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(CreateProblemRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(endpoint(apiBaseUrl, "/problems/create/prepare"), body)
        return json.decodeFromString(PrepareCreateProblemResponseDto.serializer(), payload)
    }

    override suspend fun confirmCreateProblemOnChain(request: ConfirmCreateProblemRequestDto): CreateProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(ConfirmCreateProblemRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(endpoint(apiBaseUrl, "/problems/create/confirm"), body)
        return json.decodeFromString(CreateProblemResponseDto.serializer(), payload)
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
                memoryUsedKb = resultObj.optionalInt("memoryUsedKb"),
                message = resultObj.optionalString("message"),
            )
        }
        return ValidateCreateProblemResponseDto(
            total = obj.requiredInt("total"),
            successful = obj.requiredInt("successful"),
            allSuccessful = obj.requiredBoolean("allSuccessful"),
            results = results,
            runtimeMs = obj.optionalInt("runtimeMs") ?: 0,
            memoryUsedKb = obj.optionalInt("memoryUsedKb"),
            sandboxNodeId = obj.optionalString("sandboxNodeId"),
            sandboxImageHash = obj.optionalString("sandboxImageHash"),
            sandboxRunHash = obj.optionalString("sandboxRunHash"),
        )
    }

    override suspend fun cancelCreateProblemValidation(runId: String) {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(
            CancelCreateProblemValidationRequestDto.serializer(),
            CancelCreateProblemValidationRequestDto(validationRunId = runId),
        )
        httpTextClient.postJson(endpoint(apiBaseUrl, "/problems/create/validate/cancel"), body)
    }

    override suspend fun prepareJoinProblemOnChain(problemId: Int): PrepareJoinProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val payload = httpTextClient.postJson(
            endpoint(apiBaseUrl, "/problems/$problemId/join/prepare"),
            "{}",
        )
        return json.decodeFromString(PrepareJoinProblemResponseDto.serializer(), payload)
    }

    override suspend fun confirmJoinProblemOnChain(
        problemId: Int,
        request: ConfirmJoinProblemRequestDto,
    ): JoinProblemResponseDto {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(ConfirmJoinProblemRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(
            endpoint(apiBaseUrl, "/problems/$problemId/join/confirm"),
            body,
        )
        return json.decodeFromString(JoinProblemResponseDto.serializer(), payload)
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
                memoryUsedKb = resultObj.optionalInt("memoryUsedKb"),
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
            runtimeMs = obj.optionalInt("runtimeMs") ?: 0,
            memoryUsedKb = obj.optionalInt("memoryUsedKb"),
            results = results,
            sandboxNodeId = obj.optionalString("sandboxNodeId"),
            sandboxImageHash = obj.optionalString("sandboxImageHash"),
            sandboxRunHash = obj.optionalString("sandboxRunHash"),
        )
    }

    override suspend fun submitProblemCode(
        problemId: Int,
        request: RunProblemRequestDto,
    ): SubmissionJudgeJobDto {
        val json = Json { ignoreUnknownKeys = true }
        val body = json.encodeToString(RunProblemRequestDto.serializer(), request)
        val payload = httpTextClient.postJson(
            endpoint(apiBaseUrl, "/problems/$problemId/submit"),
            body,
        )
        return json.decodeFromString(SubmissionJudgeJobDto.serializer(), payload)
    }

    override suspend fun fetchSubmissionJudgeJob(jobId: Long): SubmissionJudgeJobDto {
        val json = Json { ignoreUnknownKeys = true }
        val payload = httpTextClient.get(
            endpoint(apiBaseUrl, "/problems/submission-jobs/$jobId"),
        )
        return json.decodeFromString(SubmissionJudgeJobDto.serializer(), payload)
    }

    override suspend fun retrySubmissionJudgeJob(jobId: Long): SubmissionJudgeJobDto {
        val json = Json { ignoreUnknownKeys = true }
        val payload = httpTextClient.postJson(
            endpoint(apiBaseUrl, "/problems/submission-jobs/$jobId/retry"),
            "{}",
        )
        return json.decodeFromString(SubmissionJudgeJobDto.serializer(), payload)
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

private fun JsonObject.requiredObject(name: String): JsonObject {
    return this[name]?.jsonObject
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

private fun JsonObject.optionalLong(name: String): Long? {
    return this[name]?.jsonPrimitive?.longOrNull
}

private fun JsonObject.optionalInt(name: String): Int? {
    return this[name]?.jsonPrimitive?.intOrNull
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    return this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
        ?: error("Missing or invalid '$name' in backend response.")
}
