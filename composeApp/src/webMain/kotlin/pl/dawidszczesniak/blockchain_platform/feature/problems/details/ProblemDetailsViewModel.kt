@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.feature.problems.details

import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletProvider
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletTransactionRequest
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.participation.ParticipationSyncStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmJoinProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetSubmissionJudgeJobUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareJoinProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RetrySubmissionJudgeJobUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmitProblemCodeUseCase

data class ProblemDetailsGateState(
    val isMembershipLoading: Boolean = true,
    val isJoined: Boolean = false,
    val isJoining: Boolean = false,
    val joinStatusMessage: String? = null,
    val joinErrorMessage: String? = null,
    val registeredParticipants: Int? = null,
    val isRunning: Boolean = false,
    val runErrorMessage: String? = null,
    val runResult: RunProblemResponseDto? = null,
    val isSubmitting: Boolean = false,
    val isSubmitRequestInFlight: Boolean = false,
    val activeSubmissionJobId: Long? = null,
    val submitStatusMessage: String? = null,
    val submitErrorMessage: String? = null,
    val submitResult: SubmitProblemResponseDto? = null,
    val submitRetryAllowed: Boolean = false,
    val submitReceiptTimeoutMs: Long? = null,
    val submitReceiptWaitStartedAtMs: Long? = null,
)

