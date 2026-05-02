package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttestationStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.JudgeLanguages
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.bytes32HashHex
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionTest
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionNodeAttestationDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionPersistedTestResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionRecordDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxNodeRunOutput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunTestOutput

internal class SubmitProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface SubmitProblemCodeUseCase {
    operator fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmitProblemResponseDto
}

internal sealed interface SubmissionJudgeOutcome {
    data class Accepted(val response: SubmitProblemResponseDto) : SubmissionJudgeOutcome

    data class Rejected(
        val preview: RunProblemResponseDto,
        val message: String,
    ) : SubmissionJudgeOutcome
}

internal interface SubmissionJudgeService {
    fun judge(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmissionJudgeOutcome
}

internal class SubmitProblemCodeUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val sandboxClient: SandboxClient,
    private val sandboxConfig: SandboxConfig,
    private val contractConfig: BlockchainPlatformContractConfig,
    private val contractClient: BlockchainPlatformContractClient,
) : SubmitProblemCodeUseCase, SubmissionJudgeService {
    override fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmitProblemResponseDto {
        return when (val outcome = judge(userId, problemId, request)) {
            is SubmissionJudgeOutcome.Accepted -> outcome.response
            is SubmissionJudgeOutcome.Rejected -> throw SubmitProblemValidationException(outcome.message)
        }
    }

    override fun judge(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmissionJudgeOutcome {
        val languageProfile = runCatching { JudgeLanguages.requireSupported(request.language) }
            .getOrElse { error ->
                throw SubmitProblemValidationException(error.message ?: "Unsupported language.")
            }
        val sourceCode = request.sourceCode.trim()
        if (sourceCode.isBlank()) {
            throw SubmitProblemValidationException("Source code cannot be empty.")
        }
        if (sourceCode.length > MAX_SOURCE_CODE_CHARS) {
            throw SubmitProblemValidationException(
                "Source code is too long. Max length is $MAX_SOURCE_CODE_CHARS characters."
            )
        }

        val context = runCatching {
            repository.fetchExecutionContextForUser(
                userId = userId,
                problemId = problemId,
            )
        }.getOrElse { error ->
            throw SubmitProblemValidationException(
                error.message?.ifBlank { "Cannot submit this solution." } ?: "Cannot submit this solution."
            )
        }

        val sandboxInputs = context.tests.map(languageProfile::applyTo)
        val nodeRuns = sandboxClient.runSolutionOnAllNodes(
            sourceCode = sourceCode,
            language = languageProfile.id,
            tests = sandboxInputs,
        )
        if (nodeRuns.isEmpty()) {
            throw SubmitProblemValidationException("No sandbox nodes are configured.")
        }

        val evaluatedNodes = nodeRuns.map { node ->
            evaluateNodeRun(node)
        }

        val validNodeGroups = evaluatedNodes
            .filter { it.isValid && !it.resultHash.isNullOrBlank() }
            .groupBy { it.resultHash!! to (it.imageHash ?: "") }
        val consensusEntry = validNodeGroups.entries.maxByOrNull { it.value.size }
            ?: throw SubmitProblemValidationException("No valid node attestation was returned by the sandbox cluster.")
        val consensusReached = consensusEntry.value.size
        if (consensusReached < sandboxConfig.requiredConsensus) {
            throw SubmitProblemValidationException(
                "Sandbox consensus not reached: got $consensusReached/${sandboxConfig.requiredConsensus} valid matching nodes."
            )
        }

        val consensusResultHash = consensusEntry.key.first
        val consensusImageHash = consensusEntry.key.second.takeIf { it.isNotBlank() }
        val consensusNodeIds = consensusEntry.value.map { it.nodeId }.toSet()
        val consensusNode = consensusEntry.value.first()

        val executionByTestId = consensusNode.results.associateBy { it.id }
        val evaluatedTests = context.tests.map { test ->
            val execution = executionByTestId[test.id]
            if (execution == null) {
                test.toMissingSubmissionResult()
            } else {
                test.evaluateSubmissionTest(execution)
            }
        }

        val passedCount = evaluatedTests.count { it.apiResult.passed }
        val allPassed = passedCount == evaluatedTests.size
        // Correctness requires full consensus; ranking metrics use median to reduce node jitter.
        val runtimeMs = medianInt(
            consensusEntry.value.mapNotNull { node ->
                node.suiteExecutionTimeMs ?: node.results.maxOfOrNull { it.executionTimeMs }
            }
        ) ?: 0
        val reportedMemoryUsedKb = medianInt(
            consensusEntry.value.mapNotNull { node ->
                node.results.mapNotNull { it.memoryUsedKb }.maxOrNull()
            }
        )
        if (!allPassed) {
            val failedCount = evaluatedTests.size - passedCount
            return SubmissionJudgeOutcome.Rejected(
                preview = RunProblemResponseDto(
                    total = evaluatedTests.size,
                    passed = passedCount,
                    allPassed = false,
                    runtimeMs = runtimeMs,
                    memoryUsedKb = reportedMemoryUsedKb,
                    results = evaluatedTests.map { it.apiResult },
                    sandboxNodeId = consensusNode.nodeId,
                    sandboxImageHash = consensusNode.imageHash,
                    sandboxRunHash = consensusNode.runHash,
                ),
                message = "Submission blocked: $failedCount/${evaluatedTests.size} tests did not pass. Solution was not submitted.",
            )
        }
        val memoryUsedKb = reportedMemoryUsedKb
            ?: throw SubmitProblemValidationException("Submission blocked: sandbox memory usage was not reported.")
        val submissionStatus = SubmissionAttemptStatus.Accepted
        val codeHash = sha256Hex(sourceCode)
        val testsHash = hashExecutionTests(context)
        val sandboxImageHash = bytes32HashHex(consensusImageHash)
        val commitmentHash = buildCommitmentHash(
            competitionId = context.onchainCompetitionId,
            participantWalletAddress = context.participantWalletAddress,
            codeHash = codeHash,
            testsHash = testsHash,
            resultHash = consensusResultHash,
            imageHash = sandboxImageHash,
            runtimeMs = runtimeMs,
            memoryUsedKb = memoryUsedKb,
            consensusNodes = consensusReached,
        )
        val submissionRecord = repository.createSubmissionRecord(
            SubmissionRecordDraft(
                problemId = problemId,
                userId = userId,
                status = submissionStatus,
                sourceCode = sourceCode,
                language = languageProfile.id,
                codeHash = codeHash,
                testsHash = testsHash,
                resultHash = consensusResultHash,
                consensusImageHash = sandboxImageHash,
                consensusNodes = consensusReached,
                commitmentHash = commitmentHash,
                runtimeMs = runtimeMs,
                memoryUsedKb = memoryUsedKb,
                testResults = evaluatedTests.map { test ->
                    SubmissionPersistedTestResult(
                        problemTestId = test.problemTestId,
                        status = test.dbStatus,
                        executionTimeMs = test.apiResult.executionTimeMs,
                        memoryUsedKb = test.apiResult.memoryUsedKb,
                        message = test.apiResult.message,
                    )
                },
                nodeAttestations = evaluatedNodes.map { node ->
                    SubmissionNodeAttestationDraft(
                        nodeId = node.nodeId,
                        nodeUrl = node.nodeUrl,
                        imageHash = node.imageHash,
                        runHash = node.runHash,
                        resultHash = node.resultHash,
                        attestationPayloadHash = node.attestationPayloadHash,
                        attestationSignature = node.attestationSignature,
                        attestationScheme = node.attestationScheme ?: DEFAULT_ATTESTATION_SCHEME,
                        isValid = node.isValid,
                        isConsensus = node.nodeId in consensusNodeIds,
                        status = node.status,
                        message = node.message,
                    )
                },
            )
        )
        val resultWrite = contractClient.recordSubmissionResult(
            SubmissionResultRecord(
                competitionId = context.onchainCompetitionId,
                submissionId = submissionRecord.submissionId,
                participantWalletAddress = context.participantWalletAddress,
                submissionHash = commitmentHash,
                codeHash = codeHash,
                testsHash = testsHash,
                resultHash = consensusResultHash,
                sandboxImageHash = sandboxImageHash,
                runtimeMs = runtimeMs,
                memoryUsedKb = memoryUsedKb,
                consensusNodes = consensusReached,
            )
        )
        if (!resultWrite.success || resultWrite.txHash.isNullOrBlank()) {
            val error = resultWrite.error?.ifBlank { null }
                ?: "Submission result was not recorded on-chain."
            repository.markSubmissionResultFailed(
                submissionId = submissionRecord.submissionId,
                error = error,
            )
            throw SubmitProblemValidationException(error)
        }
        repository.markSubmissionResultRecorded(
            submissionId = submissionRecord.submissionId,
            proxyAddress = contractConfig.proxyAddress,
            txHash = resultWrite.txHash,
            recordedAt = Instant.now(),
        )

        return SubmissionJudgeOutcome.Accepted(
            response = SubmitProblemResponseDto(
                submissionId = submissionRecord.submissionId,
                total = evaluatedTests.size,
                passed = passedCount,
                allPassed = allPassed,
                runtimeMs = runtimeMs,
                memoryUsedKb = memoryUsedKb,
                results = evaluatedTests.map { it.apiResult },
                consensusRequired = sandboxConfig.requiredConsensus,
                consensusReached = consensusReached,
                sandboxImageHash = sandboxImageHash,
                sandboxResultHash = consensusResultHash,
                commitmentHash = commitmentHash,
                proxyAddress = contractConfig.proxyAddress,
                txHash = resultWrite.txHash,
                explorerUrl = contractConfig.explorerTxUrl(resultWrite.txHash),
            )
        )
    }

    private fun evaluateNodeRun(node: SandboxNodeRunOutput): EvaluatedNodeRun {
        val nodeId = node.nodeId?.trim().orEmpty().ifBlank { "unknown-node" }
        if (!node.errorMessage.isNullOrBlank()) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = node.resultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = emptyList(),
                status = SubmissionAttestationStatus.Error,
                isValid = false,
                message = node.errorMessage,
            )
        }
        val computedResultHash = computeSandboxResultHash(node.results)
        if (computedResultHash != node.resultHash) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = node.resultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Node result hash does not match recomputed value.",
            )
        }

        val imageHash = node.imageHash.orEmpty()
        val runHash = node.runHash.orEmpty()
        val executedAt = node.executedAt.orEmpty()
        if (imageHash.isBlank() || runHash.isBlank() || executedAt.isBlank()) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Node attestation metadata is incomplete.",
            )
        }

        val scheme = node.attestationScheme?.trim()?.lowercase().orEmpty()
        if (scheme != DEFAULT_ATTESTATION_SCHEME) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Unsupported attestation scheme '$scheme'.",
            )
        }

        val sharedSecret = sandboxConfig.nodeAttestationSecrets[nodeId].orEmpty()
        if (sharedSecret.isBlank()) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "No shared attestation secret configured for node '$nodeId'.",
            )
        }

        val expectedPayloadHash = computeAttestationPayloadHash(
            nodeId = nodeId,
            imageHash = imageHash,
            runHash = runHash,
            resultHash = computedResultHash,
            executedAt = executedAt,
        )
        if (expectedPayloadHash != node.attestationPayloadHash) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Attestation payload hash mismatch.",
            )
        }

        val expectedSignature = hmacSha256Hex(
            secret = sharedSecret,
            payloadHash = expectedPayloadHash,
        )
        if (!constantTimeEquals(expectedSignature, node.attestationSignature.orEmpty())) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Attestation signature mismatch.",
            )
        }

        return EvaluatedNodeRun(
            nodeId = nodeId,
            nodeUrl = node.nodeUrl,
            imageHash = node.imageHash,
            runHash = node.runHash,
            resultHash = computedResultHash,
            suiteExecutionTimeMs = node.suiteExecutionTimeMs,
            attestationPayloadHash = node.attestationPayloadHash,
            attestationSignature = node.attestationSignature,
            attestationScheme = node.attestationScheme,
            results = node.results,
            status = SubmissionAttestationStatus.Ok,
            isValid = true,
            message = null,
        )
    }
}

