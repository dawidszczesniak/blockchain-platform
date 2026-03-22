package pl.dawidszczesniak.blockchain_platform.feature.problems.create

import kotlin.math.abs
import kotlin.math.round
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
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemValidationTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemTestCaseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto

const val MAX_CREATE_PROBLEM_TESTS = 50
const val MIN_CREATE_PROBLEM_TESTS = 1
const val MIN_PUBLIC_CREATE_PROBLEM_TESTS = 1

data class CreateProblemTest(
    val id: Int,
    val input: String,
    val isHidden: Boolean,
    val expanded: Boolean,
)

data class CreateProblemTestValidation(
    val input: CreateProblemValidationError? = null,
)

data class CreateProblemTestRunResult(
    val status: String,
    val output: String?,
    val executionTimeMs: Int,
    val message: String?,
)

enum class CreateProblemValidationError {
    Required,
    InvalidInteger,
    MustBePositive,
    MustBeNonNegative,
    InvalidDate,
    SubmitBeforeJoin,
    MinPublicTests,
}

data class CreateProblemValidation(
    val prize: CreateProblemValidationError? = null,
    val participants: CreateProblemValidationError? = null,
    val entryFee: CreateProblemValidationError? = null,
    val title: CreateProblemValidationError? = null,
    val description: CreateProblemValidationError? = null,
    val referenceSolution: CreateProblemValidationError? = null,
    val joinUntilDate: CreateProblemValidationError? = null,
    val submitUntilDate: CreateProblemValidationError? = null,
    val publicTests: CreateProblemValidationError? = null,
    val testsById: Map<Int, CreateProblemTestValidation> = emptyMap(),
) {
    val hasErrors: Boolean
        get() = prize != null ||
            participants != null ||
            entryFee != null ||
            title != null ||
            description != null ||
            referenceSolution != null ||
            joinUntilDate != null ||
            submitUntilDate != null ||
            publicTests != null ||
            testsById.isNotEmpty()
}

data class CreateProblemState(
    val prize: String = "",
    val participants: String = "",
    val entryFee: String = "",
    val title: String = "",
    val description: String = "",
    val constraints: String = "",
    val referenceSolutionCode: String = "",
    val joinUntilDate: String = "",
    val submitUntilDate: String = "",
    val tests: List<CreateProblemTest> = listOf(
        CreateProblemTest(
            id = 1,
            input = "",
            isHidden = false,
            expanded = false,
        ),
    ),
    val nextTestId: Int = 2,
    val runningTestIds: Set<Int> = emptySet(),
    val isRunningAllTests: Boolean = false,
    val runErrorMessage: String? = null,
    val testRunResultsById: Map<Int, CreateProblemTestRunResult> = emptyMap(),
    val validatedSnapshotHash: String? = null,
    val submitAttempted: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitFailed: Boolean = false,
    val requiresFreshValidationForSubmit: Boolean = false,
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

    val validationSnapshotHash: String
        get() = buildCreateProblemValidationSnapshotHash(this)

    val isValidationFresh: Boolean
        get() = validatedSnapshotHash != null && validatedSnapshotHash == validationSnapshotHash

    val canSubmit: Boolean
        get() = !validation.hasErrors &&
            !isSubmitting &&
            !isRunningAllTests &&
            runningTestIds.isEmpty() &&
            isValidationFresh

    val validation: CreateProblemValidation
        get() = validateCreateProblem(this)
}

sealed interface CreateProblemIntent {
    data class PrizeChanged(val value: String) : CreateProblemIntent
    data class ParticipantsChanged(val value: String) : CreateProblemIntent
    data class EntryFeeChanged(val value: String) : CreateProblemIntent
    data class TitleChanged(val value: String) : CreateProblemIntent
    data class DescriptionChanged(val value: String) : CreateProblemIntent
    data class ConstraintsChanged(val value: String) : CreateProblemIntent
    data class ReferenceSolutionChanged(val value: String) : CreateProblemIntent
    data class JoinUntilChanged(val value: String) : CreateProblemIntent
    data class SubmitUntilChanged(val value: String) : CreateProblemIntent
    data object AddTest : CreateProblemIntent
    data class ToggleTest(val id: Int) : CreateProblemIntent
    data class RemoveTest(val id: Int) : CreateProblemIntent
    data class TestInputChanged(val id: Int, val value: String) : CreateProblemIntent
    data class TestHiddenChanged(val id: Int, val value: Boolean) : CreateProblemIntent
    data class RunSingleTest(val id: Int) : CreateProblemIntent
    data object RunAllTests : CreateProblemIntent
    data object Submit : CreateProblemIntent
}