class ProblemDetailsViewModel(
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
    private val prepareJoinProblemOnChainUseCase: PrepareJoinProblemOnChainUseCase,
    private val confirmJoinProblemOnChainUseCase: ConfirmJoinProblemOnChainUseCase,
    private val runProblemCodeUseCase: RunProblemCodeUseCase,
    private val submitProblemCodeUseCase: SubmitProblemCodeUseCase,
    private val getSubmissionJudgeJobUseCase: GetSubmissionJudgeJobUseCase,
    private val retrySubmissionJudgeJobUseCase: RetrySubmissionJudgeJobUseCase,
    private val participationSyncStore: ParticipationSyncStore,
    private val walletProvider: WalletProvider,
    private val walletSessionStore: WalletSessionStore,
) {
    private var scope = newScope()
    private val joinedProblemIds = mutableSetOf<Int>()
    private var loadedProblemId: Int? = null
    private val _state = MutableStateFlow(ProblemDetailsGateState())
    val state: StateFlow<ProblemDetailsGateState> = _state.asStateFlow()

    fun load(
        problemId: Int,
        initialRegisteredParticipants: Int,
        requiredParticipants: Int,
        isLoggedIn: Boolean,
    ) {
        val isNewProblem = loadedProblemId != problemId
        loadedProblemId = problemId
        if (!isLoggedIn) {
            joinedProblemIds.clear()
        }
        val cachedJoined = isLoggedIn && joinedProblemIds.contains(problemId)
        val initialRegistered = if (isNewProblem) {
            initialRegisteredParticipants
        } else {
            max(_state.value.registeredParticipants ?: 0, initialRegisteredParticipants)
        }
        _state.update { current ->
            current.copy(
                isMembershipLoading = isLoggedIn,
                isJoined = cachedJoined,
                isJoining = false,
                joinStatusMessage = null,
                joinErrorMessage = null,
                registeredParticipants = initialRegistered,
                isRunning = false,
                runErrorMessage = null,
                runResult = if (isNewProblem) null else current.runResult,
                isSubmitting = false,
                isSubmitRequestInFlight = false,
                activeSubmissionJobId = null,
                submitStatusMessage = null,
                submitErrorMessage = null,
                submitResult = if (isNewProblem) null else current.submitResult,
                submitRetryAllowed = false,
                submitReceiptTimeoutMs = null,
                submitReceiptWaitStartedAtMs = null,
            )
        }
        if (!isLoggedIn) {
            _state.update { current ->
                current.copy(
                    isMembershipLoading = false,
                    isJoined = false,
                    isRunning = false,
                    isSubmitting = false,
                    isSubmitRequestInFlight = false,
                    activeSubmissionJobId = null,
                )
            }
            return
        }

        activeScope().launch {
            runCatching { getParticipationProblemsUseCase() }
                .onSuccess { participationProblems ->
                    joinedProblemIds.clear()
                    joinedProblemIds.addAll(participationProblems.map { it.id })
                    _state.update { current ->
                        current.copy(
                            isMembershipLoading = false,
                            isJoined = joinedProblemIds.contains(problemId),
                            joinErrorMessage = null,
                            joinStatusMessage = null,
                        )
                    }
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            isMembershipLoading = false,
                            joinStatusMessage = null,
                        )
                    }
                }
        }
    }

    fun join(problemId: Int) {
        if (_state.value.isJoining) {
            return
        }
        _state.update { current ->
            current.copy(
                isJoining = true,
                joinStatusMessage = "Przygotowuję transakcję dołączenia.",
                joinErrorMessage = null,
            )
        }
        activeScope().launch {
            runCatching {
                val walletId = walletSessionStore.currentWalletId()
                    ?: error("Reconnect wallet before joining this competition.")
                val walletAddress = walletSessionStore.currentWalletAddress()
                    ?: error("Reconnect wallet before joining this competition.")
                val prepared = prepareJoinProblemOnChainUseCase(problemId)
                prepared.approvalTransaction?.let { approval ->
                    _state.update { current ->
                        current.copy(
                            joinStatusMessage = "Potwierdź autoryzację ${prepared.paymentAsset.symbol} w portfelu.",
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
                            joinStatusMessage = "Autoryzacja wysłana. Czekam na potwierdzenie w sieci.",
                        )
                    }
                    walletProvider.waitForTransactionReceipt(walletId, approvalTxHash)
                }
                _state.update { current ->
                    current.copy(
                        joinStatusMessage = "Potwierdź dołączenie w portfelu.",
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
                        joinStatusMessage = "Transakcja wysłana. Czekam na potwierdzenie w sieci.",
                    )
                }
                val receipt = walletProvider.waitForTransactionReceipt(walletId, txHash)
                _state.update { current ->
                    current.copy(
                        joinStatusMessage = "Potwierdzam dołączenie z backendem.",
                    )
                }
                confirmJoinProblemOnChainUseCase(
                    problemId = problemId,
                    intentId = prepared.intentId,
                    txHash = receipt.transactionHash,
                )
            }
                .onSuccess { result ->
                    joinedProblemIds.add(problemId)
                    participationSyncStore.notifyChanged()
                    _state.update { current ->
                        current.copy(
                            isJoining = false,
                            isJoined = true,
                            joinStatusMessage = null,
                            joinErrorMessage = null,
                            registeredParticipants = max(
                                current.registeredParticipants ?: 0,
                                result.registeredParticipants,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            isJoining = false,
                            joinStatusMessage = null,
                            joinErrorMessage = extractReadableErrorMessage(error),
                        )
                    }
                }
        }
    }

    fun run(problemId: Int, sourceCode: String, language: String) {
        if (_state.value.isRunning) {
            return
        }
        val normalizedCode = sourceCode.trim()
        if (normalizedCode.isEmpty()) {
            _state.update { current ->
                current.copy(
                    runErrorMessage = "Source code cannot be empty.",
                    runResult = null,
                )
            }
            return
        }
        _state.update { current ->
            current.copy(
                isRunning = true,
                runErrorMessage = null,
                submitStatusMessage = null,
                submitErrorMessage = null,
                submitResult = null,
            )
        }
        activeScope().launch {
            runCatching { runProblemCodeUseCase(problemId, sourceCode, language) }
                .onSuccess { runResult ->
                    _state.update { current ->
                        current.copy(
                            isRunning = false,
                            runErrorMessage = null,
                            runResult = runResult,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    _state.update { current ->
                        current.copy(
                            isRunning = false,
                            runErrorMessage = extractReadableErrorMessage(error),
                            runResult = null,
                        )
                    }
                }
        }
    }

    fun submit(problemId: Int, sourceCode: String, language: String) {
        if (_state.value.isSubmitting) {
            return
        }
        val normalizedCode = sourceCode.trim()
        if (normalizedCode.isEmpty()) {
            _state.update { current ->
                current.copy(
                    submitErrorMessage = "Source code cannot be empty.",
                    submitResult = null,
                )
            }
            return
        }
        _state.update { current ->
            current.copy(
                isSubmitting = true,
                isSubmitRequestInFlight = true,
                activeSubmissionJobId = null,
                submitStatusMessage = null,
                submitErrorMessage = null,
                runErrorMessage = null,
                runResult = null,
                submitResult = null,
                submitRetryAllowed = false,
                submitReceiptTimeoutMs = null,
                submitReceiptWaitStartedAtMs = null,
            )
        }
        activeScope().launch {
            runCatching { submitProblemCodeUseCase(problemId, sourceCode, language) }
                .onSuccess { job ->
                    _state.update { current ->
                        val statusMessage = humanizeSubmissionJobStatus(job.status, job.queuePosition, job.message)
        current.copy(
                isSubmitting = true,
                isSubmitRequestInFlight = false,
                            activeSubmissionJobId = job.jobId,
                            submitStatusMessage = statusMessage,
                            submitErrorMessage = null,
                            submitResult = null,
                            submitRetryAllowed = job.retryAllowed,
                            submitReceiptTimeoutMs = job.receiptTimeoutMs,
                            submitReceiptWaitStartedAtMs = nextReceiptWaitStartedAt(
                                currentStartedAtMs = current.submitReceiptWaitStartedAtMs,
                                previousMessage = current.submitStatusMessage,
                                nextMessage = statusMessage,
                            ),
                        )
                    }
                    runCatching { pollSubmissionJudgeJob(job.jobId) }
                        .onFailure { error ->
                            if (error is CancellationException) {
                                return@onFailure
                            }
                            _state.update { current ->
                                current.copy(
                                    isSubmitting = false,
                                    isSubmitRequestInFlight = false,
                                    submitStatusMessage = null,
                                    submitErrorMessage = extractReadableErrorMessage(error),
                                    submitResult = null,
                                    submitRetryAllowed = false,
                                    submitReceiptWaitStartedAtMs = null,
                                )
                            }
                        }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            isSubmitRequestInFlight = false,
                            submitStatusMessage = null,
                            submitErrorMessage = extractReadableErrorMessage(error),
                            submitResult = null,
                            submitRetryAllowed = false,
                            submitReceiptWaitStartedAtMs = null,
                        )
                    }
                }
        }
    }

    fun retrySubmit() {
        val snapshot = _state.value
        val jobId = snapshot.activeSubmissionJobId ?: return
        if (snapshot.isSubmitting || !snapshot.submitRetryAllowed) {
            return
        }
        _state.update { current ->
            current.copy(
                isSubmitting = true,
                isSubmitRequestInFlight = false,
                submitStatusMessage = "Ponawiam sprawdzanie transakcji.",
                submitErrorMessage = null,
                submitResult = null,
                submitReceiptWaitStartedAtMs = null,
            )
        }
        activeScope().launch {
            runCatching { retrySubmissionJudgeJobUseCase(jobId) }
                .onSuccess { job ->
                    _state.update { current ->
                        val statusMessage = humanizeSubmissionJobStatus(job.status, job.queuePosition, job.message)
                        current.copy(
                            isSubmitting = true,
                            isSubmitRequestInFlight = false,
                            activeSubmissionJobId = job.jobId,
                            submitStatusMessage = statusMessage,
                            submitErrorMessage = null,
                            submitRetryAllowed = job.retryAllowed,
                            submitReceiptTimeoutMs = job.receiptTimeoutMs,
                            submitReceiptWaitStartedAtMs = nextReceiptWaitStartedAt(
                                currentStartedAtMs = current.submitReceiptWaitStartedAtMs,
                                previousMessage = current.submitStatusMessage,
                                nextMessage = statusMessage,
                            ),
                        )
                    }
                    pollSubmissionJudgeJob(job.jobId)
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            isSubmitRequestInFlight = false,
                            submitStatusMessage = null,
                            submitErrorMessage = extractReadableErrorMessage(error),
                            submitRetryAllowed = false,
                            submitReceiptWaitStartedAtMs = null,
                        )
                    }
                }
        }
    }

    fun dismissSubmitPopup() {
        _state.update { current ->
            if (current.isSubmitting || current.isSubmitRequestInFlight) {
                current
            } else {
                current.copy(
                    submitStatusMessage = null,
                    submitErrorMessage = null,
                    submitRetryAllowed = false,
                    submitReceiptWaitStartedAtMs = null,
                )
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun activeScope(): CoroutineScope {
        if (!scope.isActive) {
            scope = newScope()
        }
        return scope
    }

    private suspend fun pollSubmissionJudgeJob(jobId: Long) {
        while (activeScope().isActive) {
            val job = runCatching { getSubmissionJudgeJobUseCase(jobId) }
                .getOrElse { error ->
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            isSubmitRequestInFlight = false,
                            submitStatusMessage = null,
                            submitErrorMessage = extractReadableErrorMessage(error),
                            submitResult = null,
                            submitRetryAllowed = false,
                            submitReceiptWaitStartedAtMs = null,
                        )
                    }
                    return
                }
            when (job.status.trim().lowercase()) {
                "queued" -> {
                    _state.update { current ->
                        val statusMessage = humanizeSubmissionJobStatus(job.status, job.queuePosition, job.message)
                        current.copy(
                            isSubmitting = true,
                            isSubmitRequestInFlight = false,
                            activeSubmissionJobId = job.jobId,
                            submitStatusMessage = statusMessage,
                            submitErrorMessage = null,
                            submitRetryAllowed = job.retryAllowed,
                            submitReceiptTimeoutMs = job.receiptTimeoutMs,
                            submitReceiptWaitStartedAtMs = nextReceiptWaitStartedAt(
                                currentStartedAtMs = current.submitReceiptWaitStartedAtMs,
                                previousMessage = current.submitStatusMessage,
                                nextMessage = statusMessage,
                            ),
                        )
                    }
                    delay(SUBMISSION_JOB_POLL_INTERVAL_MS)
                }

                "running" -> {
                    _state.update { current ->
                        val statusMessage = humanizeSubmissionJobStatus(job.status, job.queuePosition, job.message)
                        current.copy(
                            isSubmitting = true,
                            isSubmitRequestInFlight = false,
                            activeSubmissionJobId = job.jobId,
                            submitStatusMessage = statusMessage,
                            submitErrorMessage = null,
                            submitRetryAllowed = job.retryAllowed,
                            submitReceiptTimeoutMs = job.receiptTimeoutMs,
                            submitReceiptWaitStartedAtMs = nextReceiptWaitStartedAt(
                                currentStartedAtMs = current.submitReceiptWaitStartedAtMs,
                                previousMessage = current.submitStatusMessage,
                                nextMessage = statusMessage,
                            ),
                        )
                    }
                    delay(SUBMISSION_JOB_POLL_INTERVAL_MS)
                }

                "accepted" -> {
                    val result = job.submissionResult
                        ?: throw IllegalStateException("Judge finished without submission result.")
                    participationSyncStore.notifyChanged()
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            isSubmitRequestInFlight = false,
                            activeSubmissionJobId = job.jobId,
                            submitStatusMessage = null,
                            submitErrorMessage = null,
                            submitResult = result,
                            submitRetryAllowed = false,
                            submitReceiptWaitStartedAtMs = null,
                        )
                    }
                    return
                }

                "rejected" -> {
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            isSubmitRequestInFlight = false,
                            activeSubmissionJobId = job.jobId,
                            submitStatusMessage = null,
                            submitErrorMessage = humanizeSubmitValidationMessage(
                                job.message ?: "Nie wysłano rozwiązania.",
                            ),
                            runResult = job.runPreview,
                            submitResult = null,
                            submitRetryAllowed = false,
                            submitReceiptWaitStartedAtMs = null,
                        )
                    }
                    return
                }

                else -> {
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            isSubmitRequestInFlight = false,
                            submitStatusMessage = null,
                            submitErrorMessage = job.message ?: "Judge zakończył się błędem.",
                            submitResult = null,
                            activeSubmissionJobId = job.jobId,
                            submitRetryAllowed = job.retryAllowed,
                            submitReceiptTimeoutMs = job.receiptTimeoutMs,
                            submitReceiptWaitStartedAtMs = null,
                        )
                    }
                    return
                }
            }
        }
    }
}

