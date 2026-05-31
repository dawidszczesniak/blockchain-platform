package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
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
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.JudgeLanguageProfile
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.JudgeLanguages
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.PreparedSignedSubmissionResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.bytes32HashHex
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.generateOnchainSubmissionId
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.PersistedSubmissionRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionTest
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemSubmissionRepository
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
    private val repository: ProblemSubmissionRepository,
    private val sandboxClient: SandboxClient,
    private val sandboxConfig: SandboxConfig,
    private val contractConfig: BlockchainPlatformContractConfig,
    private val contractClient: SubmissionResultContractClient,
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
        val validatedRequest = validateSubmissionRequest(request)
        val context = loadExecutionContext(userId, problemId)
        val evaluation = evaluateSubmission(
            problemId = problemId,
            userId = userId,
            context = context,
            request = validatedRequest,
            reportStatus = reportStatus,
        )
        return rejectedSubmissionOutcome(problemId, userId, evaluation)
            ?: acceptedSubmissionOutcome(
                userId = userId,
                problemId = problemId,
                context = context,
                request = validatedRequest,
                evaluation = evaluation,
                reportStatus = reportStatus,
            )
    }

    private fun validateSubmissionRequest(request: RunProblemRequestDto): ValidSubmissionRequest {
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
        return ValidSubmissionRequest(
            sourceCode = sourceCode,
            languageProfile = languageProfile,
        )
    }

    private fun loadExecutionContext(userId: Long, problemId: Int): ProblemExecutionContext {
        return runCatching {
            repository.fetchExecutionContextForUser(
                userId = userId,
                problemId = problemId,
            )
        }.getOrElse { error ->
            throw SubmitProblemValidationException(
                error.message?.ifBlank { "Cannot submit this solution." } ?: "Cannot submit this solution."
            )
        }
    }

    private fun evaluateSubmission(
        problemId: Int,
        userId: Long,
        context: ProblemExecutionContext,
        request: ValidSubmissionRequest,
        reportStatus: (String) -> Unit,
    ): SubmissionClusterEvaluation {
        val sandboxInputs = context.tests.map(request.languageProfile::applyTo)
        logger.info(
            "Starting submission evaluation for problemId={}, userId={}, tests={}, language={}.",
            problemId,
            userId,
            sandboxInputs.size,
            request.languageProfile.id,
        )
        return evaluateSubmissionOnCluster(
            context = context,
            sourceCode = request.sourceCode,
            language = request.languageProfile.id,
            tests = sandboxInputs,
            reportStatus = reportStatus,
            sandboxClient = sandboxClient,
            sandboxConsensusEvaluator = sandboxConsensusEvaluator,
            sandboxConfig = sandboxConfig,
        )
    }

    private fun rejectedSubmissionOutcome(
        problemId: Int,
        userId: Long,
        evaluation: SubmissionClusterEvaluation,
    ): SubmissionJudgeOutcome.Rejected? {
        val consensus = evaluation.consensus
        val consensusNode = consensus.representativeNode
        val evaluatedTests = evaluation.evaluatedTests
        val passedCount = evaluatedTests.count { it.apiResult.passed }
        logger.info(
            "Submission evaluation aggregated for problemId={}, userId={}: consensusReached={}/{}, runtimeMs={}, memoryUsedKb={}.",
            problemId,
            userId,
            consensus.consensusReached,
            sandboxConfig.requiredConsensus,
            consensus.runtimeMs,
            consensus.memoryUsedKb,
        )
        if (passedCount == evaluatedTests.size) {
            return null
        }

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
                runtimeMs = consensus.runtimeMs,
                memoryUsedKb = consensus.memoryUsedKb,
                results = evaluatedTests.map { it.apiResult },
                sandboxNodeId = consensusNode.nodeId,
                sandboxImageHash = consensusNode.imageHash,
                sandboxRunHash = consensusNode.runHash,
            ),
            message = "Submission blocked: $failedCount/${evaluatedTests.size} tests did not pass. Solution was not submitted.",
        )
    }

    private fun acceptedSubmissionOutcome(
        userId: Long,
        problemId: Int,
        context: ProblemExecutionContext,
        request: ValidSubmissionRequest,
        evaluation: SubmissionClusterEvaluation,
        reportStatus: (String) -> Unit,
    ): SubmissionJudgeOutcome.Accepted {
        val consensus = evaluation.consensus
        val evaluatedTests = evaluation.evaluatedTests
        val memoryUsedKb = consensus.memoryUsedKb
            ?: throw SubmitProblemValidationException("Submission blocked: sandbox memory usage was not reported.")
        val hashes = buildSubmissionHashes(
            context = context,
            sourceCode = request.sourceCode,
            consensus = consensus,
            memoryUsedKb = memoryUsedKb,
        )
        val submissionRecord = persistAcceptedSubmission(
            userId = userId,
            problemId = problemId,
            context = context,
            request = request,
            evaluation = evaluation,
            memoryUsedKb = memoryUsedKb,
            hashes = hashes,
        )
        reportStatus("Przygotowuję podpisany wynik do zapisu on-chain.")
        val signedSubmission = prepareSignedSubmission(
            problemId = problemId,
            userId = userId,
            context = context,
            submissionRecord = submissionRecord,
            hashes = hashes,
            consensus = consensus,
            memoryUsedKb = memoryUsedKb,
        )
        val acceptedResponse = buildAcceptedResponse(
            submissionId = submissionRecord.submissionId,
            evaluatedTests = evaluatedTests,
            consensus = consensus,
            memoryUsedKb = memoryUsedKb,
            hashes = hashes,
            signedSubmission = signedSubmission,
        )
        return SubmissionJudgeOutcome.Accepted(response = acceptedResponse)
    }

    private fun buildSubmissionHashes(
        context: ProblemExecutionContext,
        sourceCode: String,
        consensus: SandboxConsensusDecision,
        memoryUsedKb: Int,
    ): SubmissionHashes {
        val codeHash = sha256Hex(sourceCode)
        val challengeHash = hashExecutionChallenge(context)
        val sandboxImageHash = bytes32HashHex(consensus.imageHash)
        return SubmissionHashes(
            codeHash = codeHash,
            challengeHash = challengeHash,
            sandboxImageHash = sandboxImageHash,
            commitmentHash = buildCommitmentHash(
                competitionId = context.onchainCompetitionId,
                participantWalletAddress = context.participantWalletAddress,
                codeHash = codeHash,
                challengeHash = challengeHash,
                resultHash = consensus.resultHash,
                imageHash = sandboxImageHash,
                runtimeMs = consensus.runtimeMs,
                memoryUsedKb = memoryUsedKb,
                consensusNodes = consensus.consensusReached,
            ),
        )
    }

    private fun persistAcceptedSubmission(
        userId: Long,
        problemId: Int,
        context: ProblemExecutionContext,
        request: ValidSubmissionRequest,
        evaluation: SubmissionClusterEvaluation,
        memoryUsedKb: Int,
        hashes: SubmissionHashes,
    ) = repository.createSubmissionRecord(
            SubmissionRecordDraft(
                onchainSubmissionId = generateOnchainSubmissionId(),
                problemId = problemId,
                userId = userId,
                status = SubmissionAttemptStatus.Accepted,
                sourceCode = request.sourceCode,
                language = request.languageProfile.id,
                codeHash = hashes.codeHash,
                challengeHash = hashes.challengeHash,
                resultHash = evaluation.consensus.resultHash,
                consensusImageHash = hashes.sandboxImageHash,
                consensusNodes = evaluation.consensus.consensusReached,
                commitmentHash = hashes.commitmentHash,
                runtimeMs = evaluation.consensus.runtimeMs,
                memoryUsedKb = memoryUsedKb,
                testResults = persistedTestResults(evaluation.evaluatedTests),
                nodeAttestations = nodeAttestations(evaluation.consensus),
            )
        ).also { submissionRecord ->
            logger.info(
                "Persisted accepted submission draft for problemId={}, userId={}, submissionId={}.",
                problemId,
                userId,
                submissionRecord.submissionId,
            )
        }

    private fun persistedTestResults(evaluatedTests: List<EvaluatedSubmissionTest>): List<SubmissionPersistedTestResult> {
        return evaluatedTests.map { test ->
            SubmissionPersistedTestResult(
                problemTestId = test.problemTestId,
                status = test.dbStatus,
                executionTimeMs = test.apiResult.executionTimeMs,
                memoryUsedKb = test.apiResult.memoryUsedKb,
                message = test.apiResult.message,
            )
        }
    }

    private fun nodeAttestations(consensus: SandboxConsensusDecision): List<SubmissionNodeAttestationDraft> {
        return consensus.evaluatedNodes.map { node ->
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
                isConsensus = node.nodeId in consensus.consensusNodeIds,
                status = node.status,
                message = node.message,
            )
        }
    }

    private fun prepareSignedSubmission(
        problemId: Int,
        userId: Long,
        context: ProblemExecutionContext,
        submissionRecord: PersistedSubmissionRecord,
        hashes: SubmissionHashes,
        consensus: SandboxConsensusDecision,
        memoryUsedKb: Int,
    ) = contractClient.prepareSignedSubmissionResult(
        SubmissionResultRecord(
            competitionId = context.onchainCompetitionId,
            onchainSubmissionId = submissionRecord.onchainSubmissionId,
            participantWalletAddress = context.participantWalletAddress,
            submissionHash = hashes.commitmentHash,
            codeHash = hashes.codeHash,
            challengeHash = hashes.challengeHash,
            resultHash = consensus.resultHash,
            sandboxImageHash = hashes.sandboxImageHash,
            runtimeMs = consensus.runtimeMs,
            memoryUsedKb = memoryUsedKb,
            consensusNodes = consensus.consensusReached,
        )
    ).also { signedSubmission ->
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
    }

    private fun buildAcceptedResponse(
        submissionId: Long,
        evaluatedTests: List<EvaluatedSubmissionTest>,
        consensus: SandboxConsensusDecision,
        memoryUsedKb: Int,
        hashes: SubmissionHashes,
        signedSubmission: PreparedSignedSubmissionResult,
    ): SubmitProblemResponseDto {
        val passedCount = evaluatedTests.count { it.apiResult.passed }
        return SubmitProblemResponseDto(
            submissionId = submissionId,
            total = evaluatedTests.size,
            passed = passedCount,
            allPassed = passedCount == evaluatedTests.size,
            runtimeMs = consensus.runtimeMs,
            memoryUsedKb = memoryUsedKb,
            results = evaluatedTests.map { it.apiResult },
            consensusRequired = sandboxConfig.requiredConsensus,
            consensusReached = consensus.consensusReached,
            sandboxImageHash = hashes.sandboxImageHash,
            sandboxResultHash = consensus.resultHash,
            commitmentHash = hashes.commitmentHash,
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
    }
}

