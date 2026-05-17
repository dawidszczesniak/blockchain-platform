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
import kotlinx.datetime.LocalDate
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PaymentAssetDto
import pl.dawidszczesniak.blockchain_platform.feature.platform.parseHumanAmountToAtomic
import pl.dawidszczesniak.blockchain_platform.feature.platform.sanitizeHumanAmountInput
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletProvider
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletTransactionRequest
import pl.dawidszczesniak.blockchain_platform.feature.platform.usecase.GetPlatformConfigUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemValidationTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemTestCaseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ValidateCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmCreateProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareCreateProblemOnChainUseCase

const val MAX_CREATE_PROBLEM_TESTS = 50
const val MIN_CREATE_PROBLEM_TESTS = 1
const val MIN_PUBLIC_CREATE_PROBLEM_TESTS = 1
const val CREATE_PROBLEM_RUN_ERROR_REFERENCE_SOLUTION_REQUIRED = "__create_problem_run_error_reference_solution_required__"
const val CREATE_PROBLEM_SUBMIT_ERROR_TEST_INPUT_REQUIRED = "__create_problem_submit_error_test_input_required__"

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
    val memoryUsedKb: Int? = null,
    val message: String?,
)

enum class CreateProblemValidationError {
    Required,
    InvalidInteger,
    InvalidAmount,
    MustBePositive,
    MustBeNonNegative,
    PaymentAssetRequired,
    SubmitBeforeJoin,
    MinPublicTests,
}

data class CreateProblemValidation(
    val paymentAsset: CreateProblemValidationError? = null,
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
        get() = paymentAsset != null ||
            prize != null ||
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
    val supportedPaymentAssets: List<PaymentAssetDto> = emptyList(),
    val selectedPaymentAssetCode: String? = null,
    val isPaymentAssetsLoading: Boolean = true,
    val prize: String = "",
    val participants: String = "",
    val entryFee: String = "",
    val title: String = "",
    val description: String = "",
    val referenceSolutionCode: String = "",
    val joinUntilDate: LocalDate? = null,
    val submitUntilDate: LocalDate? = null,
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
    val submitStatusMessage: String? = null,
    val submitErrorMessage: String? = null,
    val submitSuccessProblemId: Int? = null,
) {
    val selectedPaymentAsset: PaymentAssetDto?
        get() = supportedPaymentAssets.firstOrNull { it.code == selectedPaymentAssetCode }

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

    val creatorProfit: Double
        get() = grossRevenue - prizeValue - platformFee

    val winnerProfit: Double
        get() = prizeValue - entryFeeValue

    val netRevenue: Double
        get() = creatorProfit

    val canAddTest: Boolean
        get() = tests.size < MAX_CREATE_PROBLEM_TESTS

    val validationSnapshotHash: String
        get() = buildCreateProblemValidationSnapshotHash(this)

    val isValidationFresh: Boolean
        get() = validatedSnapshotHash != null && validatedSnapshotHash == validationSnapshotHash

    val canAttemptSubmit: Boolean
        get() = !isSubmitting &&
            !isRunningAllTests &&
            runningTestIds.isEmpty()

    val canSubmit: Boolean
        get() = !validation.hasErrors &&
            canAttemptSubmit &&
            isValidationFresh

    val validation: CreateProblemValidation
        get() = validateCreateProblem(this)
}

sealed interface CreateProblemIntent {
    data class PaymentAssetChanged(val code: String) : CreateProblemIntent
    data class PrizeChanged(val value: String) : CreateProblemIntent
    data class ParticipantsChanged(val value: String) : CreateProblemIntent
    data class EntryFeeChanged(val value: String) : CreateProblemIntent
    data class TitleChanged(val value: String) : CreateProblemIntent
    data class DescriptionChanged(val value: String) : CreateProblemIntent
    data class ReferenceSolutionChanged(val value: String) : CreateProblemIntent
    data class JoinUntilChanged(val value: LocalDate) : CreateProblemIntent
    data class SubmitUntilChanged(val value: LocalDate) : CreateProblemIntent
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
    private val prepareCreateProblemOnChainUseCase: PrepareCreateProblemOnChainUseCase,
    private val confirmCreateProblemOnChainUseCase: ConfirmCreateProblemOnChainUseCase,
    private val validateCreateProblemUseCase: ValidateCreateProblemUseCase,
    private val getPlatformConfigUseCase: GetPlatformConfigUseCase,
    private val walletProvider: WalletProvider,
    private val walletSessionStore: WalletSessionStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(CreateProblemState())
    val state: StateFlow<CreateProblemState> = _state.asStateFlow()