private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun extractReadableErrorMessage(error: Throwable): String {
    val raw = error.message?.trim().orEmpty()
    if (raw.isEmpty()) {
        return "Operation failed."
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
            return humanizeSubmitValidationMessage(parsed)
        }
    }
    return humanizeSubmitValidationMessage(raw)
}

private fun humanizeSubmitValidationMessage(raw: String): String {
    val blockedMatch = SUBMIT_BLOCKED_PATTERN.matchEntire(raw)
    if (blockedMatch != null) {
        val failed = blockedMatch.groupValues[1]
        val total = blockedMatch.groupValues[2]
        return "Nie wysłano rozwiązania. Niezaliczone testy: $failed/$total."
    }
    return raw
}

private fun humanizeSubmissionJobStatus(status: String, queuePosition: Int?, message: String?): String {
    message?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    return when (status.trim().lowercase()) {
        "queued" -> {
            if (queuePosition != null && queuePosition > 0) {
                "Rozwiązanie czeka w kolejce judge. Pozycja: $queuePosition."
            } else {
                "Rozwiązanie czeka w kolejce judge."
            }
        }

        "running" -> "Rozwiązanie jest przetwarzane."
        else -> status
    }
}

private val SUBMIT_BLOCKED_PATTERN =
    Regex("""Submission blocked: (\d+)/(\d+) tests did not pass\. Solution was not submitted\.""")

private const val SUBMISSION_JOB_POLL_INTERVAL_MS = 1_000L

private fun nextReceiptWaitStartedAt(
    currentStartedAtMs: Long?,
    previousMessage: String?,
    nextMessage: String?,
): Long? {
    return if (nextMessage.isReceiptWaitingMessage()) {
        currentStartedAtMs ?: currentEpochMillis()
    } else if (previousMessage.isReceiptWaitingMessage()) {
        null
    } else {
        currentStartedAtMs
    }
}

private fun String?.isReceiptWaitingMessage(): Boolean {
    return this?.contains("Czekam na potwierdzenie w sieci", ignoreCase = true) == true
}

@JsFun("() => Date.now()")
private external fun currentEpochMillisJs(): Double

private fun currentEpochMillis(): Long = currentEpochMillisJs().toLong()
