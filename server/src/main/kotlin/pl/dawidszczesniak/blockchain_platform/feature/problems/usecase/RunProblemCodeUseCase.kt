package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionTest
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunInput

internal class RunProblemValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface RunProblemCodeUseCase {
    operator fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): RunProblemResponseDto
}

internal class RunProblemCodeUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val sandboxClient: SandboxClient,
) : RunProblemCodeUseCase {
    override fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): RunProblemResponseDto {
        val language = request.language.trim().lowercase()
        if (language != SUPPORTED_LANGUAGE) {
            throw RunProblemValidationException("Only Kotlin language is supported.")
        }
        val sourceCode = request.sourceCode.trim()
        if (sourceCode.isBlank()) {
            throw RunProblemValidationException("Source code cannot be empty.")
        }
        if (sourceCode.length > MAX_SOURCE_CODE_CHARS) {
            throw RunProblemValidationException(
                "Source code is too long. Max length is $MAX_SOURCE_CODE_CHARS characters."
            )
        }

        val context = runCatching {
            repository.fetchExecutionContextForUser(
                userId = userId,
                problemId = problemId,
            )
        }.getOrElse { error ->
            throw RunProblemValidationException(
                error.message?.ifBlank { "Cannot run this solution." } ?: "Cannot run this solution."
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
        val sandboxResult = runCatching {
            sandboxClient.runSolution(
                sourceCode = sourceCode,
                tests = sandboxInputs,
            )
        }.getOrElse { error ->
            throw RunProblemValidationException(
                error.message?.ifBlank { "Sandbox execution failed." } ?: "Sandbox execution failed."
            )
        }

        val outputByTestId = sandboxResult.results.associateBy { it.id }
        val testResults = context.tests.map { test ->
            val execution = outputByTestId[test.id]
            if (execution == null) {
                return@map test.toMissingSandboxResult()
            }
            when (execution.status.uppercase()) {
                "OK" -> {
                    val fallbackPassed = when {
                        execution.passed != null -> null
                        test.expectedOutput.isNotBlank() -> {
                            normalizeOutput(execution.output ?: "") == normalizeOutput(test.expectedOutput)
                        }
                        else -> false
                    }
                    val passed = execution.passed ?: fallbackPassed ?: false
                    val failureMessage = execution.message
                        ?: if (test.validatorCode.isNotBlank() && execution.passed == null) {
                            "Sandbox node does not expose validator verdict for this test."
                        } else {
                            "Output does not match expected value."
                        }
                    test.toDto(
                        status = if (passed) RunStatus.Passed else RunStatus.Failed,
                        passed = passed,
                        executionTimeMs = execution.executionTimeMs,
                        actualOutput = execution.output,
                        message = if (passed) null else failureMessage,
                    )
                }

                "TIMEOUT" -> {
                    test.toDto(
                        status = RunStatus.Timeout,
                        passed = false,
                        executionTimeMs = execution.executionTimeMs,
                        actualOutput = null,
                        message = execution.message ?: "Execution timed out.",
                    )
                }

                else -> {
                    test.toDto(
                        status = RunStatus.Error,
                        passed = false,
                        executionTimeMs = execution.executionTimeMs,
                        actualOutput = null,
                        message = execution.message ?: "Sandbox execution error.",
                    )
                }
            }
        }

        val passedCount = testResults.count { it.passed }
        return RunProblemResponseDto(
            total = testResults.size,
            passed = passedCount,
            allPassed = passedCount == testResults.size,
            results = testResults,
            sandboxNodeId = sandboxResult.nodeId,
            sandboxImageHash = sandboxResult.imageHash,
            sandboxRunHash = sandboxResult.runHash,
        )
    }
}

private enum class RunStatus {
    Passed,
    Failed,
    Error,
    Timeout,
}

private fun ProblemExecutionTest.toMissingSandboxResult(): RunProblemTestResultDto {
    return toDto(
        status = RunStatus.Error,
        passed = false,
        executionTimeMs = 0,
        actualOutput = null,
        message = "Sandbox returned no result for this test.",
    )
}

private fun ProblemExecutionTest.toDto(
    status: RunStatus,
    passed: Boolean,
    executionTimeMs: Int,
    actualOutput: String?,
    message: String?,
): RunProblemTestResultDto {
    val expectedOutputForDisplay = expectedOutput.takeIf { it.isNotBlank() }
    val safeMessage = when {
        isHidden && !passed && status == RunStatus.Failed -> HIDDEN_TEST_FAILED_MESSAGE
        else -> message
    }
    return RunProblemTestResultDto(
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
}

private fun normalizeOutput(raw: String): String {
    return raw.replace("\r\n", "\n").trimEnd()
}

private const val SUPPORTED_LANGUAGE = "kotlin"
private const val MAX_SOURCE_CODE_CHARS = 120_000
private const val HIDDEN_TEST_FAILED_MESSAGE = "Hidden test failed."
