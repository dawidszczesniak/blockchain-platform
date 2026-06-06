package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.Instant
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemTestCaseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemTestDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunInput

internal enum class CreateProblemReferenceTestStatus {
    Ok,
    Timeout,
    Error,
}

internal data class CreateProblemReferenceTestResult(
    val index: Int,
    val status: CreateProblemReferenceTestStatus,
    val output: String?,
    val executionTimeMs: Int,
    val memoryUsedKb: Int? = null,
    val message: String?,
)

internal data class CreateProblemReferenceValidationEvidence(
    val nodeId: String?,
    val runHash: String?,
    val resultHash: String?,
    val imageHash: String?,
    val consensusNodes: Int,
    val runtimeMs: Int,
    val memoryUsedKb: Int?,
    val validatedAt: Instant,
)

internal data class CreateProblemReferenceValidationResult(
    val tests: List<CreateProblemReferenceTestResult>,
    val evidence: CreateProblemReferenceValidationEvidence,
) {
    val allSuccessful: Boolean
        get() = tests.all { it.status == CreateProblemReferenceTestStatus.Ok && it.output != null }
}

internal interface CreateProblemReferenceValidationService {
    fun validateReferenceSolution(
        referenceSolutionLanguage: String,
        referenceSolutionCode: String,
        testCases: List<CreateProblemTestCaseDto>,
        requireDeterminism: Boolean,
        validationRunId: String? = null,
        cancellation: SandboxRunCancellation? = null,
    ): CreateProblemReferenceValidationResult
}

