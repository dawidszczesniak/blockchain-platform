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
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformWriteResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.PreparedCompetitionTransaction
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.PreparedCompetitionTransactions
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.VerifiedCompetitionCreation
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.VerifiedCompetitionJoin
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.JoinProblemResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.PersistedSubmissionRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionTest
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
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
                onchainCompetitionId = 77,
                participantWalletAddress = "0x1111111111111111111111111111111111111111",
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
                memoryUsedKb = 1200,
                message = null,
            ),
            SandboxRunTestOutput(
                id = 102,
                order = 2,
                status = "OK",
                output = "13",
                passed = false,
                executionTimeMs = 13,
                memoryUsedKb = 1300,
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
                    suiteExecutionTimeMs = 24,
                )
            )
        )
        val useCase = SubmitProblemCodeUseCaseImpl(
            repository = repository,
            sandboxClient = sandboxClient,
            sandboxConfig = sandboxConfig,
            contractConfig = fakePlatformContractConfig(),
            contractClient = FakeBlockchainPlatformContractClient(),
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
    fun `stores suite execution time as submission runtime`() {
        val repository = FakeProblemWriteRepository(
            executionContext = ProblemExecutionContext(
                problemId = 7,
                onchainCompetitionId = 77,
                participantWalletAddress = "0x1111111111111111111111111111111111111111",
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
                memoryUsedKb = 1200,
                message = null,
            ),
            SandboxRunTestOutput(
                id = 102,
                order = 2,
                status = "OK",
                output = "12",
                passed = true,
                executionTimeMs = 19,
                memoryUsedKb = 1400,
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
                    suiteExecutionTimeMs = 24,
                )
            )
        )
        val useCase = SubmitProblemCodeUseCaseImpl(
            repository = repository,
            sandboxClient = sandboxClient,
            sandboxConfig = sandboxConfig,
            contractConfig = fakePlatformContractConfig(),
            contractClient = FakeBlockchainPlatformContractClient(),
        )

        val result = useCase(
            userId = 42,
            problemId = 7,
            request = RunProblemRequestDto(
                sourceCode = "fun solve(input: String): String = input",
            ),
        )

        assertEquals(24, result.runtimeMs)
        assertEquals(24, assertNotNull(repository.lastSubmissionDraft).runtimeMs)
    }

    @Test
    fun `falls back to max test execution time when suite runtime is unavailable`() {
        val repository = FakeProblemWriteRepository(
            executionContext = ProblemExecutionContext(
                problemId = 7,
                onchainCompetitionId = 77,
                participantWalletAddress = "0x1111111111111111111111111111111111111111",
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
                memoryUsedKb = 1200,
                message = null,
            ),
            SandboxRunTestOutput(
                id = 102,
                order = 2,
                status = "OK",
                output = "12",
                passed = true,
                executionTimeMs = 19,
                memoryUsedKb = 1400,
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
            contractConfig = fakePlatformContractConfig(),
            contractClient = FakeBlockchainPlatformContractClient(),
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

    @Test
    fun `uses median runtime and memory from consensus nodes as official metrics`() {
        val repository = FakeProblemWriteRepository(
            executionContext = ProblemExecutionContext(
                problemId = 7,
                onchainCompetitionId = 77,
                participantWalletAddress = "0x1111111111111111111111111111111111111111",
                requiredParticipants = 3,
                registeredParticipants = 3,
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
        val contractClient = FakeBlockchainPlatformContractClient()
        val sandboxClient = FakeSandboxClient(
            nodeRuns = listOf(
                validNodeRun(
                    nodeId = "sandbox-node-1",
                    nodeUrl = "http://sandbox-node-1",
                    secret = secrets.getValue("sandbox-node-1"),
                    results = acceptedAdditionResults(maxMemoryUsedKb = 1_000),
                    suiteExecutionTimeMs = 100,
                ),
                validNodeRun(
                    nodeId = "sandbox-node-2",
                    nodeUrl = "http://sandbox-node-2",
                    secret = secrets.getValue("sandbox-node-2"),
                    results = acceptedAdditionResults(maxMemoryUsedKb = 1_100),
                    suiteExecutionTimeMs = 105,
                ),
                validNodeRun(
                    nodeId = "sandbox-node-3",
                    nodeUrl = "http://sandbox-node-3",
                    secret = secrets.getValue("sandbox-node-3"),
                    results = acceptedAdditionResults(maxMemoryUsedKb = 3_000),
                    suiteExecutionTimeMs = 300,
                ),
            )
        )
        val useCase = SubmitProblemCodeUseCaseImpl(
            repository = repository,
            sandboxClient = sandboxClient,
            sandboxConfig = sandboxConfig,
            contractConfig = fakePlatformContractConfig(),
            contractClient = contractClient,
        )

        val result = useCase(
            userId = 42,
            problemId = 7,
            request = RunProblemRequestDto(
                sourceCode = "fun solve(input: String): String = input",
            ),
        )

        assertEquals(105, result.runtimeMs)
        assertEquals(1_100, result.memoryUsedKb)
        assertEquals(105, assertNotNull(repository.lastSubmissionDraft).runtimeMs)
        assertEquals(1_100, repository.lastSubmissionDraft?.memoryUsedKb)
        assertEquals(105, contractClient.lastRecord?.runtimeMs)
        assertEquals(1_100, contractClient.lastRecord?.memoryUsedKb)
    }
}

private class FakeProblemWriteRepository(
    private val executionContext: ProblemExecutionContext,
) : ProblemWriteRepository {
    var createSubmissionRecordCalled = false
    var lastSubmissionDraft: SubmissionRecordDraft? = null
    var recordedSubmissionId: Long? = null
    var failedSubmissionId: Long? = null

    override fun createProblemForUser(
        userId: Long,
        draft: pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemDraft,
    ): Int = error("Not used in this test.")

    override fun findProblemIdByOnchainCreationTxHash(txHash: String): Int? = error("Not used in this test.")

    override fun registerUserForProblem(userId: Long, problemId: Int): JoinProblemResult =
        error("Not used in this test.")

    override fun registerUserForProblemOnChain(
        userId: Long,
        problemId: Int,
        txHash: String,
        joinedAt: Instant,
        fromWallet: String,
    ): JoinProblemResult = error("Not used in this test.")

    override fun fetchOnchainJoinContext(problemId: Int) =
        error("Not used in this test.")

    override fun fetchExecutionContextForUser(userId: Long, problemId: Int): ProblemExecutionContext =
        executionContext

    override fun createSubmissionRecord(draft: SubmissionRecordDraft): PersistedSubmissionRecord {
        createSubmissionRecordCalled = true
        lastSubmissionDraft = draft
        return PersistedSubmissionRecord(
            submissionId = 1L,
        )
    }

    override fun markSubmissionResultRecorded(
        submissionId: Long,
        proxyAddress: String,
        txHash: String,
        recordedAt: Instant,
        fromWallet: String,
    ) {
        recordedSubmissionId = submissionId
    }

    override fun markSubmissionResultFailed(submissionId: Long, error: String) {
        failedSubmissionId = submissionId
    }

    override fun fetchCompetitionSettlementSnapshot(problemId: Int) =
        error("Not used in this test.")

    override fun fetchBestSettlementCandidate(problemId: Int) =
        error("Not used in this test.")

    override fun recordSettledWinner(
        problemId: Int,
        winnerUserId: Long,
        payoutAmountAtomic: String,
        txHash: String,
        settledAt: Instant,
        fromWallet: String,
    ) = error("Not used in this test.")

    override fun markCompetitionSettlementCancelled(problemId: Int, txHash: String, settledAt: Instant, fromWallet: String) =
        error("Not used in this test.")

    override fun markCompetitionSettlementPendingError(problemId: Int, error: String) =
        error("Not used in this test.")

    override fun markCompetitionSettlementFailed(problemId: Int, error: String) =
        error("Not used in this test.")
}

private class FakeSandboxClient(
    private val nodeRuns: List<SandboxNodeRunOutput>,
) : SandboxClient {
    override fun runSolution(sourceCode: String, language: String, tests: List<SandboxRunInput>): SandboxRunOutput =
        error("Not used in this test.")

    override fun runSolutionOnAllNodes(
        sourceCode: String,
        language: String,
        tests: List<SandboxRunInput>,
    ): List<SandboxNodeRunOutput> = nodeRuns
}

private class FakeBlockchainPlatformContractClient(
    private val response: BlockchainPlatformWriteResult = BlockchainPlatformWriteResult(
        success = true,
        txHash = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    ),
) : BlockchainPlatformContractClient {
    var lastRecord: SubmissionResultRecord? = null

    override fun prepareCreateCompetition(
        paymentAsset: PaymentAssetConfig,
        competitionKey: String,
        joinDeadlineEpochSeconds: Long,
        submitDeadlineEpochSeconds: Long,
        entryFeeAmountAtomic: String,
        requiredParticipants: Int,
        prizeAmountAtomic: String,
    ): PreparedCompetitionTransactions = error("Not used in this test.")

    override fun prepareJoinCompetition(
        paymentAsset: PaymentAssetConfig,
        competitionId: Long,
        entryFeeAmountAtomic: String,
    ): PreparedCompetitionTransactions = error("Not used in this test.")

    override fun verifyCreateCompetitionTransaction(
        txHash: String,
        expectedCreatorWallet: String,
        expectedPaymentAsset: PaymentAssetConfig,
        expectedCompetitionKey: String,
        expectedJoinDeadlineEpochSeconds: Long,
        expectedSubmitDeadlineEpochSeconds: Long,
        expectedEntryFeeAmountAtomic: String,
        expectedRequiredParticipants: Int,
        expectedPrizeAmountAtomic: String,
    ): VerifiedCompetitionCreation = error("Not used in this test.")

    override fun verifyJoinCompetitionTransaction(
        txHash: String,
        expectedParticipantWallet: String,
        expectedPaymentAsset: PaymentAssetConfig,
        expectedCompetitionId: Long,
        expectedEntryFeeAmountAtomic: String,
    ): VerifiedCompetitionJoin = error("Not used in this test.")

    override fun settleCompetition(competitionId: Long): BlockchainPlatformWriteResult =
        error("Not used in this test.")

    override fun cancelCompetition(competitionId: Long): BlockchainPlatformWriteResult =
        error("Not used in this test.")

    override fun recordSubmissionResult(record: SubmissionResultRecord): BlockchainPlatformWriteResult {
        lastRecord = record
        return response
    }

    override fun close() = Unit
}

private fun fakePlatformContractConfig(): BlockchainPlatformContractConfig {
    return BlockchainPlatformContractConfig(
        proxyAddress = "0x2222222222222222222222222222222222222222",
        operatorPrivateKey = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        gasLimit = 700_000L,
        gasPriceWei = null,
        receiptTimeoutMs = 120_000L,
        receiptPollIntervalMs = 2_000L,
        explorerTxBaseUrl = "https://sepolia.etherscan.io/tx",
        prepareIntentTtlSeconds = 900,
    )
}

private fun validNodeRun(
    nodeId: String,
    nodeUrl: String,
    secret: String,
    results: List<SandboxRunTestOutput>,
    suiteExecutionTimeMs: Int? = null,
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
        suiteExecutionTimeMs = suiteExecutionTimeMs,
        attestationPayloadHash = payloadHash,
        attestationSignature = signature,
        attestationScheme = "hmac-sha256",
        executedAt = executedAt,
        results = results,
        errorMessage = null,
    )
}

private fun acceptedAdditionResults(maxMemoryUsedKb: Int): List<SandboxRunTestOutput> {
    return listOf(
        SandboxRunTestOutput(
            id = 101,
            order = 1,
            status = "OK",
            output = "4",
            passed = true,
            executionTimeMs = 11,
            memoryUsedKb = maxMemoryUsedKb - 100,
            message = null,
        ),
        SandboxRunTestOutput(
            id = 102,
            order = 2,
            status = "OK",
            output = "12",
            passed = true,
            executionTimeMs = 19,
            memoryUsedKb = maxMemoryUsedKb,
            message = null,
        ),
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
