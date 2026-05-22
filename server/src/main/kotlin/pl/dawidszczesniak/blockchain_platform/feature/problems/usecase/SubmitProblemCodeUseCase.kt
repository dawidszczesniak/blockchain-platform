package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import java.time.Instant
import org.slf4j.LoggerFactory
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
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunTestOutput

private val logger = LoggerFactory.getLogger("SubmitProblemCodeUseCase")

internal class SubmitProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal class SubmissionReceiptTimeoutException(
    val submissionId: Long,
    val partialResponse: SubmitProblemResponseDto,
    message: String,
) : IllegalStateException(message)

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
    fun judge(
        userId: Long,
        problemId: Int,
        request: RunProblemRequestDto,
        reportStatus: (String) -> Unit = {},
    ): SubmissionJudgeOutcome
}

internal interface SubmissionReceiptRetryService {
    fun retryPendingReceipt(
        userId: Long,
        problemId: Int,
        submissionId: Long,
        partialResponse: SubmitProblemResponseDto,
        reportStatus: (String) -> Unit = {},
    ): SubmitProblemResponseDto
}

internal class SubmitProblemCodeUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val sandboxClient: SandboxClient,
    private val sandboxConfig: SandboxConfig,
    private val contractConfig: BlockchainPlatformContractConfig,
    private val contractClient: BlockchainPlatformContractClient,
    private val sandboxConsensusEvaluator: SandboxConsensusEvaluator = SandboxConsensusEvaluator(sandboxConfig),
) : SubmitProblemCodeUseCase, SubmissionJudgeService, SubmissionReceiptRetryService {
    override fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmitProblemResponseDto {
        return when (val outcome = judge(userId, problemId, request)) {
            is SubmissionJudgeOutcome.Accepted -> outcome.response
            is SubmissionJudgeOutcome.Rejected -> throw SubmitProblemValidationException(outcome.message)
        }
    }

    override fun judge(
        userId: Long,
        problemId: Int,
        request: RunProblemRequestDto,
        reportStatus: (String) -> Unit,
    ): SubmissionJudgeOutcome {
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
        logger.info(
            "Starting submission evaluation for problemId={}, userId={}, tests={}, language={}.",
            problemId,
            userId,
            sandboxInputs.size,
            languageProfile.id,
        )
        reportStatus("Uruchamianie testów na sandboxach.")
        val nodeRuns = sandboxClient.runSolutionOnAllNodes(
            sourceCode = sourceCode,
            language = languageProfile.id,
            tests = sandboxInputs,
        )
        if (nodeRuns.isEmpty()) {
            throw SubmitProblemValidationException("No sandbox nodes are configured.")
        }

        val consensus = runCatching {
            sandboxConsensusEvaluator.evaluate(nodeRuns)
        }.getOrElse { error ->
            throw SubmitProblemValidationException(
                error.message?.ifBlank { "Sandbox consensus could not be established." }
                    ?: "Sandbox consensus could not be established."
            )
        }
        val evaluatedNodes = consensus.evaluatedNodes
        val consensusReached = consensus.consensusReached
        val consensusResultHash = consensus.resultHash
        val consensusImageHash = consensus.imageHash
        val consensusNodeIds = consensus.consensusNodeIds
        val consensusNode = consensus.representativeNode

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
        val runtimeMs = consensus.runtimeMs
        val reportedMemoryUsedKb = consensus.memoryUsedKb
        logger.info(
            "Sandbox consensus established for problemId={}, userId={}: consensusReached={}/{}, runtimeMs={}, memoryUsedKb={}.",
            problemId,
            userId,
            consensusReached,
            sandboxConfig.requiredConsensus,
            runtimeMs,
            reportedMemoryUsedKb,
        )
        if (!allPassed) {
            val failedCount = evaluatedTests.size - passedCount
            logger.warn(
                "Submission blocked for problemId={}, userId={}: failedTests={}/{}.",
                problemId,
                userId,
                failedCount,
                evaluatedTests.size,
            )
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
        logger.info(
            "Persisted accepted submission draft for problemId={}, userId={}, submissionId={}.",
            problemId,
            userId,
            submissionRecord.submissionId,
        )
        val acceptedResponse = SubmitProblemResponseDto(
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
            txHash = "",
            explorerUrl = null,
        )
        return SubmissionJudgeOutcome.Accepted(
            response = recordAcceptedSubmissionResult(
                context = context,
                submissionId = submissionRecord.submissionId,
                acceptedResponse = acceptedResponse,
                codeHash = codeHash,
                testsHash = testsHash,
                resultHash = consensusResultHash,
                sandboxImageHash = sandboxImageHash,
                runtimeMs = runtimeMs,
                memoryUsedKb = memoryUsedKb,
                consensusReached = consensusReached,
                reportStatus = reportStatus,
            )
        )
    }

    override fun retryPendingReceipt(
        userId: Long,
        problemId: Int,
        submissionId: Long,
        partialResponse: SubmitProblemResponseDto,
        reportStatus: (String) -> Unit,
    ): SubmitProblemResponseDto {
        val retryContext = repository.fetchSubmissionReceiptRetryContext(submissionId)
            ?: throw SubmitProblemValidationException("Submission retry context was not found.")
        val txHash = retryContext.txHash?.takeIf { it.isNotBlank() }
            ?: throw SubmitProblemValidationException("Submission retry is not available because transaction hash is missing.")
        if (retryContext.recordedAt != null) {
            return partialResponse.copy(
                txHash = txHash,
                explorerUrl = contractConfig.explorerTxUrl(txHash),
            )
        }
        reportStatus("Ponawiam sprawdzanie potwierdzenia transakcji on-chain.")
        val resultWrite = contractClient.confirmSubmissionResultReceipt(
            txHash = txHash,
            onProgress = reportStatus,
        )
        if (!resultWrite.success || resultWrite.txHash.isNullOrBlank()) {
            val error = resultWrite.error?.ifBlank { null } ?: "Submission result was not recorded on-chain."
            if (resultWrite.txHash != null && error.equals(RECEIPT_TIMEOUT_ERROR, ignoreCase = true)) {
                throwReceiptTimeout(
                    submissionId = submissionId,
                    txHash = resultWrite.txHash,
                    partialResponse = partialResponse.copy(
                        txHash = resultWrite.txHash,
                        explorerUrl = contractConfig.explorerTxUrl(resultWrite.txHash),
                    ),
                )
            }
            repository.markSubmissionResultFailed(submissionId, error)
            throw SubmitProblemValidationException(error)
        }
        repository.markSubmissionResultRecorded(
            submissionId = submissionId,
            proxyAddress = contractConfig.proxyAddress,
            txHash = resultWrite.txHash,
            recordedAt = Instant.now(),
            fromWallet = contractConfig.operatorWalletAddress,
        )
        return partialResponse.copy(
            txHash = resultWrite.txHash,
            explorerUrl = contractConfig.explorerTxUrl(resultWrite.txHash),
        )
    }

    private fun recordAcceptedSubmissionResult(
        context: ProblemExecutionContext,
        submissionId: Long,
        acceptedResponse: SubmitProblemResponseDto,
        codeHash: String,
        testsHash: String,
        resultHash: String,
        sandboxImageHash: String,
        runtimeMs: Int,
        memoryUsedKb: Int,
        consensusReached: Int,
        reportStatus: (String) -> Unit,
    ): SubmitProblemResponseDto {
        reportStatus("Zapisuję wynik on-chain.")
        val resultWrite = contractClient.recordSubmissionResult(
            SubmissionResultRecord(
                competitionId = context.onchainCompetitionId,
                submissionId = submissionId,
                participantWalletAddress = context.participantWalletAddress,
                submissionHash = acceptedResponse.commitmentHash,
                codeHash = codeHash,
                testsHash = testsHash,
                resultHash = resultHash,
                sandboxImageHash = sandboxImageHash,
                runtimeMs = runtimeMs,
                memoryUsedKb = memoryUsedKb,
                consensusNodes = consensusReached,
            ),
            onProgress = reportStatus,
            onTransactionSent = { txHash ->
                repository.markSubmissionResultPendingConfirmation(
                    submissionId = submissionId,
                    proxyAddress = contractConfig.proxyAddress,
                    txHash = txHash,
                    fromWallet = contractConfig.operatorWalletAddress,
                )
            },
        )
        return when {
            resultWrite.success && !resultWrite.txHash.isNullOrBlank() -> {
                logger.info(
                    "On-chain submission result write confirmed for submissionId={} with txHash={}.",
                    submissionId,
                    resultWrite.txHash,
                )
                repository.markSubmissionResultRecorded(
                    submissionId = submissionId,
                    proxyAddress = contractConfig.proxyAddress,
                    txHash = resultWrite.txHash,
                    recordedAt = Instant.now(),
                    fromWallet = contractConfig.operatorWalletAddress,
                )
                acceptedResponse.copy(
                    txHash = resultWrite.txHash,
                    explorerUrl = contractConfig.explorerTxUrl(resultWrite.txHash),
                )
            }

            else -> {
                logger.warn(
                    "On-chain submission result write failed for submissionId={}: txHash={}, error={}.",
                    submissionId,
                    resultWrite.txHash,
                    resultWrite.error,
                )
                val error = resultWrite.error?.ifBlank { null } ?: "Submission result was not recorded on-chain."
                if (resultWrite.txHash != null && error.equals(RECEIPT_TIMEOUT_ERROR, ignoreCase = true)) {
                    throwReceiptTimeout(
                        submissionId = submissionId,
                        txHash = resultWrite.txHash,
                        partialResponse = acceptedResponse.copy(
                            txHash = resultWrite.txHash,
                            explorerUrl = contractConfig.explorerTxUrl(resultWrite.txHash),
                        ),
                    )
                }
                repository.markSubmissionResultFailed(
                    submissionId = submissionId,
                    error = error,
                )
                throw SubmitProblemValidationException(error)
            }
        }
    }

    private fun throwReceiptTimeout(
        submissionId: Long,
        txHash: String,
        partialResponse: SubmitProblemResponseDto,
    ): Nothing {
        val detailedError = buildReceiptTimeoutMessage(
            submissionId = submissionId,
            txHash = txHash,
            timeoutMs = contractConfig.receiptTimeoutMs,
        )
        repository.markSubmissionResultPendingError(
            submissionId = submissionId,
            error = detailedError,
            txHash = txHash,
        )
        throw SubmissionReceiptTimeoutException(
            submissionId = submissionId,
            partialResponse = partialResponse,
            message = detailedError,
        )
    }
}

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

private fun buildReceiptTimeoutMessage(
    submissionId: Long,
    txHash: String,
    timeoutMs: Long,
): String {
    return buildString {
        appendLine(RECEIPT_TIMEOUT_ERROR)
        appendLine("Submission ID: $submissionId")
        appendLine("Transaction hash: $txHash")
        appendLine("Receipt timeout: ${timeoutMs} ms")
        append("Retry is available and will re-check the same transaction receipt without sending a new transaction.")
    }
}

private const val RECEIPT_TIMEOUT_ERROR = "Transaction sent but receipt was not confirmed in time."

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

internal fun computeSandboxResultHash(results: List<SandboxRunTestOutput>): String {
    val canonical = results
        .sortedBy { it.order }
        .joinToString("\n") { result ->
            // Consensus must be stable across nodes, so runtime metrics stay outside the hash.
            "${result.id}|${result.order}|${result.status}|${result.output.orEmpty()}|${result.passed ?: false}|${result.message.orEmpty()}"
    }
    return sha256Hex(canonical)
}

private fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun keccakHex(text: String): String {
    val digest = Hash.sha3(text.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private const val MAX_SOURCE_CODE_CHARS = 120_000
private const val HIDDEN_TEST_FAILED_MESSAGE = "Hidden test failed."
private const val DEFAULT_ATTESTATION_SCHEME = "hmac-sha256"
