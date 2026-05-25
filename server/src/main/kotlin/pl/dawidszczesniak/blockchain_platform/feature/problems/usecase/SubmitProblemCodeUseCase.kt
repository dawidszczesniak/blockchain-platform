package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import java.util.UUID
import org.slf4j.LoggerFactory
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttestationStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PreparedWalletTransactionDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.JudgeLanguages
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.bytes32HashHex
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.generateOnchainSubmissionId
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionTest
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionNodeAttestationDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionPersistedTestResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionRecordDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunInput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunTestOutput

private val logger = LoggerFactory.getLogger("SubmitProblemCodeUseCase")

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
    fun judge(
        userId: Long,
        problemId: Int,
        request: RunProblemRequestDto,
        reportStatus: (String) -> Unit = {},
    ): SubmissionJudgeOutcome
}

internal class SubmitProblemCodeUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val sandboxClient: SandboxClient,
    private val sandboxConfig: SandboxConfig,
    private val contractConfig: BlockchainPlatformContractConfig,
    private val contractClient: BlockchainPlatformContractClient,
    private val blockchainConfig: BlockchainConfig,
    private val sandboxConsensusEvaluator: SandboxConsensusEvaluator = SandboxConsensusEvaluator(sandboxConfig),
) : SubmitProblemCodeUseCase, SubmissionJudgeService {
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
        val evaluation = evaluateSubmissionAcrossAttempts(
            context = context,
            sourceCode = sourceCode,
            language = languageProfile.id,
            tests = sandboxInputs,
            reportStatus = reportStatus,
            sandboxClient = sandboxClient,
            sandboxConsensusEvaluator = sandboxConsensusEvaluator,
            sandboxConfig = sandboxConfig,
        )
        val evaluatedNodes = evaluation.canonicalAttempt.consensus.evaluatedNodes
        val consensusReached = evaluation.consensusReached
        val consensusResultHash = evaluation.canonicalAttempt.consensus.resultHash
        val consensusImageHash = evaluation.canonicalAttempt.consensus.imageHash
        val consensusNodeIds = evaluation.canonicalAttempt.consensus.consensusNodeIds
        val consensusNode = evaluation.canonicalAttempt.consensus.representativeNode
        val evaluatedTests = evaluation.canonicalAttempt.evaluatedTests
        val passedCount = evaluatedTests.count { it.apiResult.passed }
        val allPassed = passedCount == evaluatedTests.size
        val runtimeMs = evaluation.runtimeMs
        val reportedMemoryUsedKb = evaluation.memoryUsedKb
        logger.info(
            "Submission evaluation aggregated for problemId={}, userId={}: attempts={}, consensusReached={}/{}, runtimeMs={}, memoryUsedKb={}.",
            problemId,
            userId,
            evaluation.attempts.size,
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
        val challengeHash = hashExecutionChallenge(context)
        val sandboxImageHash = bytes32HashHex(consensusImageHash)
        val commitmentHash = buildCommitmentHash(
            competitionId = context.onchainCompetitionId,
            participantWalletAddress = context.participantWalletAddress,
            codeHash = codeHash,
            challengeHash = challengeHash,
            resultHash = consensusResultHash,
            imageHash = sandboxImageHash,
            runtimeMs = runtimeMs,
            memoryUsedKb = memoryUsedKb,
            consensusNodes = consensusReached,
        )
        val submissionRecord = repository.createSubmissionRecord(
            SubmissionRecordDraft(
                onchainSubmissionId = generateOnchainSubmissionId(),
                problemId = problemId,
                userId = userId,
                status = submissionStatus,
                sourceCode = sourceCode,
                language = languageProfile.id,
                codeHash = codeHash,
                challengeHash = challengeHash,
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
        reportStatus("Przygotowuję podpisany wynik do zapisu on-chain.")
        val signedSubmission = contractClient.prepareSignedSubmissionResult(
            SubmissionResultRecord(
                competitionId = context.onchainCompetitionId,
                onchainSubmissionId = submissionRecord.onchainSubmissionId,
                participantWalletAddress = context.participantWalletAddress,
                submissionHash = commitmentHash,
                codeHash = codeHash,
                challengeHash = challengeHash,
                resultHash = consensusResultHash,
                sandboxImageHash = sandboxImageHash,
                runtimeMs = runtimeMs,
                memoryUsedKb = memoryUsedKb,
                consensusNodes = consensusReached,
            )
        )
        if (!signedSubmission.simulationErrorMessage.isNullOrBlank()) {
            logger.warn(
                "Submission on-chain preflight reverted for problemId={}, userId={}, submissionId={}, onchainSubmissionId={}, competitionId={}: {}",
                problemId,
                userId,
                submissionRecord.submissionId,
                submissionRecord.onchainSubmissionId,
                context.onchainCompetitionId,
                signedSubmission.simulationErrorMessage,
            )
            repository.markSubmissionResultPendingError(
                submissionId = submissionRecord.submissionId,
                error = signedSubmission.simulationErrorMessage,
            )
        } else {
            logger.info(
                "Submission on-chain payload prepared for problemId={}, userId={}, submissionId={}, onchainSubmissionId={}, competitionId={}.",
                problemId,
                userId,
                submissionRecord.submissionId,
                submissionRecord.onchainSubmissionId,
                context.onchainCompetitionId,
            )
        }
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
            chainId = blockchainConfig.chainId,
            proxyAddress = contractConfig.proxyAddress,
            walletTransaction = signedSubmission.simulationErrorMessage?.let { null } ?: PreparedWalletTransactionDto(
                to = signedSubmission.transaction.to,
                data = signedSubmission.transaction.data,
                valueHex = signedSubmission.transaction.valueHex,
            ),
            signature = signedSubmission.signatureHex,
            signerWalletAddress = signedSubmission.signerWalletAddress,
            onchainSimulationError = signedSubmission.simulationErrorMessage,
            onchainRecorded = false,
            txHash = "",
            explorerUrl = null,
        )
        return SubmissionJudgeOutcome.Accepted(
            response = acceptedResponse,
        )
    }
}

private data class SubmissionEvaluationAttempt(
    val attemptNumber: Int,
    val consensus: SandboxConsensusDecision,
    val evaluatedTests: List<EvaluatedSubmissionTest>,
    val worstCaseRuntimeMs: Int,
    val worstCaseMemoryUsedKb: Int?,
)

private data class SubmissionEvaluationAggregate(
    val attempts: List<SubmissionEvaluationAttempt>,
    val canonicalAttempt: SubmissionEvaluationAttempt,
    val consensusReached: Int,
    val runtimeMs: Int,
    val memoryUsedKb: Int?,
)

private fun evaluateSubmissionAcrossAttempts(
    context: ProblemExecutionContext,
    sourceCode: String,
    language: String,
    tests: List<SandboxRunInput>,
    reportStatus: (String) -> Unit,
    sandboxClient: SandboxClient,
    sandboxConsensusEvaluator: SandboxConsensusEvaluator,
    sandboxConfig: SandboxConfig,
): SubmissionEvaluationAggregate {
    val attempts = (1..SUBMISSION_EVALUATION_ATTEMPTS).map { attemptNumber ->
        reportStatus("Uruchamianie testów na sandboxach (proba $attemptNumber/$SUBMISSION_EVALUATION_ATTEMPTS).")
        val nodeRuns = sandboxClient.runSolutionOnAllNodes(
            sourceCode = sourceCode,
            language = language,
            tests = tests,
            runId = "submission-${context.problemId}-${UUID.randomUUID()}-$attemptNumber",
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

        val executionByTestId = consensus.representativeNode.results.associateBy { it.id }
        val evaluatedTests = context.tests.map { test ->
            val execution = executionByTestId[test.id]
            if (execution == null) {
                test.toMissingSubmissionResult()
            } else {
                test.evaluateSubmissionTest(execution)
            }
        }
        val worstCaseRuntimeMs = consensusWorstCaseRuntimeMs(consensus)
        val worstCaseMemoryUsedKb = consensusWorstCaseMemoryUsedKb(consensus)
        logger.info(
            "Submission attempt {}/{} for problemId={}, participantWallet={}: consensusReached={}/{}, worstCaseRuntimeMs={}, worstCaseMemoryUsedKb={}.",
            attemptNumber,
            SUBMISSION_EVALUATION_ATTEMPTS,
            context.problemId,
            context.participantWalletAddress,
            consensus.consensusReached,
            sandboxConfig.requiredConsensus,
            worstCaseRuntimeMs,
            worstCaseMemoryUsedKb,
        )
        SubmissionEvaluationAttempt(
            attemptNumber = attemptNumber,
            consensus = consensus,
            evaluatedTests = evaluatedTests,
            worstCaseRuntimeMs = worstCaseRuntimeMs,
            worstCaseMemoryUsedKb = worstCaseMemoryUsedKb,
        )
    }

    ensureConsistentAttemptOutputs(attempts)

    val finalConsensusReached = attempts.minOf { it.consensus.consensusReached }
    val canonicalAttempt = attempts.first { it.consensus.consensusReached == finalConsensusReached }
    val runtimeMs = medianAttemptMetric(attempts.map { it.worstCaseRuntimeMs }) ?: 0
    val memoryAttemptMetrics = attempts.map { it.worstCaseMemoryUsedKb }
    val memoryUsedKb = if (memoryAttemptMetrics.all { it != null }) {
        medianAttemptMetric(memoryAttemptMetrics.filterNotNull())
    } else {
        null
    }

    return SubmissionEvaluationAggregate(
        attempts = attempts,
        canonicalAttempt = canonicalAttempt,
        consensusReached = finalConsensusReached,
        runtimeMs = runtimeMs,
        memoryUsedKb = memoryUsedKb,
    )
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

private fun ensureConsistentAttemptOutputs(attempts: List<SubmissionEvaluationAttempt>) {
    val resultHashes = attempts.map { it.consensus.resultHash }.distinct()
    if (resultHashes.size != 1) {
        throw SubmitProblemValidationException(
            "Submission blocked: repeated sandbox attempts produced inconsistent result hashes."
        )
    }
    val imageHashes = attempts.map { it.consensus.imageHash.orEmpty() }.distinct()
    if (imageHashes.size != 1) {
        throw SubmitProblemValidationException(
            "Submission blocked: repeated sandbox attempts produced inconsistent sandbox images."
        )
    }
}

private fun consensusWorstCaseRuntimeMs(consensus: SandboxConsensusDecision): Int {
    return consensusConsensusNodes(consensus)
        .map { node ->
            node.results.maxOfOrNull { it.executionTimeMs } ?: node.suiteExecutionTimeMs ?: 0
        }
        .maxOrNull()
        ?: 0
}

private fun consensusWorstCaseMemoryUsedKb(consensus: SandboxConsensusDecision): Int? {
    return consensusConsensusNodes(consensus)
        .mapNotNull { node ->
            node.results.mapNotNull { it.memoryUsedKb }.maxOrNull()
        }
        .maxOrNull()
}

private fun consensusConsensusNodes(consensus: SandboxConsensusDecision): List<EvaluatedNodeRun> {
    return consensus.evaluatedNodes.filter { it.nodeId in consensus.consensusNodeIds }
}

private fun medianAttemptMetric(values: List<Int>): Int? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        ((sorted[middle - 1].toLong() + sorted[middle].toLong()) / 2L).toInt()
    }
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

private fun hashExecutionChallenge(context: ProblemExecutionContext): String {
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
    challengeHash: String,
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
        append(challengeHash)
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
private const val SUBMISSION_EVALUATION_ATTEMPTS = 3
private const val HIDDEN_TEST_FAILED_MESSAGE = "Hidden test failed."
private const val DEFAULT_ATTESTATION_SCHEME = "hmac-sha256"