private data class EvaluatedNodeRun(
    val nodeId: String,
    val nodeUrl: String,
    val imageHash: String?,
    val runHash: String?,
    val resultHash: String?,
    val suiteExecutionTimeMs: Int? = null,
    val attestationPayloadHash: String?,
    val attestationSignature: String?,
    val attestationScheme: String?,
    val results: List<SandboxRunTestOutput>,
    val status: SubmissionAttestationStatus,
    val isValid: Boolean,
    val message: String?,
)

private data class EvaluatedSubmissionTest(
    val problemTestId: Long,
    val dbStatus: SubmissionTestResultStatus,
    val apiResult: RunProblemTestResultDto,
)

private enum class SubmitRunStatus {
    Passed,
    Failed,
    Error,
    Timeout,
}

private fun ProblemExecutionTest.toMissingSubmissionResult(): EvaluatedSubmissionTest {
    return toSubmissionResult(
        status = SubmitRunStatus.Error,
        passed = false,
        executionTimeMs = 0,
        memoryUsedKb = null,
        actualOutput = null,
        message = "Consensus result does not contain this test.",
    )
}

private fun ProblemExecutionTest.evaluateSubmissionTest(
    execution: SandboxRunTestOutput,
): EvaluatedSubmissionTest {
    return when (execution.status.uppercase()) {
        "OK" -> {
            val fallbackPassed = when {
                execution.passed != null -> null
                expectedOutput.isNotBlank() -> {
                    normalizeOutput(execution.output ?: "") == normalizeOutput(expectedOutput)
                }
                else -> false
            }
            val passed = execution.passed ?: fallbackPassed ?: false
            val failureMessage = execution.message
                ?: if (validatorCode.isNotBlank() && execution.passed == null) {
                    "Sandbox node does not expose validator verdict for this test."
                } else {
                    "Output does not match expected value."
                }
            toSubmissionResult(
                status = if (passed) SubmitRunStatus.Passed else SubmitRunStatus.Failed,
                passed = passed,
                executionTimeMs = execution.executionTimeMs,
                memoryUsedKb = execution.memoryUsedKb,
                actualOutput = execution.output,
                message = if (passed) null else failureMessage,
            )
        }

        "TIMEOUT" -> {
            toSubmissionResult(
                status = SubmitRunStatus.Timeout,
                passed = false,
                executionTimeMs = execution.executionTimeMs,
                memoryUsedKb = execution.memoryUsedKb,
                actualOutput = null,
                message = execution.message ?: "Execution timed out.",
            )
        }

        else -> {
            toSubmissionResult(
                status = SubmitRunStatus.Error,
                passed = false,
                executionTimeMs = execution.executionTimeMs,
                memoryUsedKb = execution.memoryUsedKb,
                actualOutput = null,
                message = execution.message ?: "Sandbox execution error.",
            )
        }
    }
}

