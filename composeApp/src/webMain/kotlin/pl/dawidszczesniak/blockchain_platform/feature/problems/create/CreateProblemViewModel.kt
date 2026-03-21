package pl.dawidszczesniak.blockchain_platform.feature.problems.create

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ProblemExampleDto
import kotlin.math.abs
import kotlin.math.round

const val MAX_CREATE_PROBLEM_TESTS = 10
const val MIN_CREATE_PROBLEM_EXAMPLES = 3
const val MAX_CREATE_PROBLEM_EXAMPLES = 10

data class CreateProblemTest(
    val id: Int,
    val code: String,
    val expanded: Boolean,
)

data class CreateProblemExample(
    val id: Int,
    val input: String,
    val output: String,
    val explanation: String,
    val expanded: Boolean,
)

data class CreateProblemExampleValidation(
    val input: CreateProblemValidationError? = null,
    val output: CreateProblemValidationError? = null,
    val explanation: CreateProblemValidationError? = null,
)

enum class CreateProblemValidationError {
    Required,
    InvalidInteger,
    MustBePositive,
    MustBeNonNegative,
    InvalidDate,
    SubmitBeforeJoin,
}

data class CreateProblemValidation(
    val prize: CreateProblemValidationError? = null,
    val participants: CreateProblemValidationError? = null,
    val entryFee: CreateProblemValidationError? = null,
    val description: CreateProblemValidationError? = null,
    val joinUntilDate: CreateProblemValidationError? = null,
    val submitUntilDate: CreateProblemValidationError? = null,
    val testsById: Map<Int, CreateProblemValidationError> = emptyMap(),
    val examplesById: Map<Int, CreateProblemExampleValidation> = emptyMap(),
) {
    val hasErrors: Boolean
        get() = prize != null ||
            participants != null ||
            entryFee != null ||
            description != null ||
            joinUntilDate != null ||
            submitUntilDate != null ||
            testsById.isNotEmpty() ||
            examplesById.isNotEmpty()
}

data class CreateProblemState(
    val prize: String = "",
    val participants: String = "",
    val entryFee: String = "",
    val description: String = "",
    val constraints: String = "",
    val joinUntilDate: String = "",
    val submitUntilDate: String = "",
    val tests: List<CreateProblemTest> = listOf(
        CreateProblemTest(id = 1, code = "", expanded = true)
    ),
    val examples: List<CreateProblemExample> = defaultExamples(),
    val nextTestId: Int = 2,
    val nextExampleId: Int = MIN_CREATE_PROBLEM_EXAMPLES + 1,
    val submitAttempted: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
    val submitErrorMessage: String? = null,
    val submitSuccessProblemId: Int? = null,
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

    val canAddExample: Boolean
        get() = examples.size < MAX_CREATE_PROBLEM_EXAMPLES

    val validation: CreateProblemValidation
        get() = validateCreateProblem(this)
}

sealed interface CreateProblemIntent {
    data class PrizeChanged(val value: String) : CreateProblemIntent
    data class ParticipantsChanged(val value: String) : CreateProblemIntent
    data class EntryFeeChanged(val value: String) : CreateProblemIntent
    data class DescriptionChanged(val value: String) : CreateProblemIntent
    data class ConstraintsChanged(val value: String) : CreateProblemIntent
    data class JoinUntilChanged(val value: String) : CreateProblemIntent
    data class SubmitUntilChanged(val value: String) : CreateProblemIntent
    data object AddTest : CreateProblemIntent
    data class ToggleTest(val id: Int) : CreateProblemIntent
    data class RemoveTest(val id: Int) : CreateProblemIntent
    data class TestCodeChanged(val id: Int, val value: String) : CreateProblemIntent
    data object AddExample : CreateProblemIntent
    data class ToggleExample(val id: Int) : CreateProblemIntent
    data class RemoveExample(val id: Int) : CreateProblemIntent
    data class ExampleInputChanged(val id: Int, val value: String) : CreateProblemIntent
    data class ExampleOutputChanged(val id: Int, val value: String) : CreateProblemIntent
    data class ExampleExplanationChanged(val id: Int, val value: String) : CreateProblemIntent
    data object Submit : CreateProblemIntent
}

