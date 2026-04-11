package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.db.AnchorBatchStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAnchorStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.anchor.AnchorConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.anchor.AnchorTransactionResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.anchor.BlockchainAnchorClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.JoinProblemResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.PersistedSubmissionRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionTest
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionAnchorBatchRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionRecordDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxNodeRunOutput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunInput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunOutput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunTestOutput

class SubmitProblemCodeUseCaseTest {
    @Test
    fun `does not persist submission when any test does not pass`() {
        val repository = FakeProblemWriteRepository(
            executionContext = ProblemExecutionContext(
                problemId = 7,
                requiredParticipants = 1,
                registeredParticipants = 1,
                submitUntilDate = LocalDate.of(2026, 4, 30),
                tests = listOf(
                    ProblemExecutionTest(
                        id = 101,
                        order = 1,
                        inputData = "2 2",
                        expectedOutput = "4",
                        validatorCode = "",
                        validatorLanguage = "kotlin",
                        isHidden = false,
                        timeoutMs = 1_000,
                        memoryLimitMb = 256,
                    ),
                    ProblemExecutionTest(
                        id = 102,
                        order = 2,
                        inputData = "5 7",
                        expectedOutput = "12",
                        validatorCode = "",
                        validatorLanguage = "kotlin",
                        isHidden = false,
                        timeoutMs = 1_000,
                        memoryLimitMb = 256,
                    ),
                ),
            )
        )
        val secret = "sandbox-secret"
        val sandboxConfig = SandboxConfig(
            nodes = listOf("http://sandbox-node-1"),
            requestTimeoutMs = 20_000,
            connectTimeoutMs = 2_500,
            expectedImageHash = null,
            requiredConsensus = 1,
            nodeAttestationSecrets = mapOf("sandbox-node-1" to secret),
        )
        val results = listOf(
            SandboxRunTestOutput(
                id = 101,
                order = 1,
                status = "OK",
                output = "4",
                passed = true,
                executionTimeMs = 11,
                message = null,
            ),
            SandboxRunTestOutput(
                id = 102,
                order = 2,
                status = "OK",
                output = "13",
                passed = false,
                executionTimeMs = 13,
                message = "Output does not match expected value.",
            ),
        )
        val sandboxClient = FakeSandboxClient(
            nodeRuns = listOf(
                validNodeRun(
                    nodeId = "sandbox-node-1",
                    nodeUrl = "http://sandbox-node-1",
                    secret = secret,
                    results = results,
                )
            )
        )
        val useCase = SubmitProblemCodeUseCaseImpl(
            repository = repository,
            sandboxClient = sandboxClient,
            sandboxConfig = sandboxConfig,
            anchorConfig = AnchorConfig(
                enabled = false,
                chainId = null,
                contractAddress = null,
                signerPrivateKey = null,
                gasLimit = 350_000L,
                gasPriceWei = null,
                receiptTimeoutMs = 90_000L,
                receiptPollIntervalMs = 2_000L,
                explorerTxBaseUrl = null,
                contractMethodName = "anchorSubmission",
            ),
            blockchainAnchorClient = FakeBlockchainAnchorClient(),
        )

        val error = assertFailsWith<SubmitProblemValidationException> {
            useCase(
                userId = 42,
                problemId = 7,
                request = RunProblemRequestDto(
                    sourceCode = "fun solve(input: String): String = input",
                ),
            )
        }

        assertEquals(
            "Submission blocked: 1/2 tests did not pass. Solution was not submitted.",
            error.message,
        )
        assertFalse(repository.createSubmissionRecordCalled)
    }