private fun ProblemExecutionTest.toSubmissionResult(
    status: SubmitRunStatus,
    passed: Boolean,
    executionTimeMs: Int,
    memoryUsedKb: Int?,
    actualOutput: String?,
    message: String?,
): EvaluatedSubmissionTest {
    val expectedOutputForDisplay = expectedOutput.takeIf { it.isNotBlank() }
    val safeMessage = when {
        isHidden && !passed && status == SubmitRunStatus.Failed -> HIDDEN_TEST_FAILED_MESSAGE
        else -> message
    }
    val apiResult = RunProblemTestResultDto(
        index = order,
        status = status.name,
        passed = passed,
        hidden = isHidden,
        executionTimeMs = executionTimeMs,
        memoryUsedKb = memoryUsedKb,
        input = if (isHidden) null else inputData,
        expectedOutput = if (isHidden) null else expectedOutputForDisplay,
        actualOutput = if (isHidden) null else actualOutput,
        message = safeMessage,
    )
    val dbStatus = when (status) {
        SubmitRunStatus.Passed -> SubmissionTestResultStatus.Passed
        SubmitRunStatus.Failed -> SubmissionTestResultStatus.Failed
        SubmitRunStatus.Error -> SubmissionTestResultStatus.Error
        SubmitRunStatus.Timeout -> SubmissionTestResultStatus.Timeout
    }
    return EvaluatedSubmissionTest(
        problemTestId = id,
        dbStatus = dbStatus,
        apiResult = apiResult,
    )
}