internal class CreateProblemReferenceValidationServiceImpl(
    private val sandboxClient: SandboxClient,
    private val sandboxConsensusEvaluator: SandboxConsensusEvaluator,
) : CreateProblemReferenceValidationService {
    @Suppress("UNUSED_PARAMETER")
    override fun validateReferenceSolution(
        referenceSolutionLanguage: String,
        referenceSolutionCode: String,
        testCases: List<CreateProblemTestCaseDto>,
        requireDeterminism: Boolean,
        validationRunId: String?,
        cancellation: SandboxRunCancellation?,
    ): CreateProblemReferenceValidationResult {
        val normalizedLanguage = referenceSolutionLanguage.trim().lowercase()
        if (normalizedLanguage != CREATE_PROBLEM_SUPPORTED_LANGUAGE) {
            throw CreateProblemValidationException("Only Kotlin reference solution is supported.")
        }

        if (referenceSolutionCode.trim().isBlank()) {
            throw CreateProblemValidationException("referenceSolutionCode is required.")
        }
        if (referenceSolutionCode.length > MAX_REFERENCE_SOLUTION_CHARS) {
            throw CreateProblemValidationException(
                "Reference solution is too long. Max length is $MAX_REFERENCE_SOLUTION_CHARS characters."
            )
        }

        val tests = parseStructuredTests(testCases)
        cancellation?.throwIfCancelled()
        return executeReferenceSolution(
            referenceSolutionCode = referenceSolutionCode,
            tests = tests,
            validationRunId = validationRunId,
            cancellation = cancellation,
        )
    }

    private fun parseStructuredTests(testCases: List<CreateProblemTestCaseDto>): List<NewProblemTestDraft> {
        if (testCases.isEmpty()) {
            throw CreateProblemValidationException("At least one testCase is required.")
        }
        if (testCases.size > MAX_TEST_CASES) {
            throw CreateProblemValidationException(
                "Too many tests. Maximum supported tests count is $MAX_TEST_CASES."
            )
        }

        return testCases.mapIndexed { index, testCase ->
            val humanIndex = index + 1
            val inputData = testCase.inputData
            if (inputData.isBlank()) {
                throw CreateProblemValidationException(
                    "testCases[$humanIndex].inputData is required."
                )
            }
            if (inputData.length > MAX_TEST_INPUT_CHARS) {
                throw CreateProblemValidationException(
                    "testCases[$humanIndex].inputData is too long. Max length is $MAX_TEST_INPUT_CHARS characters."
                )
            }
            if (testCase.memoryLimitMb !in 1..MAX_TEST_MEMORY_LIMIT_MB) {
                throw CreateProblemValidationException(
                    "testCases[$humanIndex].memoryLimitMb must be in range 1..$MAX_TEST_MEMORY_LIMIT_MB."
                )
            }
            NewProblemTestDraft(
                inputData = inputData,
                expectedOutput = "",
                validatorCode = "",
                validatorLanguage = CREATE_PROBLEM_SUPPORTED_LANGUAGE,
                isHidden = testCase.isHidden,
                timeoutMs = DEFAULT_CREATE_PROBLEM_TEST_TIMEOUT_MS,
                memoryLimitMb = testCase.memoryLimitMb,
            )
        }
    }

    private fun executeReferenceSolution(
        referenceSolutionCode: String,
        tests: List<NewProblemTestDraft>,
        validationRunId: String?,
        cancellation: SandboxRunCancellation?,
    ): CreateProblemReferenceValidationResult {
        val sandboxInputs = tests.mapIndexed { index, test ->
            SandboxRunInput(
                id = (index + 1).toLong(),
                order = index + 1,
                inputData = test.inputData,
                expectedOutput = "",
                validatorCode = "",
                validatorLanguage = CREATE_PROBLEM_SUPPORTED_LANGUAGE,
                timeoutMs = test.timeoutMs,
                memoryLimitMb = test.memoryLimitMb,
            )
        }
        val sandboxResult = runCatching {
            sandboxClient.runSolutionOnAllNodes(
                sourceCode = referenceSolutionCode,
                language = CREATE_PROBLEM_SUPPORTED_LANGUAGE,
                tests = sandboxInputs,
                runId = validationRunId,
                cancellation = cancellation,
            )
        }.getOrElse { error ->
            if (error is CreateProblemValidationCancelledException) {
                throw error
            }
            throw CreateProblemValidationException(
                error.message?.ifBlank { "Reference solution execution failed." }
                    ?: "Reference solution execution failed."
            )
        }
        val consensus = runCatching {
            sandboxConsensusEvaluator.evaluate(sandboxResult)
        }.getOrElse { error ->
            if (error is CreateProblemValidationCancelledException) {
                throw error
            }
            throw CreateProblemValidationException(
                error.message?.ifBlank { "Reference solution execution failed." }
                    ?: "Reference solution execution failed."
            )
        }

        val executionByOrder = consensus.representativeNode.results.associateBy { it.order }
        val testResults = tests.indices.map { index ->
            val humanIndex = index + 1
            val execution = executionByOrder[humanIndex]
                ?: return@map CreateProblemReferenceTestResult(
                    index = humanIndex,
                    status = CreateProblemReferenceTestStatus.Error,
                    output = null,
                    executionTimeMs = 0,
                    memoryUsedKb = null,
                    message = "Sandbox returned no result for testCases[$humanIndex].",
                )

            when (execution.status.uppercase()) {
                "OK" -> CreateProblemReferenceTestResult(
                    index = humanIndex,
                    status = CreateProblemReferenceTestStatus.Ok,
                    output = normalizeCreateProblemOutput(execution.output.orEmpty()),
                    executionTimeMs = execution.executionTimeMs,
                    memoryUsedKb = execution.memoryUsedKb,
                    message = null,
                )

                "TIMEOUT" -> CreateProblemReferenceTestResult(
                    index = humanIndex,
                    status = CreateProblemReferenceTestStatus.Timeout,
                    output = null,
                    executionTimeMs = execution.executionTimeMs,
                    memoryUsedKb = execution.memoryUsedKb,
                    message = execution.message?.ifBlank { null } ?: "Execution timed out.",
                )

                else -> CreateProblemReferenceTestResult(
                    index = humanIndex,
                    status = CreateProblemReferenceTestStatus.Error,
                    output = null,
                    executionTimeMs = execution.executionTimeMs,
                    memoryUsedKb = execution.memoryUsedKb,
                    message = execution.message?.ifBlank { null } ?: "Execution failed.",
                )
            }
        }

        return CreateProblemReferenceValidationResult(
            tests = testResults,
            evidence = CreateProblemReferenceValidationEvidence(
                nodeId = consensus.representativeNode.nodeId,
                runHash = consensus.representativeNode.runHash,
                resultHash = consensus.resultHash,
                imageHash = consensus.imageHash,
                consensusNodes = consensus.consensusReached,
                runtimeMs = consensus.runtimeMs,
                memoryUsedKb = consensus.memoryUsedKb,
                validatedAt = Instant.now(),
            ),
        )
    }
}

internal fun normalizeCreateProblemOutput(raw: String): String {
    return raw.replace("\r\n", "\n").trimEnd()
}

internal const val CREATE_PROBLEM_SUPPORTED_LANGUAGE = "kotlin"
internal const val MAX_TEST_CASES = 100
internal const val DEFAULT_CREATE_PROBLEM_TEST_TIMEOUT_MS = 1_000
internal const val MAX_TEST_MEMORY_LIMIT_MB = 2048
internal const val MAX_REFERENCE_SOLUTION_CHARS = 120_000
internal const val MAX_TEST_INPUT_CHARS = 64_000