class CreateProblemViewModel(
    private val createProblemUseCase: CreateProblemUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(CreateProblemState())
    val state: StateFlow<CreateProblemState> = _state.asStateFlow()

    fun onIntent(intent: CreateProblemIntent) {
        when (intent) {
            is CreateProblemIntent.PrizeChanged -> {
                val normalized = intent.value.filter { it.isDigit() }
                _state.update { current ->
                    current.copy(prize = normalized).clearSubmissionFeedback()
                }
            }

            is CreateProblemIntent.ParticipantsChanged -> {
                val normalized = intent.value.filter { it.isDigit() }
                _state.update { current ->
                    current.copy(participants = normalized).clearSubmissionFeedback()
                }
            }

            is CreateProblemIntent.EntryFeeChanged -> {
                val normalized = intent.value.filter { it.isDigit() }
                _state.update { current ->
                    current.copy(entryFee = normalized).clearSubmissionFeedback()
                }
            }

            is CreateProblemIntent.DescriptionChanged -> {
                _state.update { current ->
                    current.copy(description = intent.value).clearSubmissionFeedback()
                }
            }

            is CreateProblemIntent.ConstraintsChanged -> {
                _state.update { current ->
                    current.copy(constraints = intent.value).clearSubmissionFeedback()
                }
            }

            is CreateProblemIntent.JoinUntilChanged -> {
                _state.update { current ->
                    current.copy(joinUntilDate = intent.value).clearSubmissionFeedback()
                }
            }

            is CreateProblemIntent.SubmitUntilChanged -> {
                _state.update { current ->
                    current.copy(submitUntilDate = intent.value).clearSubmissionFeedback()
                }
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
                        ).clearSubmissionFeedback()
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
                        ).clearSubmissionFeedback()
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
                    ).clearSubmissionFeedback()
                }
            }

            CreateProblemIntent.AddExample -> {
                _state.update { current ->
                    if (!current.canAddExample) {
                        current
                    } else {
                        current.copy(
                            examples = current.examples + CreateProblemExample(
                                id = current.nextExampleId,
                                input = "",
                                output = "",
                                explanation = "",
                                expanded = true,
                            ),
                            nextExampleId = current.nextExampleId + 1,
                        ).clearSubmissionFeedback()
                    }
                }
            }

            is CreateProblemIntent.ToggleExample -> {
                _state.update { current ->
                    current.copy(
                        examples = current.examples.map { example ->
                            if (example.id == intent.id) {
                                example.copy(expanded = !example.expanded)
                            } else {
                                example
                            }
                        }
                    )
                }
            }

            is CreateProblemIntent.RemoveExample -> {
                _state.update { current ->
                    if (current.examples.size <= MIN_CREATE_PROBLEM_EXAMPLES) {
                        current
                    } else {
                        current.copy(
                            examples = current.examples.filterNot { it.id == intent.id }
                        ).clearSubmissionFeedback()
                    }
                }
            }

            is CreateProblemIntent.ExampleInputChanged -> {
                _state.update { current ->
                    current.copy(
                        examples = current.examples.map { example ->
                            if (example.id == intent.id) {
                                example.copy(input = intent.value)
                            } else {
                                example
                            }
                        }
                    ).clearSubmissionFeedback()
                }
            }

            is CreateProblemIntent.ExampleOutputChanged -> {
                _state.update { current ->
                    current.copy(
                        examples = current.examples.map { example ->
                            if (example.id == intent.id) {
                                example.copy(output = intent.value)
                            } else {
                                example
                            }
                        }
                    ).clearSubmissionFeedback()
                }
            }

            is CreateProblemIntent.ExampleExplanationChanged -> {
                _state.update { current ->
                    current.copy(
                        examples = current.examples.map { example ->
                            if (example.id == intent.id) {
                                example.copy(explanation = intent.value)
                            } else {
                                example
                            }
                        }
                    ).clearSubmissionFeedback()
                }
            }

            CreateProblemIntent.Submit -> submit()
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun submit() {
        val snapshot = state.value
        if (snapshot.validation.hasErrors) {
            _state.update { current ->
                current.copy(
                    submitAttempted = true,
                    submitFailed = false,
                    submitErrorMessage = null,
                    submitSuccessProblemId = null,
                )
            }
            return
        }

        val request = snapshot.toCreateProblemRequest()
        _state.update { current ->
            current.copy(
                submitAttempted = true,
                isSubmitting = true,
                submitFailed = false,
                submitErrorMessage = null,
                submitSuccessProblemId = null,
            )
        }

        scope.launch {
            runCatching {
                createProblemUseCase(request)
            }.onSuccess { createdProblemId ->
                _state.value = CreateProblemState(
                    submitSuccessProblemId = createdProblemId,
                )
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        isSubmitting = false,
                        submitFailed = true,
                        submitErrorMessage = extractReadableErrorMessage(error),
                        submitSuccessProblemId = null,
                    )
                }
            }
        }
    }
}