    init {
        loadPaymentAssets()
    }

    fun onIntent(intent: CreateProblemIntent) {
        when (intent) {
            is CreateProblemIntent.PaymentAssetChanged -> {
                _state.update { current ->
                    current.copy(selectedPaymentAssetCode = intent.code).clearSubmissionAndValidationFeedback()
                }
            }

            is CreateProblemIntent.PrizeChanged -> {
                val normalized = sanitizeHumanAmountInput(intent.value)
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
                val normalized = sanitizeHumanAmountInput(intent.value)
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
                    submitStatusMessage = null,
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
                    submitStatusMessage = null,
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
                submitStatusMessage = "Przygotowuję transakcję konkursu.",
                submitErrorMessage = null,
                submitSuccessProblemId = null,
            )
        }

        scope.launch {
            runCatching {
                val walletId = walletSessionStore.currentWalletId()
                    ?: error("Reconnect wallet before creating an on-chain competition.")
                val walletAddress = walletSessionStore.currentWalletAddress()
                    ?: error("Reconnect wallet before creating an on-chain competition.")
                val prepared = prepareCreateProblemOnChainUseCase(request)
                prepared.approvalTransaction?.let { approval ->
                    _state.update { current ->
                        current.copy(
                            submitStatusMessage = "Potwierdź autoryzację ${prepared.paymentAsset.symbol} w portfelu.",
                        )
                    }
                    val approvalTxHash = walletProvider.sendTransaction(
                        walletId = walletId,
                        walletAddress = walletAddress,
                        request = WalletTransactionRequest(
                            to = approval.to,
                            data = approval.data,
                            valueHex = approval.valueHex,
                        ),
                    )
                    _state.update { current ->
                        current.copy(
                            submitStatusMessage = "Autoryzacja wysłana. Czekam na potwierdzenie w sieci.",
                        )
                    }
                    walletProvider.waitForTransactionReceipt(
                        walletId = walletId,
                        txHash = approvalTxHash,
                    )
                }
                _state.update { current ->
                    current.copy(
                        submitStatusMessage = "Potwierdź utworzenie konkursu w portfelu.",
                    )
                }
                val txHash = walletProvider.sendTransaction(
                    walletId = walletId,
                    walletAddress = walletAddress,
                    request = WalletTransactionRequest(
                        to = prepared.transaction.to,
                        data = prepared.transaction.data,
                        valueHex = prepared.transaction.valueHex,
                    ),
                )
                _state.update { current ->
                    current.copy(
                        submitStatusMessage = "Transakcja wysłana. Czekam na potwierdzenie w sieci.",
                    )
                }
                val receipt = walletProvider.waitForTransactionReceipt(
                    walletId = walletId,
                    txHash = txHash,
                )
                _state.update { current ->
                    current.copy(
                        submitStatusMessage = "Potwierdzam konkurs z backendem.",
                    )
                }
                confirmCreateProblemOnChainUseCase(
                    intentId = prepared.intentId,
                    txHash = receipt.transactionHash,
                )
            }.onSuccess { createdProblemId ->
                _state.value = state.value.copy(
                    prize = "",
                    participants = "",
                    entryFee = "",
                    title = "",
                    description = "",
                    referenceSolutionCode = "",
                    joinUntilDate = null,
                    submitUntilDate = null,
                    tests = listOf(
                        CreateProblemTest(
                            id = 1,
                            input = "",
                            isHidden = false,
                            expanded = false,
                        ),
                    ),
                    nextTestId = 2,
                    runningTestIds = emptySet(),
                    isRunningAllTests = false,
                    runErrorMessage = null,
                    testRunResultsById = emptyMap(),
                    validatedSnapshotHash = null,
                    submitAttempted = false,
                    isSubmitting = false,
                    submitFailed = false,
                    requiresFreshValidationForSubmit = false,
                    submitStatusMessage = null,
                    submitErrorMessage = null,
                    submitSuccessProblemId = createdProblemId,
                )
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        isSubmitting = false,
                        submitFailed = true,
                        requiresFreshValidationForSubmit = false,
                        submitStatusMessage = null,
                        submitErrorMessage = extractReadableErrorMessage(error),
                        submitSuccessProblemId = null,
                    )
                }
            }
        }
    }

    private fun loadPaymentAssets() {
        scope.launch {
            runCatching { getPlatformConfigUseCase() }
                .onSuccess { platform ->
                    val supportedAssets = platform.supportedPaymentAssets
                    _state.update { current ->
                        current.copy(
                            supportedPaymentAssets = supportedAssets,
                            selectedPaymentAssetCode = current.selectedPaymentAssetCode
                                ?: supportedAssets.firstOrNull()?.code,
                            isPaymentAssetsLoading = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(isPaymentAssetsLoading = false)
                    }
                }
        }
    }

    private fun runSingleTest(testId: Int) {
        val snapshot = state.value
        if (snapshot.isRunningAllTests || snapshot.runningTestIds.contains(testId) || snapshot.isSubmitting) {
            return
        }
        if (snapshot.referenceSolutionCode.trim().isEmpty()) {
            _state.update { current ->
                current.copy(
                    runErrorMessage = CREATE_PROBLEM_RUN_ERROR_REFERENCE_SOLUTION_REQUIRED,
                    submitFailed = false,
                    requiresFreshValidationForSubmit = false,
                    submitErrorMessage = null,
                    submitSuccessProblemId = null,
                )
            }
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
        if (snapshot.referenceSolutionCode.trim().isEmpty()) {
            _state.update { current ->
                current.copy(
                    runErrorMessage = CREATE_PROBLEM_RUN_ERROR_REFERENCE_SOLUTION_REQUIRED,
                    submitFailed = false,
                    requiresFreshValidationForSubmit = false,
                    submitErrorMessage = null,
                    submitSuccessProblemId = null,
                )
            }
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
    val selectedPaymentAsset = state.selectedPaymentAsset
    val paymentAssetError = if (selectedPaymentAsset == null) {
        CreateProblemValidationError.PaymentAssetRequired
    } else {
        null
    }

    val prizeRaw = state.prize.trim()
    var prizeError: CreateProblemValidationError? = null
    if (prizeRaw.isEmpty()) {
        prizeError = CreateProblemValidationError.Required
    } else if (selectedPaymentAsset == null) {
        prizeError = CreateProblemValidationError.PaymentAssetRequired
    } else {
        prizeError = when (parseHumanAmountToAtomic(prizeRaw, selectedPaymentAsset)) {
            null -> CreateProblemValidationError.InvalidAmount
            "0" -> CreateProblemValidationError.MustBePositive
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
    } else if (selectedPaymentAsset == null) {
        entryFeeError = CreateProblemValidationError.PaymentAssetRequired
    } else {
        entryFeeError = if (parseHumanAmountToAtomic(entryFeeRaw, selectedPaymentAsset) == null) {
            CreateProblemValidationError.InvalidAmount
        } else {
            null
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

    val joinDate = state.joinUntilDate
    val joinError = if (joinDate == null) {
        CreateProblemValidationError.Required
    } else {
        null
    }

    val submitDate = state.submitUntilDate
    val submitError = when {
        submitDate == null -> CreateProblemValidationError.Required
        joinDate != null && submitDate <= joinDate -> CreateProblemValidationError.SubmitBeforeJoin
        else -> null
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
        paymentAsset = paymentAssetError,
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
    val paymentAsset = requireNotNull(selectedPaymentAsset) {
        "payment asset must be set in valid state."
    }
    return CreateProblemRequestDto(
        title = title.trim(),
        description = description.trim(),
        constraints = "",
        examples = emptyList(),
        referenceSolutionCode = referenceSolutionCode,
        referenceSolutionLanguage = "kotlin",
        paymentAssetCode = paymentAsset.code,
        prizeAmountAtomic = requireNotNull(parseHumanAmountToAtomic(prize.trim(), paymentAsset)),
        entryFeeAmountAtomic = requireNotNull(parseHumanAmountToAtomic(entryFee.trim(), paymentAsset)),
        requiredParticipants = participants.trim().toInt(),
        joinUntilDate = requireNotNull(joinUntilDate) {
            "joinUntilDate must be set in valid state."
        },
        submitUntilDate = requireNotNull(submitUntilDate) {
            "submitUntilDate must be set in valid state."
        },
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
        memoryUsedKb = memoryUsedKb,
        message = message,
    )
}

private fun CreateProblemState.clearSubmissionFeedback(): CreateProblemState {
    return copy(
        isSubmitting = false,
        submitFailed = false,
        requiresFreshValidationForSubmit = false,
        submitStatusMessage = null,
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
            return humanizeCreateProblemErrorMessage(parsed)
        }
    }
    return humanizeCreateProblemErrorMessage(raw)
}

private fun humanizeCreateProblemErrorMessage(raw: String): String {
    return when {
        raw.contains("testcases[", ignoreCase = true) &&
            raw.contains("input", ignoreCase = true) &&
            raw.contains("required", ignoreCase = true) -> {
            CREATE_PROBLEM_SUBMIT_ERROR_TEST_INPUT_REQUIRED
        }
        else -> raw
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