class CreateProblemViewModel(
    private val createProblemUseCase: CreateProblemUseCase,
    private val validateCreateProblemUseCase: ValidateCreateProblemUseCase,
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

            is CreateProblemIntent.TitleChanged -> {
                _state.update { current ->
                    current.copy(title = intent.value).clearSubmissionFeedback()
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

            is CreateProblemIntent.ReferenceSolutionChanged -> {
                _state.update { current ->
                    current.copy(referenceSolutionCode = intent.value).clearSubmissionAndValidationFeedback()
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
                                input = "",
                                isHidden = true,
                                expanded = false,
                            ),
                            nextTestId = current.nextTestId + 1,
                        ).clearSubmissionAndValidationFeedback()
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
                    if (current.tests.size <= MIN_CREATE_PROBLEM_TESTS) {
                        current
                    } else {
                        current.copy(
                            tests = current.tests.filterNot { it.id == intent.id }
                        ).clearSubmissionAndValidationFeedback()
                    }
                }
            }

            is CreateProblemIntent.TestInputChanged -> {
                _state.update { current ->
                    current.copy(
                        tests = current.tests.map { test ->
                            if (test.id == intent.id) {
                                test.copy(input = intent.value)
                            } else {
                                test
                            }
                        }
                    ).clearSubmissionAndValidationFeedback()
                }
            }

            is CreateProblemIntent.TestHiddenChanged -> {
                _state.update { current ->
                    current.copy(
                        tests = current.tests.map { test ->
                            if (test.id == intent.id) {
                                test.copy(isHidden = intent.value)
                            } else {
                                test
                            }
                        }
                    ).clearSubmissionAndValidationFeedback()
                }
            }

            is CreateProblemIntent.RunSingleTest -> runSingleTest(intent.id)
            CreateProblemIntent.RunAllTests -> runAllTests()
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
                    requiresFreshValidationForSubmit = false,
                    submitErrorMessage = null,
                    submitSuccessProblemId = null,
                )
            }
            return
        }
        if (!snapshot.isValidationFresh) {
            _state.update { current ->
                current.copy(
                    submitAttempted = true,
                    submitFailed = true,
                    requiresFreshValidationForSubmit = true,
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
                requiresFreshValidationForSubmit = false,
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
                        requiresFreshValidationForSubmit = false,
                        submitErrorMessage = extractReadableErrorMessage(error),
                        submitSuccessProblemId = null,
                    )
                }
            }
        }
    }

    private fun runSingleTest(testId: Int) {
        val snapshot = state.value
        if (snapshot.isRunningAllTests || snapshot.runningTestIds.contains(testId) || snapshot.isSubmitting) {
            return
        }
        val selectedTest = snapshot.tests.firstOrNull { it.id == testId } ?: return
        val requestSnapshotHash = snapshot.validationSnapshotHash
        val request = snapshot.toValidateCreateProblemRequest(selectedTestIds = setOf(testId))

        _state.update { current ->
            current.copy(
                runningTestIds = current.runningTestIds + testId,
                runErrorMessage = null,
                submitFailed = false,
                requiresFreshValidationForSubmit = false,
                submitErrorMessage = null,
                submitSuccessProblemId = null,
            )
        }

        scope.launch {
            runCatching { validateCreateProblemUseCase(request) }
                .onSuccess { response ->
                    val apiResult = response.results.firstOrNull()
                    _state.update { current ->
                        val resultMap = current.testRunResultsById.toMutableMap()
                        if (apiResult != null && current.validationSnapshotHash == requestSnapshotHash) {
                            resultMap[testId] = apiResult.toUiResult()
                        }
                        val staleResult = current.validationSnapshotHash != requestSnapshotHash
                        val singleTestValidated = !staleResult &&
                            current.tests.size == 1 &&
                            response.allSuccessful &&
                            apiResult != null
                        current.copy(
                            runningTestIds = current.runningTestIds - testId,
                            runErrorMessage = when {
                                staleResult -> current.runErrorMessage
                                apiResult == null -> "Validation returned no result for test ${selectedTest.id}."
                                else -> null
                            },
                            testRunResultsById = if (staleResult) current.testRunResultsById else resultMap,
                            validatedSnapshotHash = if (singleTestValidated) requestSnapshotHash else current.validatedSnapshotHash,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            runningTestIds = current.runningTestIds - testId,
                            runErrorMessage = extractReadableErrorMessage(error),
                        )
                    }
                }
        }
    }

    private fun runAllTests() {
        val snapshot = state.value
        if (snapshot.isRunningAllTests || snapshot.runningTestIds.isNotEmpty() || snapshot.isSubmitting) {
            return
        }
        val requestSnapshotHash = snapshot.validationSnapshotHash
        val request = snapshot.toValidateCreateProblemRequest(selectedTestIds = null)
        _state.update { current ->
            current.copy(
                isRunningAllTests = true,
                runErrorMessage = null,
                submitFailed = false,
                requiresFreshValidationForSubmit = false,
                submitErrorMessage = null,
                submitSuccessProblemId = null,
                validatedSnapshotHash = null,
            )
        }

        scope.launch {
            runCatching { validateCreateProblemUseCase(request) }
                .onSuccess { response ->
                    val responseByIndex = response.results.associateBy { it.index }
                    _state.update { current ->
                        val mappedResults = current.tests.mapIndexedNotNull { index, test ->
                            val result = responseByIndex[index + 1] ?: return@mapIndexedNotNull null
                            test.id to result.toUiResult()
                        }.toMap()
                        val staleResult = current.validationSnapshotHash != requestSnapshotHash
                        val receivedAllResults = mappedResults.size == current.tests.size
                        current.copy(
                            isRunningAllTests = false,
                            runErrorMessage = when {
                                staleResult -> current.runErrorMessage
                                !receivedAllResults -> "Validation returned incomplete test results."
                                else -> null
                            },
                            testRunResultsById = if (staleResult) current.testRunResultsById else mappedResults,
                            validatedSnapshotHash = if (!staleResult && receivedAllResults && response.allSuccessful) {
                                requestSnapshotHash
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            isRunningAllTests = false,
                            runErrorMessage = extractReadableErrorMessage(error),
                            validatedSnapshotHash = null,
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

    val titleError = if (state.title.trim().isEmpty()) {
        CreateProblemValidationError.Required
    } else {
        null
    }

    val descriptionError = if (state.description.trim().isEmpty()) {
        CreateProblemValidationError.Required
    } else {
        null
    }

    val referenceSolutionError = if (state.referenceSolutionCode.trim().isEmpty()) {
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

    val testErrors = buildMap<Int, CreateProblemTestValidation> {
        state.tests.forEach { test ->
            val inputError = if (test.input.trim().isEmpty()) {
                CreateProblemValidationError.Required
            } else {
                null
            }
            if (inputError != null) {
                put(
                    test.id,
                    CreateProblemTestValidation(input = inputError)
                )
            }
        }
    }
    val publicTestsError = if (state.tests.count { !it.isHidden } < MIN_PUBLIC_CREATE_PROBLEM_TESTS) {
        CreateProblemValidationError.MinPublicTests
    } else {
        null
    }

    return CreateProblemValidation(
        prize = prizeError,
        participants = participantsError,
        entryFee = entryFeeError,
        title = titleError,
        description = descriptionError,
        referenceSolution = referenceSolutionError,
        joinUntilDate = joinError,
        submitUntilDate = submitError,
        publicTests = publicTestsError,
        testsById = testErrors,
    )
}

private fun CreateProblemState.toCreateProblemRequest(): CreateProblemRequestDto {
    require(!validation.hasErrors) {
        "CreateProblemRequestDto can be built only from valid state."
    }
    return CreateProblemRequestDto(
        title = title.trim(),
        description = description.trim(),
        constraints = constraints.trim(),
        examples = emptyList(),
        referenceSolutionCode = referenceSolutionCode,
        referenceSolutionLanguage = "kotlin",
        prizeAmount = prize.trim().toLong(),
        entryFeeAmount = entryFee.trim().toLong(),
        requiredParticipants = participants.trim().toInt(),
        joinUntilDate = joinUntilDate.trim(),
        submitUntilDate = submitUntilDate.trim(),
        tests = emptyList(),
        testCases = tests.map { test ->
            CreateProblemTestCaseDto(
                inputData = test.input,
                isHidden = test.isHidden,
                timeoutMs = 1000,
                memoryLimitMb = 256,
            )
        },
    )
}

private fun CreateProblemState.toValidateCreateProblemRequest(
    selectedTestIds: Set<Int>?,
): ValidateCreateProblemRequestDto {
    val selectedTests = if (selectedTestIds == null) {
        tests
    } else {
        tests.filter { it.id in selectedTestIds }
    }
    return ValidateCreateProblemRequestDto(
        referenceSolutionCode = referenceSolutionCode,
        referenceSolutionLanguage = "kotlin",
        testCases = selectedTests.map { test ->
            CreateProblemTestCaseDto(
                inputData = test.input,
                isHidden = test.isHidden,
                timeoutMs = 1000,
                memoryLimitMb = 256,
            )
        },
    )
}

private fun CreateProblemValidationTestResultDto.toUiResult(): CreateProblemTestRunResult {
    return CreateProblemTestRunResult(
        status = status,
        output = output,
        executionTimeMs = executionTimeMs,
        message = message,
    )
}

private fun CreateProblemState.clearSubmissionFeedback(): CreateProblemState {
    return copy(
        isSubmitting = false,
        submitFailed = false,
        requiresFreshValidationForSubmit = false,
        submitErrorMessage = null,
        submitSuccessProblemId = null,
    )
}

private fun CreateProblemState.clearSubmissionAndValidationFeedback(): CreateProblemState {
    return clearSubmissionFeedback().copy(
        isRunningAllTests = false,
        runningTestIds = emptySet(),
        runErrorMessage = null,
        testRunResultsById = emptyMap(),
        validatedSnapshotHash = null,
    )
}

private fun buildCreateProblemValidationSnapshotHash(state: CreateProblemState): String {
    return buildString {
        append(state.referenceSolutionCode)
        append('\u0001')
        state.tests.forEach { test ->
            append(test.input)
            append('\u0002')
            append(if (test.isHidden) '1' else '0')
            append('\u0003')
        }
    }
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
