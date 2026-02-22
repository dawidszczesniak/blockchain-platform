package pl.dawidszczesniak.blockchain_platform.presentation.create

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.round

const val MAX_CREATE_PROBLEM_TESTS = 10

data class CreateProblemTest(
    val id: Int,
    val code: String,
    val expanded: Boolean,
)

data class CreateProblemState(
    val prize: String = "",
    val participants: String = "",
    val entryFee: String = "",
    val description: String = "",
    val joinUntilDate: String = "",
    val submitUntilDate: String = "",
    val tests: List<CreateProblemTest> = listOf(
        CreateProblemTest(id = 1, code = "", expanded = true)
    ),
    val nextTestId: Int = 2,
) {
    val prizeValue: Double
        get() = parseAmount(prize)

    val entryFeeValue: Double
        get() = parseAmount(entryFee)

    val participantsValue: Int
        get() = participants.trim().toIntOrNull() ?: 0

    val grossRevenue: Double
        get() = entryFeeValue * participantsValue

    val platformFee: Double
        get() = grossRevenue * 0.02

    val netRevenue: Double
        get() = grossRevenue - prizeValue - platformFee

    val canAddTest: Boolean
        get() = tests.size < MAX_CREATE_PROBLEM_TESTS
}

sealed interface CreateProblemIntent {
    data class PrizeChanged(val value: String) : CreateProblemIntent
    data class ParticipantsChanged(val value: String) : CreateProblemIntent
    data class EntryFeeChanged(val value: String) : CreateProblemIntent
    data class DescriptionChanged(val value: String) : CreateProblemIntent
    data class JoinUntilChanged(val value: String) : CreateProblemIntent
    data class SubmitUntilChanged(val value: String) : CreateProblemIntent
    data object AddTest : CreateProblemIntent
    data class ToggleTest(val id: Int) : CreateProblemIntent
    data class RemoveTest(val id: Int) : CreateProblemIntent
    data class TestCodeChanged(val id: Int, val value: String) : CreateProblemIntent
}

class CreateProblemViewModel {
    private val _state = MutableStateFlow(CreateProblemState())
    val state: StateFlow<CreateProblemState> = _state.asStateFlow()

    fun onIntent(intent: CreateProblemIntent) {
        when (intent) {
            is CreateProblemIntent.PrizeChanged -> {
                _state.update { current -> current.copy(prize = intent.value) }
            }

            is CreateProblemIntent.ParticipantsChanged -> {
                _state.update { current -> current.copy(participants = intent.value) }
            }

            is CreateProblemIntent.EntryFeeChanged -> {
                _state.update { current -> current.copy(entryFee = intent.value) }
            }

            is CreateProblemIntent.DescriptionChanged -> {
                _state.update { current -> current.copy(description = intent.value) }
            }

            is CreateProblemIntent.JoinUntilChanged -> {
                _state.update { current -> current.copy(joinUntilDate = intent.value) }
            }

            is CreateProblemIntent.SubmitUntilChanged -> {
                _state.update { current -> current.copy(submitUntilDate = intent.value) }
            }

            CreateProblemIntent.AddTest -> {
                _state.update { current ->
                    if (!current.canAddTest) {
                        current
                    } else {
                        current.copy(
                            tests = current.tests + CreateProblemTest(
                                id = current.nextTestId,
                                code = "",
                                expanded = true,
                            ),
                            nextTestId = current.nextTestId + 1,
                        )
                    }
                }
            }

            is CreateProblemIntent.ToggleTest -> {
                _state.update { current ->
                    current.copy(
                        tests = current.tests.map { test ->
                            if (test.id == intent.id) {
                                test.copy(expanded = !test.expanded)
                            } else {
                                test
                            }
                        }
                    )
                }
            }

            is CreateProblemIntent.RemoveTest -> {
                _state.update { current ->
                    if (current.tests.size <= 1) {
                        current
                    } else {
                        current.copy(
                            tests = current.tests.filterNot { it.id == intent.id }
                        )
                    }
                }
            }

            is CreateProblemIntent.TestCodeChanged -> {
                _state.update { current ->
                    current.copy(
                        tests = current.tests.map { test ->
                            if (test.id == intent.id) {
                                test.copy(code = intent.value)
                            } else {
                                test
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun parseAmount(value: String): Double {
    val normalized = value.replace(",", ".").trim()
    return normalized.toDoubleOrNull() ?: 0.0
}

fun formatAmount(value: Double): String {
    val rounded = round(value * 100) / 100
    val asLong = rounded.toLong().toDouble()
    return if (abs(rounded - asLong) < 0.0001) {
        asLong.toLong().toString()
    } else {
        rounded.toString()
    }
}
