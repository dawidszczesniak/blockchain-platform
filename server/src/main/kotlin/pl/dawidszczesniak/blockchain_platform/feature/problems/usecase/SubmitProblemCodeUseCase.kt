package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.db.AnchorBatchStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAnchorStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttestationStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.anchor.AnchorConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.anchor.BlockchainAnchorClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.anchor.MerkleTreeBuilder
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.PendingSubmissionAnchorRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionTest
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionAnchorDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionNodeAttestationDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionPersistedTestResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionRecordDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxNodeRunOutput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunInput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunTestOutput

internal class SubmitProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface SubmitProblemCodeUseCase {
    operator fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmitProblemResponseDto
}

internal class SubmitProblemCodeUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val sandboxClient: SandboxClient,
    private val sandboxConfig: SandboxConfig,
    private val anchorConfig: AnchorConfig,
    private val blockchainAnchorClient: BlockchainAnchorClient,
) : SubmitProblemCodeUseCase {
    override fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmitProblemResponseDto {
        val language = request.language.trim().lowercase()
        if (language != SUPPORTED_LANGUAGE) {
            throw SubmitProblemValidationException("Only Kotlin language is supported.")
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

        val sandboxInputs = context.tests.map { test ->
            SandboxRunInput(
                id = test.id,
                order = test.order,
                inputData = test.inputData,
                expectedOutput = test.expectedOutput,
                validatorCode = test.validatorCode,
                validatorLanguage = test.validatorLanguage,
                timeoutMs = test.timeoutMs,
                memoryLimitMb = test.memoryLimitMb,
            )
        }
        val nodeRuns = sandboxClient.runSolutionOnAllNodes(
            sourceCode = sourceCode,
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
        val submissionStatus = if (allPassed) {
            SubmissionAttemptStatus.Accepted
        } else {
            SubmissionAttemptStatus.Rejected
        }
        val codeHash = sha256Hex(sourceCode)
        val testsHash = hashExecutionTests(context)
        val commitmentHash = buildCommitmentHash(
            problemId = problemId,
            userId = userId,
            codeHash = codeHash,
            testsHash = testsHash,
            resultHash = consensusResultHash,
            imageHash = consensusImageHash,
        )
        val initialAnchor = if (anchorConfig.enabled) {
            SubmissionAnchorDraft(status = SubmissionAnchorStatus.Pending)
        } else {
            SubmissionAnchorDraft(status = SubmissionAnchorStatus.Disabled)
        }

        val submissionRecord = repository.createSubmissionRecord(
            SubmissionRecordDraft(
                problemId = problemId,
                userId = userId,
                status = submissionStatus,
                sourceCode = sourceCode,
                language = language,
                codeHash = codeHash,
                testsHash = testsHash,
                resultHash = consensusResultHash,
                consensusImageHash = consensusImageHash,
                consensusNodes = consensusReached,
                commitmentHash = commitmentHash,
                testResults = evaluatedTests.map { test ->
                    SubmissionPersistedTestResult(
                        problemTestId = test.problemTestId,
                        status = test.dbStatus,
                        executionTimeMs = test.apiResult.executionTimeMs,
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
                anchor = initialAnchor,
            )
        )

        val finalAnchor = if (anchorConfig.enabled) {
            anchorPendingSubmissions(submissionRecord.submissionId)
        } else {
            initialAnchor
        }

        return SubmitProblemResponseDto(
            submissionId = submissionRecord.submissionId,
            total = evaluatedTests.size,
            passed = passedCount,
            allPassed = allPassed,
            results = evaluatedTests.map { it.apiResult },
            consensusRequired = sandboxConfig.requiredConsensus,
            consensusReached = consensusReached,
            sandboxImageHash = consensusImageHash,
            sandboxResultHash = consensusResultHash,
            commitmentHash = commitmentHash,
            anchorStatus = finalAnchor.status.name,
            anchorBatchId = finalAnchor.batchId,
            anchorMerkleRoot = finalAnchor.merkleRoot,
            anchorProof = finalAnchor.merkleProof,
            anchorTxHash = finalAnchor.txHash,
            anchorExplorerUrl = anchorConfig.explorerTxUrl(finalAnchor.txHash),
            anchorError = finalAnchor.error,
        )
    }

    private fun anchorPendingSubmissions(currentSubmissionId: Long): SubmissionAnchorDraft {
        val pending = repository.fetchPendingSubmissionAnchors(anchorConfig.batchSize)
        if (pending.isEmpty()) {
            return SubmissionAnchorDraft(status = SubmissionAnchorStatus.Pending)
        }
        val sortedPending = pending.sortedBy { it.submissionId }
        val currentIncluded = sortedPending.any { it.submissionId == currentSubmissionId }
        val merkleTree = runCatching {
            MerkleTreeBuilder.buildFromLeaves(sortedPending.map(PendingSubmissionAnchorRecord::commitmentHash))
        }.getOrElse { error ->
            return SubmissionAnchorDraft(
                status = SubmissionAnchorStatus.Pending,
                error = error.message?.ifBlank { null } ?: "Failed to build Merkle tree.",
            )
        }
        val proofBySubmissionId = sortedPending.indices.associate { index ->
            sortedPending[index].submissionId to merkleTree.proofsByIndex[index].orEmpty()
        }
        val firstSubmissionId = sortedPending.first().submissionId
        val lastSubmissionId = sortedPending.last().submissionId

        val txResult = blockchainAnchorClient.anchorBatch(
            rootHash = merkleTree.rootHash,
            batchId = lastSubmissionId,
            fromSubmissionId = firstSubmissionId,
            toSubmissionId = lastSubmissionId,
            leavesCount = sortedPending.size,
        )
        val anchoredAt = if (txResult.anchored) Instant.now() else null
        val batchStatus = if (txResult.anchored) AnchorBatchStatus.Anchored else AnchorBatchStatus.Failed
        val submissionStatus = if (txResult.anchored) {
            SubmissionAnchorStatus.Anchored
        } else {
            SubmissionAnchorStatus.Pending
        }
        val createdBatch = repository.createAnchorBatch(
            rootHash = merkleTree.rootHash,
            submissionIds = sortedPending.map { it.submissionId },
            status = batchStatus,
            txHash = txResult.txHash,
            chainId = anchorConfig.chainId,
            contractAddress = anchorConfig.contractAddress,
            failureReason = txResult.error,
            anchoredAt = anchoredAt,
        )
        repository.updateSubmissionAnchors(
            submissionIds = createdBatch.submissionIds,
            status = submissionStatus,
            batchId = createdBatch.batchId,
            merkleRoot = createdBatch.rootHash,
            proofBySubmission = proofBySubmissionId,
            txHash = txResult.txHash,
            error = txResult.error,
            anchoredAt = anchoredAt,
        )

        if (!currentIncluded) {
            return SubmissionAnchorDraft(
                status = SubmissionAnchorStatus.Pending,
                error = "Submission queued for the next anchor batch.",
            )
        }
        val proof = proofBySubmissionId[currentSubmissionId].orEmpty()
        return SubmissionAnchorDraft(
            status = submissionStatus,
            batchId = createdBatch.batchId,
            merkleRoot = createdBatch.rootHash,
            merkleProof = proof,
            txHash = txResult.txHash,
            error = txResult.error,
            anchoredAt = anchoredAt,
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
                actualOutput = execution.output,
                message = if (passed) null else failureMessage,
            )
        }

        "TIMEOUT" -> {
            toSubmissionResult(
                status = SubmitRunStatus.Timeout,
                passed = false,
                executionTimeMs = execution.executionTimeMs,
                actualOutput = null,
                message = execution.message ?: "Execution timed out.",
            )
        }

        else -> {
            toSubmissionResult(
                status = SubmitRunStatus.Error,
                passed = false,
                executionTimeMs = execution.executionTimeMs,
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
    problemId: Int,
    userId: Long,
    codeHash: String,
    testsHash: String,
    resultHash: String,
    imageHash: String?,
): String {
    val nonce = ByteArray(16).also { bytes ->
        secureRandom.nextBytes(bytes)
    }
    val payload = buildString {
        append(problemId)
        append('|')
        append(userId)
        append('|')
        append(codeHash)
        append('|')
        append(testsHash)
        append('|')
        append(resultHash)
        append('|')
        append(imageHash.orEmpty())
        append('|')
        append(Instant.now().toEpochMilli())
        append('|')
        append(Numeric.toHexStringNoPrefix(nonce))
    }
    return keccakHex(payload)
}

private fun computeSandboxResultHash(results: List<SandboxRunTestOutput>): String {
    val canonical = results
        .sortedBy { it.order }
        .joinToString("\n") { result ->
            "${result.id}|${result.order}|${result.status}|${result.output.orEmpty()}|${result.passed ?: false}|${result.executionTimeMs}|${result.message.orEmpty()}"
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

private val secureRandom = SecureRandom()
private const val SUPPORTED_LANGUAGE = "kotlin"
private const val MAX_SOURCE_CODE_CHARS = 120_000
private const val HIDDEN_TEST_FAILED_MESSAGE = "Hidden test failed."
private const val DEFAULT_ATTESTATION_SCHEME = "hmac-sha256"
