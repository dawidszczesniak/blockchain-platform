package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemTestCaseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxNodeRunOutput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunInput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunOutput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunTestOutput

class CreateProblemReferenceValidationServiceImplTest {
    @Test
    fun `uses all sandbox nodes and returns consensus benchmark`() {
        val secrets = mapOf(
            "sandbox-node-1" to "sandbox-secret-1",
            "sandbox-node-2" to "sandbox-secret-2",
            "sandbox-node-3" to "sandbox-secret-3",
        )
        val sandboxConfig = SandboxConfig(
            nodes = listOf("http://sandbox-node-1", "http://sandbox-node-2", "http://sandbox-node-3"),
            requestTimeoutMs = 20_000,
            connectTimeoutMs = 2_500,
            expectedImageHash = null,
            requiredConsensus = 3,
            nodeAttestationSecrets = secrets,
        )
        val sandboxClient = FakeReferenceValidationSandboxClient(
            nodeRuns = listOf(
                validNodeRun(
                    nodeId = "sandbox-node-1",
                    nodeUrl = "http://sandbox-node-1",
                    secret = secrets.getValue("sandbox-node-1"),
                    executionTimeMs = 100,
                    memoryUsedKb = 1000,
                ),
                validNodeRun(
                    nodeId = "sandbox-node-2",
                    nodeUrl = "http://sandbox-node-2",
                    secret = secrets.getValue("sandbox-node-2"),
                    executionTimeMs = 105,
                    memoryUsedKb = 1100,
                ),
                validNodeRun(
                    nodeId = "sandbox-node-3",
                    nodeUrl = "http://sandbox-node-3",
                    secret = secrets.getValue("sandbox-node-3"),
                    executionTimeMs = 300,
                    memoryUsedKb = 3000,
                ),
            )
        )
        val service = CreateProblemReferenceValidationServiceImpl(
            sandboxClient = sandboxClient,
            sandboxConsensusEvaluator = SandboxConsensusEvaluator(sandboxConfig),
        )

        val result = service.validateReferenceSolution(
            referenceSolutionLanguage = "kotlin",
            referenceSolutionCode = "fun solve(input: String): String = input.trim()",
            testCases = listOf(
                CreateProblemTestCaseDto(
                    inputData = "5",
                    isHidden = false,
                )
            ),
            requireDeterminism = false,
        )

        assertEquals(1, sandboxClient.runSolutionOnAllNodesCalls)
        assertEquals(3, result.evidence.consensusNodes)
        assertEquals(105, result.evidence.runtimeMs)
        assertEquals(1100, result.evidence.memoryUsedKb)
        assertEquals("5", result.tests.single().output)
    }

    @Test
    fun `uses single cluster pass and same median aggregation for creator validation`() {
        val secrets = mapOf(
            "sandbox-node-1" to "sandbox-secret-1",
            "sandbox-node-2" to "sandbox-secret-2",
            "sandbox-node-3" to "sandbox-secret-3",
        )
        val sandboxConfig = SandboxConfig(
            nodes = listOf("http://sandbox-node-1", "http://sandbox-node-2", "http://sandbox-node-3"),
            requestTimeoutMs = 20_000,
            connectTimeoutMs = 2_500,
            expectedImageHash = null,
            requiredConsensus = 3,
            nodeAttestationSecrets = secrets,
        )
        val sandboxClient = FakeReferenceValidationSandboxClient(
            nodeRuns = listOf(
                validNodeRunWithResults(
                    nodeId = "sandbox-node-1",
                    nodeUrl = "http://sandbox-node-1",
                    secret = secrets.getValue("sandbox-node-1"),
                    results = listOf(
                        sandboxTestResult(id = 1L, order = 1, executionTimeMs = 82, memoryUsedKb = 900),
                        sandboxTestResult(id = 2L, order = 2, executionTimeMs = 90, memoryUsedKb = 1000, output = "7"),
                    ),
                    suiteExecutionTimeMs = 172,
                ),
                validNodeRunWithResults(
                    nodeId = "sandbox-node-2",
                    nodeUrl = "http://sandbox-node-2",
                    secret = secrets.getValue("sandbox-node-2"),
                    results = listOf(
                        sandboxTestResult(id = 1L, order = 1, executionTimeMs = 97, memoryUsedKb = 1000),
                        sandboxTestResult(id = 2L, order = 2, executionTimeMs = 105, memoryUsedKb = 1100, output = "7"),
                    ),
                    suiteExecutionTimeMs = 202,
                ),
                validNodeRunWithResults(
                    nodeId = "sandbox-node-3",
                    nodeUrl = "http://sandbox-node-3",
                    secret = secrets.getValue("sandbox-node-3"),
                    results = listOf(
                        sandboxTestResult(id = 1L, order = 1, executionTimeMs = 142, memoryUsedKb = 1100),
                        sandboxTestResult(id = 2L, order = 2, executionTimeMs = 150, memoryUsedKb = 1200, output = "7"),
                    ),
                    suiteExecutionTimeMs = 292,
                ),
            )
        )
        val service = CreateProblemReferenceValidationServiceImpl(
            sandboxClient = sandboxClient,
            sandboxConsensusEvaluator = SandboxConsensusEvaluator(sandboxConfig),
        )

        val result = service.validateReferenceSolution(
            referenceSolutionLanguage = "kotlin",
            referenceSolutionCode = "fun solve(input: String): String = input.trim()",
            testCases = listOf(
                CreateProblemTestCaseDto(
                    inputData = "5",
                    isHidden = false,
                ),
                CreateProblemTestCaseDto(
                    inputData = "7",
                    isHidden = false,
                ),
            ),
            requireDeterminism = true,
        )

        assertEquals(1, sandboxClient.runSolutionOnAllNodesCalls)
        assertEquals(202, result.evidence.runtimeMs)
        assertEquals(1100, result.evidence.memoryUsedKb)
        assertEquals(97, result.tests[0].executionTimeMs)
        assertEquals(105, result.tests[1].executionTimeMs)
    }
}

