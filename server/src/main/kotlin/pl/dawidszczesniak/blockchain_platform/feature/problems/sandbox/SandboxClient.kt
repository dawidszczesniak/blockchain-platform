package pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemValidationCancelledException
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SandboxRunCancellation

internal data class SandboxRunInput(
    val id: Long,
    val order: Int,
    val inputData: String,
    val expectedOutput: String,
    val validatorCode: String,
    val validatorLanguage: String,
    val timeoutMs: Int,
    val memoryLimitMb: Int,
)

internal data class SandboxRunOutput(
    val nodeId: String,
    val nodeUrl: String,
    val imageHash: String?,
    val runHash: String,
    val resultHash: String?,
    val suiteExecutionTimeMs: Int?,
    val attestationPayloadHash: String?,
    val attestationSignature: String?,
    val attestationScheme: String?,
    val executedAt: String?,
    val results: List<SandboxRunTestOutput>,
)

internal data class SandboxRunTestOutput(
    val id: Long,
    val order: Int,
    val status: String,
    val output: String?,
    val passed: Boolean?,
    val executionTimeMs: Int,
    val memoryUsedKb: Int? = null,
    val message: String?,
)

internal data class SandboxNodeRunOutput(
    val nodeId: String?,
    val nodeUrl: String,
    val imageHash: String?,
    val runHash: String?,
    val resultHash: String?,
    val suiteExecutionTimeMs: Int?,
    val attestationPayloadHash: String?,
    val attestationSignature: String?,
    val attestationScheme: String?,
    val executedAt: String?,
    val results: List<SandboxRunTestOutput>,
    val errorMessage: String?,
)

internal interface SandboxClient {
    fun runSolution(
        sourceCode: String,
        language: String,
        tests: List<SandboxRunInput>,
    ): SandboxRunOutput

    fun runSolutionOnAllNodes(
        sourceCode: String,
        language: String,
        tests: List<SandboxRunInput>,
        runId: String? = null,
        cancellation: SandboxRunCancellation? = null,
    ): List<SandboxNodeRunOutput>
}