private data class SubmissionClusterEvaluation(
    val consensus: SandboxConsensusDecision,
    val evaluatedTests: List<EvaluatedSubmissionTest>,
)

private data class ValidSubmissionRequest(
    val sourceCode: String,
    val languageProfile: JudgeLanguageProfile,
)

private data class SubmissionHashes(
    val codeHash: String,
    val challengeHash: String,
    val sandboxImageHash: String,
    val commitmentHash: String,
)

private fun evaluateSubmissionOnCluster(
    context: ProblemExecutionContext,
    sourceCode: String,
    language: String,
    tests: List<SandboxRunInput>,
    reportStatus: (String) -> Unit,
    sandboxClient: SandboxClient,
    sandboxConsensusEvaluator: SandboxConsensusEvaluator,
    sandboxConfig: SandboxConfig,
): SubmissionClusterEvaluation {
    reportStatus("Uruchamianie testów na sandboxach.")
    val nodeRuns = sandboxClient.runSolutionOnAllNodes(
        sourceCode = sourceCode,
        language = language,
        tests = tests,
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
    logger.info(
        "Submission cluster evaluation for problemId={}, participantWallet={}: consensusReached={}/{}, runtimeMs={}, memoryUsedKb={}.",
        context.problemId,
        context.participantWalletAddress,
        consensus.consensusReached,
        sandboxConfig.requiredConsensus,
        consensus.runtimeMs,
        consensus.memoryUsedKb,
    )
    return SubmissionClusterEvaluation(
        consensus = consensus,
        evaluatedTests = evaluatedTests,
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
private const val HIDDEN_TEST_FAILED_MESSAGE = "Hidden test failed."
private const val DEFAULT_ATTESTATION_SCHEME = "hmac-sha256"
