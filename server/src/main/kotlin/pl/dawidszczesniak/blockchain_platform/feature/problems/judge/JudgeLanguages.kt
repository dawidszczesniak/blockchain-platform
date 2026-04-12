package pl.dawidszczesniak.blockchain_platform.feature.problems.judge

import kotlin.math.ceil
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionTest
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunInput

internal data class JudgeLanguageProfile(
    val id: String,
    val displayName: String,
    val timeoutMultiplier: Double,
    val memoryMultiplier: Double,
) {
    fun applyTo(test: ProblemExecutionTest): SandboxRunInput {
        return SandboxRunInput(
            id = test.id,
            order = test.order,
            inputData = test.inputData,
            expectedOutput = test.expectedOutput,
            validatorCode = test.validatorCode,
            validatorLanguage = test.validatorLanguage,
            timeoutMs = ceil(test.timeoutMs * timeoutMultiplier).toInt().coerceAtLeast(1),
            memoryLimitMb = ceil(test.memoryLimitMb * memoryMultiplier).toInt().coerceAtLeast(1),
        )
    }
}

internal object JudgeLanguages {
    private val profiles = listOf(
        JudgeLanguageProfile(
            id = "kotlin",
            displayName = "Kotlin",
            timeoutMultiplier = 1.0,
            memoryMultiplier = 1.0,
        ),
        JudgeLanguageProfile(
            id = "java",
            displayName = "Java",
            timeoutMultiplier = 1.0,
            memoryMultiplier = 1.0,
        ),
    )

    fun requireSupported(rawLanguage: String): JudgeLanguageProfile {
        val normalized = rawLanguage.trim().lowercase()
        return profiles.firstOrNull { it.id == normalized }
            ?: error("Supported languages: ${profiles.joinToString { it.displayName }}.")
    }

    fun supportedIds(): Set<String> = profiles.mapTo(linkedSetOf()) { it.id }
}