private class FakeReferenceValidationSandboxClient(
    private val nodeRuns: List<SandboxNodeRunOutput>,
) : SandboxClient {
    var runSolutionOnAllNodesCalls: Int = 0

    override fun runSolution(
        sourceCode: String,
        language: String,
        tests: List<SandboxRunInput>,
    ): SandboxRunOutput = error("Single-node sandbox run should not be used here.")

    override fun runSolutionOnAllNodes(
        sourceCode: String,
        language: String,
        tests: List<SandboxRunInput>,
        runId: String?,
        cancellation: SandboxRunCancellation?,
    ): List<SandboxNodeRunOutput> {
        runSolutionOnAllNodesCalls += 1
        return nodeRuns
    }
}

private fun validNodeRun(
    nodeId: String,
    nodeUrl: String,
    secret: String,
    executionTimeMs: Int,
    memoryUsedKb: Int,
): SandboxNodeRunOutput {
    val results = listOf(sandboxTestResult(
        id = 1L,
        order = 1,
        executionTimeMs = executionTimeMs,
        memoryUsedKb = memoryUsedKb,
    ))
    val imageHash = "sha256:test-image"
    val runHash = "0xrun${nodeId.takeLast(1)}"
    val executedAt = "2026-05-17T12:00:00Z"
    val resultHash = computeSandboxResultHash(results)
    val payloadHash = sha256HexForCreateValidation("$nodeId|$imageHash|$runHash|$resultHash|$executedAt")
    val signature = hmacSha256HexForCreateValidation(secret, payloadHash)
    return SandboxNodeRunOutput(
        nodeId = nodeId,
        nodeUrl = nodeUrl,
        imageHash = imageHash,
        runHash = runHash,
        resultHash = resultHash,
        suiteExecutionTimeMs = executionTimeMs,
        attestationPayloadHash = payloadHash,
        attestationSignature = signature,
        attestationScheme = "hmac-sha256",
        executedAt = executedAt,
        results = results,
        errorMessage = null,
    )
}

private fun validNodeRunWithResults(
    nodeId: String,
    nodeUrl: String,
    secret: String,
    results: List<SandboxRunTestOutput>,
    suiteExecutionTimeMs: Int? = null,
): SandboxNodeRunOutput {
    val imageHash = "sha256:test-image"
    val runHash = "0xrun${nodeId.takeLast(1)}"
    val executedAt = "2026-05-17T12:00:00Z"
    val resultHash = computeSandboxResultHash(results)
    val payloadHash = sha256HexForCreateValidation("$nodeId|$imageHash|$runHash|$resultHash|$executedAt")
    val signature = hmacSha256HexForCreateValidation(secret, payloadHash)
    return SandboxNodeRunOutput(
        nodeId = nodeId,
        nodeUrl = nodeUrl,
        imageHash = imageHash,
        runHash = runHash,
        resultHash = resultHash,
        suiteExecutionTimeMs = suiteExecutionTimeMs,
        attestationPayloadHash = payloadHash,
        attestationSignature = signature,
        attestationScheme = "hmac-sha256",
        executedAt = executedAt,
        results = results,
        errorMessage = null,
    )
}

private fun sandboxTestResult(
    id: Long,
    order: Int,
    executionTimeMs: Int,
    memoryUsedKb: Int,
    output: String = "5",
): SandboxRunTestOutput {
    return SandboxRunTestOutput(
        id = id,
        order = order,
        status = "OK",
        output = output,
        passed = true,
        executionTimeMs = executionTimeMs,
        memoryUsedKb = memoryUsedKb,
        message = null,
    )
}

private fun hmacSha256HexForCreateValidation(secret: String, payloadHash: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val digest = mac.doFinal(payloadHash.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun sha256HexForCreateValidation(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}