private fun normalizeOutput(raw: String): String {
    return raw.replace("\r\n", "\n").trimEnd()
}

private fun hashExecutionTests(context: ProblemExecutionContext): String {
    val canonical = context.tests
        .sortedBy { it.order }
        .joinToString("\n") { test ->
            "${test.id}|${test.order}|${test.inputData}|${test.expectedOutput}|${test.validatorCode}|${test.validatorLanguage}|${test.isHidden}|${test.timeoutMs}|${test.memoryLimitMb}"
        }
    return sha256Hex(canonical)
}

private fun buildCommitmentHash(
    competitionId: Long,
    participantWalletAddress: String,
    codeHash: String,
    testsHash: String,
    resultHash: String,
    imageHash: String?,
    runtimeMs: Int,
    memoryUsedKb: Int,
    consensusNodes: Int,
): String {
    val payload = buildString {
        append(competitionId)
        append('|')
        append(participantWalletAddress.lowercase())
        append('|')
        append(codeHash)
        append('|')
        append(testsHash)
        append('|')
        append(resultHash)
        append('|')
        append(imageHash.orEmpty())
        append('|')
        append(runtimeMs)
        append('|')
        append(memoryUsedKb)
        append('|')
        append(consensusNodes)
    }
    return keccakHex(payload)
}