internal class SandboxHttpClient(
    private val config: SandboxConfig,
) : SandboxClient {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.connectTimeoutMs))
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val rrIndex = AtomicInteger(0)

    override fun runSolution(
        sourceCode: String,
        language: String,
        tests: List<SandboxRunInput>,
    ): SandboxRunOutput {
        if (tests.isEmpty()) {
            error("Sandbox requires at least one test.")
        }
        val payload = json.encodeToString(
            SandboxRunRequest(
                sourceCode = sourceCode,
                language = language,
                tests = tests.map { test ->
                    SandboxRunRequestTest(
                        id = test.id,
                        order = test.order,
                        inputData = test.inputData,
                        expectedOutput = test.expectedOutput,
                        validatorCode = test.validatorCode,
                        validatorLanguage = test.validatorLanguage,
                        timeoutMs = test.timeoutMs,
                        memoryLimitMb = test.memoryLimitMb,
                    )
                },
            )
        )

        val nodes = config.nodes
        val start = Math.floorMod(rrIndex.getAndIncrement(), nodes.size)
        val orderedNodes = (0 until nodes.size).map { offset ->
            nodes[(start + offset) % nodes.size]
        }

        var lastError: Throwable? = null
        orderedNodes.forEach { node ->
            runCatching {
                callNode(node, payload)
            }.onSuccess { response ->
                if (!config.expectedImageHash.isNullOrBlank() &&
                    !response.imageHash.isNullOrBlank() &&
                    response.imageHash != config.expectedImageHash
                ) {
                    throw IllegalStateException(
                        "Sandbox image hash mismatch on node '${response.nodeId}'. Expected '${config.expectedImageHash}', got '${response.imageHash}'."
                    )
                }
                return SandboxRunOutput(
                    nodeId = response.nodeId,
                    nodeUrl = node,
                    imageHash = response.imageHash,
                    runHash = response.runHash,
                    resultHash = response.resultHash,
                    suiteExecutionTimeMs = response.suiteExecutionTimeMs,
                    attestationPayloadHash = response.attestationPayloadHash,
                    attestationSignature = response.attestationSignature,
                    attestationScheme = response.attestationScheme,
                    executedAt = response.executedAt,
                    results = response.results.map { result ->
                        SandboxRunTestOutput(
                            id = result.id,
                            order = result.order,
                            status = result.status,
                            output = result.output,
                            passed = result.passed,
                            executionTimeMs = result.executionTimeMs,
                            memoryUsedKb = result.memoryUsedKb,
                            message = result.message,
                        )
                    },
                )
            }.onFailure { error ->
                lastError = error
            }
        }
        val reason = lastError?.message?.ifBlank { null } ?: "All sandbox nodes are unavailable."
        throw IllegalStateException(reason, lastError)
    }

    override fun runSolutionOnAllNodes(
        sourceCode: String,
        language: String,
        tests: List<SandboxRunInput>,
        runId: String?,
        cancellation: SandboxRunCancellation?,
    ): List<SandboxNodeRunOutput> {
        if (tests.isEmpty()) {
            error("Sandbox requires at least one test.")
        }
        cancellation?.throwIfCancelled()
        val payload = json.encodeToString(
            SandboxRunRequest(
                runId = runId,
                sourceCode = sourceCode,
                language = language,
                tests = tests.map { test ->
                    SandboxRunRequestTest(
                        id = test.id,
                        order = test.order,
                        inputData = test.inputData,
                        expectedOutput = test.expectedOutput,
                        validatorCode = test.validatorCode,
                        validatorLanguage = test.validatorLanguage,
                        timeoutMs = test.timeoutMs,
                        memoryLimitMb = test.memoryLimitMb,
                    )
                },
            )
        )
        val nodeCalls = config.nodes.associateWith { node ->
            callNodeAsync(node, payload)
        }
        if (!runId.isNullOrBlank() && cancellation != null) {
            cancellation.registerCancellationAction {
                nodeCalls.values.forEach { future ->
                    future.cancel(true)
                }
                config.nodes.forEach { node ->
                    runCatching { cancelNode(node, runId) }
                }
            }
        }
        cancellation?.throwIfCancelled()
        return config.nodes.map { node ->
            runCatching {
                nodeCalls.getValue(node).join()
            }.fold(
                onSuccess = { response ->
                    val imageHashMismatch = !config.expectedImageHash.isNullOrBlank() &&
                        !response.imageHash.isNullOrBlank() &&
                        response.imageHash != config.expectedImageHash
                    if (imageHashMismatch) {
                        SandboxNodeRunOutput(
                            nodeId = response.nodeId,
                            nodeUrl = node,
                            imageHash = response.imageHash,
                            runHash = response.runHash,
                            resultHash = response.resultHash,
                            suiteExecutionTimeMs = response.suiteExecutionTimeMs,
                            attestationPayloadHash = response.attestationPayloadHash,
                            attestationSignature = response.attestationSignature,
                            attestationScheme = response.attestationScheme,
                            executedAt = response.executedAt,
                            results = emptyList(),
                            errorMessage = "Sandbox image hash mismatch on node '${response.nodeId}'.",
                        )
                    } else {
                        SandboxNodeRunOutput(
                            nodeId = response.nodeId,
                            nodeUrl = node,
                            imageHash = response.imageHash,
                            runHash = response.runHash,
                            resultHash = response.resultHash,
                            suiteExecutionTimeMs = response.suiteExecutionTimeMs,
                            attestationPayloadHash = response.attestationPayloadHash,
                            attestationSignature = response.attestationSignature,
                            attestationScheme = response.attestationScheme,
                            executedAt = response.executedAt,
                            results = response.results.map { result ->
                                SandboxRunTestOutput(
                                    id = result.id,
                                    order = result.order,
                                    status = result.status,
                                    output = result.output,
                                    passed = result.passed,
                                    executionTimeMs = result.executionTimeMs,
                                    memoryUsedKb = result.memoryUsedKb,
                                    message = result.message,
                                )
                            },
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    val rootCause = when (error) {
                        is CompletionException -> error.cause ?: error
                        else -> error
                    }
                    if (rootCause is CancellationException || cancellation?.isCancelled == true) {
                        throw CreateProblemValidationCancelledException()
                    }
                    SandboxNodeRunOutput(
                        nodeId = null,
                        nodeUrl = node,
                        imageHash = null,
                        runHash = null,
                        resultHash = null,
                        suiteExecutionTimeMs = null,
                        attestationPayloadHash = null,
                        attestationSignature = null,
                        attestationScheme = null,
                        executedAt = null,
                        results = emptyList(),
                        errorMessage = rootCause.message?.ifBlank { null } ?: "Sandbox node call failed.",
                    )
                },
            )
        }
    }

    private fun callNodeAsync(nodeBaseUrl: String, payload: String): CompletableFuture<SandboxRunResponse> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$nodeBaseUrl/run"))
            .timeout(Duration.ofMillis(config.requestTimeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() !in 200..299) {
                    val body = response.body().trim().ifBlank { "HTTP ${response.statusCode()}" }
                    throw IllegalStateException("Sandbox node '$nodeBaseUrl' failed: $body")
                }
                json.decodeFromString<SandboxRunResponse>(response.body())
            }
    }

    private fun callNode(nodeBaseUrl: String, payload: String): SandboxRunResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$nodeBaseUrl/run"))
            .timeout(Duration.ofMillis(config.requestTimeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            val body = response.body().trim().ifBlank { "HTTP ${response.statusCode()}" }
            throw IllegalStateException("Sandbox node '$nodeBaseUrl' failed: $body")
        }
        return json.decodeFromString(SandboxRunResponse.serializer(), response.body())
    }

    private fun cancelNode(nodeBaseUrl: String, runId: String) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$nodeBaseUrl/cancel"))
            .timeout(Duration.ofMillis(config.connectTimeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(SandboxCancelRequest(runId = runId))))
            .build()
        runCatching {
            http.send(request, HttpResponse.BodyHandlers.discarding())
        }
    }
}

@Serializable
private data class SandboxRunRequest(
    val runId: String? = null,
    val sourceCode: String,
    val language: String,
    val tests: List<SandboxRunRequestTest>,
)

@Serializable
private data class SandboxCancelRequest(
    val runId: String,
)

@Serializable
private data class SandboxRunRequestTest(
    val id: Long,
    val order: Int,
    val inputData: String,
    val expectedOutput: String,
    val validatorCode: String,
    val validatorLanguage: String,
    val timeoutMs: Int,
    val memoryLimitMb: Int,
)

@Serializable
private data class SandboxRunResponse(
    val nodeId: String,
    val imageHash: String? = null,
    val runHash: String,
    val resultHash: String? = null,
    val suiteExecutionTimeMs: Int? = null,
    val attestationPayloadHash: String? = null,
    val attestationSignature: String? = null,
    val attestationScheme: String? = null,
    val executedAt: String? = null,
    val results: List<SandboxRunResponseTest>,
)

@Serializable
private data class SandboxRunResponseTest(
    val id: Long,
    val order: Int,
    val status: String,
    val output: String? = null,
    val passed: Boolean? = null,
    val executionTimeMs: Int = 0,
    val memoryUsedKb: Int? = null,
    val message: String? = null,
)
