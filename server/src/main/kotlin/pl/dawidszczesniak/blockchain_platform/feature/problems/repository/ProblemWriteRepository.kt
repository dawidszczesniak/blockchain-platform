package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import java.time.LocalDate

internal data class NewProblemTestDraft(
    val inputData: String,
    val expectedOutput: String,
    val validatorCode: String,
    val isHidden: Boolean,
    val timeoutMs: Int,
    val memoryLimitMb: Int,
)

internal data class NewProblemExampleDraft(
    val input: String,
    val output: String,
    val explanation: String,
)

internal data class NewProblemDraft(
    val title: String,
    val description: String,
    val constraints: String,
    val examples: List<NewProblemExampleDraft>,
    val prizeAmount: Long,
    val entryFeeAmount: Long,
    val requiredParticipants: Int,
    val joinUntilDate: LocalDate,
    val submitUntilDate: LocalDate,
    val tests: List<NewProblemTestDraft>,
)

internal data class JoinProblemResult(
    val joined: Boolean,
    val registeredParticipants: Int,
    val requiredParticipants: Int,
)

internal interface ProblemWriteRepository {
    fun createProblemForUser(userId: Long, draft: NewProblemDraft): Int
    fun registerUserForProblem(userId: Long, problemId: Int): JoinProblemResult
}