private fun validateCreateProblem(state: CreateProblemState): CreateProblemValidation {
    val prizeRaw = state.prize.trim()
    var prizeError: CreateProblemValidationError? = null
    if (prizeRaw.isEmpty()) {
        prizeError = CreateProblemValidationError.Required
    } else {
        val parsedPrize = prizeRaw.toLongOrNull()
        prizeError = when {
            parsedPrize == null -> CreateProblemValidationError.InvalidInteger
            parsedPrize < 0L -> CreateProblemValidationError.MustBeNonNegative
            else -> null
        }
    }

    val participantsRaw = state.participants.trim()
    var participantsError: CreateProblemValidationError? = null
    if (participantsRaw.isEmpty()) {
        participantsError = CreateProblemValidationError.Required
    } else {
        val parsedParticipants = participantsRaw.toIntOrNull()
        participantsError = when {
            parsedParticipants == null -> CreateProblemValidationError.InvalidInteger
            parsedParticipants <= 0 -> CreateProblemValidationError.MustBePositive
            else -> null
        }
    }

    val entryFeeRaw = state.entryFee.trim()
    var entryFeeError: CreateProblemValidationError? = null
    if (entryFeeRaw.isEmpty()) {
        entryFeeError = CreateProblemValidationError.Required
    } else {
        val parsedEntryFee = entryFeeRaw.toLongOrNull()
        entryFeeError = when {
            parsedEntryFee == null -> CreateProblemValidationError.InvalidInteger
            parsedEntryFee < 0L -> CreateProblemValidationError.MustBeNonNegative
            else -> null
        }
    }

    val descriptionError = if (state.description.trim().isEmpty()) {
        CreateProblemValidationError.Required
    } else {
        null
    }

    val joinRaw = state.joinUntilDate.trim()
    var joinError: CreateProblemValidationError? = null
    if (joinRaw.isEmpty()) {
        joinError = CreateProblemValidationError.Required
    } else if (!isValidIsoDate(joinRaw)) {
        joinError = CreateProblemValidationError.InvalidDate
    }

    val submitRaw = state.submitUntilDate.trim()
    var submitError: CreateProblemValidationError? = null
    if (submitRaw.isEmpty()) {
        submitError = CreateProblemValidationError.Required
    } else if (!isValidIsoDate(submitRaw)) {
        submitError = CreateProblemValidationError.InvalidDate
    } else if (joinError == null && submitRaw <= joinRaw) {
        submitError = CreateProblemValidationError.SubmitBeforeJoin
    }

    val testErrors = buildMap<Int, CreateProblemValidationError> {
        state.tests.forEach { test ->
            if (test.code.trim().isEmpty()) {
                put(test.id, CreateProblemValidationError.Required)
            }
        }
    }

    val exampleErrors = buildMap<Int, CreateProblemExampleValidation> {
        state.examples.forEach { example ->
            val inputError = if (example.input.trim().isEmpty()) {
                CreateProblemValidationError.Required
            } else {
                null
            }
            val outputError = if (example.output.trim().isEmpty()) {
                CreateProblemValidationError.Required
            } else {
                null
            }
            val explanationError = if (example.explanation.trim().isEmpty()) {
                CreateProblemValidationError.Required
            } else {
                null
            }
            if (inputError != null || outputError != null || explanationError != null) {
                put(
                    example.id,
                    CreateProblemExampleValidation(
                        input = inputError,
                        output = outputError,
                        explanation = explanationError,
                    )
                )
            }
        }
    }

    return CreateProblemValidation(
        prize = prizeError,
        participants = participantsError,
        entryFee = entryFeeError,
        description = descriptionError,
        joinUntilDate = joinError,
        submitUntilDate = submitError,
        testsById = testErrors,
        examplesById = exampleErrors,
    )
}

private fun CreateProblemState.toCreateProblemRequest(): CreateProblemRequestDto {
    require(!validation.hasErrors) {
        "CreateProblemRequestDto can be built only from valid state."
    }
    val statementExamples = examples.map { example ->
        ProblemExampleDto(
            input = example.input,
            output = example.output,
            explanation = example.explanation,
        )
    }
    return CreateProblemRequestDto(
        description = description.trim(),
        constraints = constraints.trim(),
        examples = statementExamples,
        prizeAmount = prize.trim().toLong(),
        entryFeeAmount = entryFee.trim().toLong(),
        requiredParticipants = participants.trim().toInt(),
        joinUntilDate = joinUntilDate.trim(),
        submitUntilDate = submitUntilDate.trim(),
        tests = tests.map { it.code.trim() },
    )
}

private fun CreateProblemState.clearSubmissionFeedback(): CreateProblemState {
    return copy(
        isSubmitting = false,
        submitFailed = false,
        submitErrorMessage = null,
        submitSuccessProblemId = null,
    )
}

private fun isValidIsoDate(value: String): Boolean {
    if (value.length != 10 || value[4] != '-' || value[7] != '-') {
        return false
    }
    val year = value.substring(0, 4).toIntOrNull() ?: return false
    val month = value.substring(5, 7).toIntOrNull() ?: return false
    val day = value.substring(8, 10).toIntOrNull() ?: return false
    if (month !in 1..12) {
        return false
    }
    val maxDay = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> return false
    }
    return day in 1..maxDay
}

private fun isLeapYear(year: Int): Boolean {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}

private fun extractReadableErrorMessage(error: Throwable): String? {
    val raw = error.message?.trim().orEmpty()
    if (raw.isEmpty()) {
        return null
    }
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start >= 0 && end > start) {
        val jsonPart = raw.substring(start, end + 1)
        val parsed = runCatching {
            Json.parseToJsonElement(jsonPart)
                .jsonObject["message"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
        }.getOrNull()
        if (!parsed.isNullOrEmpty()) {
            return parsed
        }
    }
    return raw
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

private fun defaultExamples(): List<CreateProblemExample> {
    return List(MIN_CREATE_PROBLEM_EXAMPLES) { index ->
        CreateProblemExample(
            id = index + 1,
            input = "",
            output = "",
            explanation = "",
            expanded = true,
        )
    }
}