private fun medianInt(values: List<Int>): Int? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        ((sorted[middle - 1].toLong() + sorted[middle].toLong()) / 2L).toInt()
    }
}

internal fun computeSandboxResultHash(results: List<SandboxRunTestOutput>): String {
    val canonical = results
        .sortedBy { it.order }
        .joinToString("\n") { result ->
            // Consensus must be stable across nodes, so runtime metrics stay outside the hash.
            "${result.id}|${result.order}|${result.status}|${result.output.orEmpty()}|${result.passed ?: false}|${result.message.orEmpty()}"
        }
    return sha256Hex(canonical)
}

private fun computeAttestationPayloadHash(
    nodeId: String,
    imageHash: String,
    runHash: String,
    resultHash: String,
    executedAt: String,
): String {
    val payload = "$nodeId|$imageHash|$runHash|$resultHash|$executedAt"
    return sha256Hex(payload)
}

private fun hmacSha256Hex(secret: String, payloadHash: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val digest = mac.doFinal(payloadHash.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun constantTimeEquals(expectedHex: String, actualHex: String): Boolean {
    val expected = normalizeHashHex(expectedHex).removePrefix("0x").toByteArray(Charsets.UTF_8)
    val actual = normalizeHashHex(actualHex).removePrefix("0x").toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(expected, actual)
}

private fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun keccakHex(text: String): String {
    val digest = Hash.sha3(text.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun normalizeHashHex(raw: String): String {
    val trimmed = raw.trim().lowercase()
    return if (trimmed.startsWith("0x")) trimmed else "0x$trimmed"
}

private const val MAX_SOURCE_CODE_CHARS = 120_000
private const val HIDDEN_TEST_FAILED_MESSAGE = "Hidden test failed."
private const val DEFAULT_ATTESTATION_SCHEME = "hmac-sha256"
