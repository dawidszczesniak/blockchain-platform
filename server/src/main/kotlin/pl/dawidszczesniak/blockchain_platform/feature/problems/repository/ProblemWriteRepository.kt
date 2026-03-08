package pl.dawidszczesniak.blockchain_platform.feature.problems.repository

import java.time.LocalDate

internal data class NewProblemDraft(
    val title: String,
    val description: String,
    val prizeAmount: Long,
    val entryFeeAmount: Long,
    val requiredParticipants: Int,
    val joinUntilDate: LocalDate,
    val submitUntilDate: LocalDate,
    val tests: List<String>,
)

internal interface ProblemWriteRepository {
    fun createProblemForDefaultUser(draft: NewProblemDraft): Int
}