    @Test
    fun `stores max test execution time as submission runtime`() {
        val repository = FakeProblemWriteRepository(
            executionContext = ProblemExecutionContext(
                problemId = 7,
                requiredParticipants = 1,
                registeredParticipants = 1,
                submitUntilDate = LocalDate.of(2026, 4, 30),
                tests = listOf(
                    ProblemExecutionTest(
                        id = 101,
                        order = 1,
                        inputData = "2 2",
                        expectedOutput = "4",
                        validatorCode = "",
                        validatorLanguage = "kotlin",
                        isHidden = false,
                        timeoutMs = 1_000,
                        memoryLimitMb = 256,
                    ),
                    ProblemExecutionTest(
                        id = 102,
                        order = 2,
                        inputData = "5 7",
                        expectedOutput = "12",
                        validatorCode = "",
                        validatorLanguage = "kotlin",
                        isHidden = false,
                        timeoutMs = 1_000,
                        memoryLimitMb = 256,
                    ),
                ),
            )
        )
        val secret = "sandbox-secret"
        val sandboxConfig = SandboxConfig(
            nodes = listOf("http://sandbox-node-1"),
            requestTimeoutMs = 20_000,
            connectTimeoutMs = 2_500,
            expectedImageHash = null,
            requiredConsensus = 1,
            nodeAttestationSecrets = mapOf("sandbox-node-1" to secret),
        )
        val results = listOf(
            SandboxRunTestOutput(
                id = 101,
                order = 1,
                status = "OK",
                output = "4",
                passed = true,
                executionTimeMs = 11,
                message = null,
            ),
            SandboxRunTestOutput(
                id = 102,
                order = 2,
                status = "OK",
                output = "12",
                passed = true,
                executionTimeMs = 19,
                message = null,
            ),
        )
        val sandboxClient = FakeSandboxClient(
            nodeRuns = listOf(
                validNodeRun(
                    nodeId = "sandbox-node-1",
                    nodeUrl = "http://sandbox-node-1",
                    secret = secret,
                    results = results,
                )
            )
        )
        val useCase = SubmitProblemCodeUseCaseImpl(
            repository = repository,
            sandboxClient = sandboxClient,
            sandboxConfig = sandboxConfig,
            anchorConfig = AnchorConfig(
                enabled = false,
                chainId = null,
                contractAddress = null,
                signerPrivateKey = null,
                gasLimit = 350_000L,
                gasPriceWei = null,
                receiptTimeoutMs = 90_000L,
                receiptPollIntervalMs = 2_000L,
                explorerTxBaseUrl = null,
                contractMethodName = "anchorSubmission",
            ),
            blockchainAnchorClient = FakeBlockchainAnchorClient(),
        )

        val result = useCase(
            userId = 42,
            problemId = 7,
            request = RunProblemRequestDto(
                sourceCode = "fun solve(input: String): String = input",
            ),
        )

        assertEquals(19, result.runtimeMs)
        assertEquals(19, assertNotNull(repository.lastSubmissionDraft).runtimeMs)
    }
}

private class FakeProblemWriteRepository(
    private val executionContext: ProblemExecutionContext,
) : ProblemWriteRepository {
    var createSubmissionRecordCalled = false
    var lastSubmissionDraft: SubmissionRecordDraft? = null

    override fun createProblemForUser(
        userId: Long,
        draft: pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemDraft,
    ): Int = error("Not used in this test.")

    override fun registerUserForProblem(userId: Long, problemId: Int): JoinProblemResult =
        error("Not used in this test.")

    override fun fetchExecutionContextForUser(userId: Long, problemId: Int): ProblemExecutionContext =
        executionContext

    override fun createSubmissionRecord(draft: SubmissionRecordDraft): PersistedSubmissionRecord {
        createSubmissionRecordCalled = true
        lastSubmissionDraft = draft
        return PersistedSubmissionRecord(
            submissionId = 1L,
            anchorStatus = SubmissionAnchorStatus.Disabled,
        )
    }

    override fun createAnchorBatch(
        rootHash: String,
        submissionIds: List<Long>,
        status: AnchorBatchStatus,
        txHash: String?,
        chainId: Long?,
        contractAddress: String?,
        failureReason: String?,
        anchoredAt: Instant?,
    ): SubmissionAnchorBatchRecord = error("Not used in this test.")

    override fun updateSubmissionAnchors(
        submissionIds: List<Long>,
        status: SubmissionAnchorStatus,
        batchId: Long,
        merkleRoot: String,
        proofBySubmission: Map<Long, List<String>>,
        txHash: String?,
        error: String?,
        anchoredAt: Instant?,
    ) = error("Not used in this test.")
}

private class FakeSandboxClient(
    private val nodeRuns: List<SandboxNodeRunOutput>,
) : SandboxClient {
    override fun runSolution(sourceCode: String, tests: List<SandboxRunInput>): SandboxRunOutput =
        error("Not used in this test.")

    override fun runSolutionOnAllNodes(
        sourceCode: String,
        tests: List<SandboxRunInput>,
    ): List<SandboxNodeRunOutput> = nodeRuns
}

private class FakeBlockchainAnchorClient : BlockchainAnchorClient {
    override fun anchorSubmission(commitmentHash: String, submissionId: Long): AnchorTransactionResult =
        error("Not used in this test.")

    override fun close() = Unit
}

private fun validNodeRun(
    nodeId: String,
    nodeUrl: String,
    secret: String,
    results: List<SandboxRunTestOutput>,
): SandboxNodeRunOutput {
    val imageHash = "local-image"
    val runHash = "0xrunhash"
    val resultHash = computeSandboxResultHash(results)
    val executedAt = "2026-04-11T12:00:00Z"
    val payloadHash = sha256Hex("$nodeId|$imageHash|$runHash|$resultHash|$executedAt")
    val signature = hmacSha256Hex(secret, payloadHash)
    return SandboxNodeRunOutput(
        nodeId = nodeId,
        nodeUrl = nodeUrl,
        imageHash = imageHash,
        runHash = runHash,
        resultHash = resultHash,
        attestationPayloadHash = payloadHash,
        attestationSignature = signature,
        attestationScheme = "hmac-sha256",
        executedAt = executedAt,
        results = results,
        errorMessage = null,
    )
}

private fun sha256Hex(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun hmacSha256Hex(secret: String, payloadHash: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val digest = mac.doFinal(payloadHash.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}
