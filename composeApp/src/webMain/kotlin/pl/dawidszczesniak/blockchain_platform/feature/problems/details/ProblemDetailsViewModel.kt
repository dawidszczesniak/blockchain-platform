@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.feature.problems.details

import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.problem_details_submit_status_confirm_backend
import blockchain_platform.composeapp.generated.resources.problem_details_submit_status_confirm_wallet
import blockchain_platform.composeapp.generated.resources.problem_details_submit_status_processing
import blockchain_platform.composeapp.generated.resources.problem_details_submit_status_queue_waiting
import blockchain_platform.composeapp.generated.resources.problem_details_submit_status_queue_waiting_position
import blockchain_platform.composeapp.generated.resources.problem_details_submit_status_retry_judging
import blockchain_platform.composeapp.generated.resources.problem_details_submit_status_retry_onchain
import blockchain_platform.composeapp.generated.resources.problem_details_submit_status_tx_sent_waiting_network
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.StringResource
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletProvider
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletTransactionRequest
import pl.dawidszczesniak.blockchain_platform.feature.platform.usecase.GetPlatformConfigUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.participation.ParticipationSyncStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmCancelCompetitionOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmJoinProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmSettleCompetitionOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmSubmissionOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetSubmissionJudgeJobUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareCancelCompetitionOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareJoinProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareSettleCompetitionOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RetrySubmissionJudgeJobUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmitProblemCodeUseCase

sealed interface ProblemDetailsUiText {
    data class Raw(
        val value: String,
    ) : ProblemDetailsUiText

    data class Resource(
        val resource: StringResource,
        val args: List<Any> = emptyList(),
    ) : ProblemDetailsUiText
}

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
    val submitStatusMessage: ProblemDetailsUiText? = null,
    val submitErrorMessage: String? = null,
    val submitResult: SubmitProblemResponseDto? = null,
    val submitInlineErrorMessage: String? = null,
    val submitRetryAllowed: Boolean = false,
)

data class CompetitionLifecycleActionState(
    val currentWalletAddress: String? = null,
    val operatorWalletAddress: String? = null,
    val isSettling: Boolean = false,
    val isCancelling: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val settlementStatusOverride: String? = null,
    val txHash: String? = null,
    val explorerUrl: String? = null,
)

class ProblemDetailsViewModel(
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
    private val prepareJoinProblemOnChainUseCase: PrepareJoinProblemOnChainUseCase,
    private val confirmJoinProblemOnChainUseCase: ConfirmJoinProblemOnChainUseCase,
    private val prepareSettleCompetitionOnChainUseCase: PrepareSettleCompetitionOnChainUseCase,
    private val confirmSettleCompetitionOnChainUseCase: ConfirmSettleCompetitionOnChainUseCase,
    private val prepareCancelCompetitionOnChainUseCase: PrepareCancelCompetitionOnChainUseCase,
    private val confirmCancelCompetitionOnChainUseCase: ConfirmCancelCompetitionOnChainUseCase,
    private val confirmSubmissionOnChainUseCase: ConfirmSubmissionOnChainUseCase,
    private val runProblemCodeUseCase: RunProblemCodeUseCase,
    private val submitProblemCodeUseCase: SubmitProblemCodeUseCase,
    private val getSubmissionJudgeJobUseCase: GetSubmissionJudgeJobUseCase,
    private val retrySubmissionJudgeJobUseCase: RetrySubmissionJudgeJobUseCase,
    private val participationSyncStore: ParticipationSyncStore,
    private val walletProvider: WalletProvider,
    private val walletSessionStore: WalletSessionStore,
    private val getPlatformConfigUseCase: GetPlatformConfigUseCase,
) {
    private var scope = newScope()
    private val joinedProblemIds = mutableSetOf<Int>()
    private var loadedProblemId: Int? = null
    private var loadedCompetitionProblemId: Int? = null
    private val _state = MutableStateFlow(ProblemDetailsGateState())
    private val _competitionActionState = MutableStateFlow(CompetitionLifecycleActionState())
    val state: StateFlow<ProblemDetailsGateState> = _state.asStateFlow()
    val competitionActionState: StateFlow<CompetitionLifecycleActionState> = _competitionActionState.asStateFlow()

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
                submitInlineErrorMessage = if (isNewProblem) null else current.submitInlineErrorMessage,
                submitRetryAllowed = false,
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

    fun loadCompetitionLifecycle(problemId: Int) {
        val isNewProblem = loadedCompetitionProblemId != problemId
        loadedCompetitionProblemId = problemId
        val currentWalletAddress = walletSessionStore.currentWalletAddress()
        _competitionActionState.update { current ->
            if (isNewProblem) {
                CompetitionLifecycleActionState(
                    currentWalletAddress = currentWalletAddress,
                )
            } else {
                current.copy(currentWalletAddress = currentWalletAddress)
            }
        }
        activeScope().launch {
            runCatching { getPlatformConfigUseCase() }
                .onSuccess { config ->
                    _competitionActionState.update { current ->
                        current.copy(
                            currentWalletAddress = walletSessionStore.currentWalletAddress(),
                            operatorWalletAddress = config.operatorWalletAddress,
                        )
                    }
                }
                .onFailure {
                    _competitionActionState.update { current ->
                        current.copy(
                            currentWalletAddress = walletSessionStore.currentWalletAddress(),
                            operatorWalletAddress = null,
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

    fun settleCompetition(problemId: Int) {
        val snapshot = _competitionActionState.value
        if (snapshot.isSettling || snapshot.isCancelling) {
            return
        }
        _competitionActionState.update { current ->
            current.copy(
                isSettling = true,
                isCancelling = false,
                statusMessage = "Przygotowuję transakcję rozliczenia konkursu.",
                errorMessage = null,
                txHash = null,
                explorerUrl = null,
            )
        }
        activeScope().launch {
            runCatching {
                val walletId = walletSessionStore.currentWalletId()
                    ?: error("Reconnect wallet before settling this competition.")
                val walletAddress = walletSessionStore.currentWalletAddress()
                    ?: error("Reconnect wallet before settling this competition.")
                val prepared = prepareSettleCompetitionOnChainUseCase(problemId)
                _competitionActionState.update { current ->
                    current.copy(
                        currentWalletAddress = walletAddress,
                        statusMessage = "Potwierdź rozliczenie konkursu w portfelu.",
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
                _competitionActionState.update { current ->
                    current.copy(
                        statusMessage = "Transakcja rozliczenia została wysłana. Czekam na potwierdzenie w sieci.",
                    )
                }
                val receipt = walletProvider.waitForTransactionReceipt(walletId, txHash)
                _competitionActionState.update { current ->
                    current.copy(
                        statusMessage = "Potwierdzam rozliczenie konkursu z backendem.",
                        txHash = receipt.transactionHash,
                    )
                }
                confirmSettleCompetitionOnChainUseCase(problemId, receipt.transactionHash)
            }
                .onSuccess { confirmed ->
                    participationSyncStore.notifyChanged()
                    _competitionActionState.update { current ->
                        current.copy(
                            isSettling = false,
                            isCancelling = false,
                            statusMessage = "Konkurs został rozliczony on-chain.",
                            errorMessage = null,
                            settlementStatusOverride = confirmed.settlementStatus,
                            txHash = confirmed.txHash,
                            explorerUrl = confirmed.explorerUrl,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    _competitionActionState.update { current ->
                        current.copy(
                            isSettling = false,
                            isCancelling = false,
                            statusMessage = null,
                            errorMessage = extractReadableErrorMessage(error),
                        )
                    }
                }
        }
    }

    fun cancelCompetition(problemId: Int) {
        val snapshot = _competitionActionState.value
        if (snapshot.isSettling || snapshot.isCancelling) {
            return
        }
        _competitionActionState.update { current ->
            current.copy(
                isSettling = false,
                isCancelling = true,
                statusMessage = "Przygotowuję transakcję anulowania konkursu.",
                errorMessage = null,
                txHash = null,
                explorerUrl = null,
            )
        }
        activeScope().launch {
            runCatching {
                val walletId = walletSessionStore.currentWalletId()
                    ?: error("Reconnect wallet before cancelling this competition.")
                val walletAddress = walletSessionStore.currentWalletAddress()
                    ?: error("Reconnect wallet before cancelling this competition.")
                val prepared = prepareCancelCompetitionOnChainUseCase(problemId)
                _competitionActionState.update { current ->
                    current.copy(
                        currentWalletAddress = walletAddress,
                        statusMessage = "Potwierdź anulowanie konkursu w portfelu.",
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
                _competitionActionState.update { current ->
                    current.copy(
                        statusMessage = "Transakcja anulowania została wysłana. Czekam na potwierdzenie w sieci.",
                    )
                }
                val receipt = walletProvider.waitForTransactionReceipt(walletId, txHash)
                _competitionActionState.update { current ->
                    current.copy(
                        statusMessage = "Potwierdzam anulowanie konkursu z backendem.",
                        txHash = receipt.transactionHash,
                    )
                }
                confirmCancelCompetitionOnChainUseCase(problemId, receipt.transactionHash)
            }
                .onSuccess { confirmed ->
                    participationSyncStore.notifyChanged()
                    _competitionActionState.update { current ->
                        current.copy(
                            isSettling = false,
                            isCancelling = false,
                            statusMessage = "Konkurs został anulowany on-chain.",
                            errorMessage = null,
                            settlementStatusOverride = confirmed.settlementStatus,
                            txHash = confirmed.txHash,
                            explorerUrl = confirmed.explorerUrl,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    _competitionActionState.update { current ->
                        current.copy(
                            isSettling = false,
                            isCancelling = false,
                            statusMessage = null,
                            errorMessage = extractReadableErrorMessage(error),
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
                    submitInlineErrorMessage = null,
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
                submitInlineErrorMessage = null,
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
                    submitInlineErrorMessage = null,
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
                submitInlineErrorMessage = null,
                submitRetryAllowed = false,
            )
        }
        activeScope().launch {
            runCatching { submitProblemCodeUseCase(problemId, sourceCode, language) }
                .onSuccess { job ->
                    applySubmissionJobState(job)
                    runCatching { pollSubmissionJudgeJob(job.jobId) }
                        .onFailure { error ->
                            if (error is CancellationException) {
                                return@onFailure
                            }
                            applySubmitError(extractReadableErrorMessage(error))
                        }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    applySubmitError(extractReadableErrorMessage(error))
                }
        }
    }

    fun retrySubmit() {
        val snapshot = _state.value
        val jobId = snapshot.activeSubmissionJobId ?: return
        if (snapshot.isSubmitting || !snapshot.submitRetryAllowed) {
            return
        }
        val pendingResult = snapshot.submitResult
        _state.update { current ->
            current.copy(
                isSubmitting = true,
                isSubmitRequestInFlight = false,
                submitStatusMessage = if (pendingResult != null && !pendingResult.onchainRecorded) {
                    uiText(Res.string.problem_details_submit_status_retry_onchain)
                } else {
                    uiText(Res.string.problem_details_submit_status_retry_judging)
                },
                submitErrorMessage = null,
                submitRetryAllowed = false,
            )
        }
        activeScope().launch {
            val retryAction = when {
                pendingResult != null && !pendingResult.onchainRecorded -> runCatching {
                    val latestJob = getSubmissionJudgeJobUseCase(jobId)
                    val latestResult = latestJob.submissionResult ?: pendingResult
                    submitAcceptedResultOnChain(jobId, latestResult)
                }.onSuccess { confirmed ->
                    applyConfirmedSubmission(jobId, confirmed)
                }

                else -> runCatching { retrySubmissionJudgeJobUseCase(jobId) }
                    .onSuccess { job ->
                        applySubmissionJobState(job)
                        pollSubmissionJudgeJob(job.jobId)
                    }
            }
            retryAction
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    applySubmitError(
                        message = extractReadableErrorMessage(error),
                        pendingResult = _state.value.submitResult ?: pendingResult,
                    )
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

    private fun applySubmissionJobState(job: SubmissionJudgeJobDto) {
        _state.update { current ->
            val statusMessage = humanizeSubmissionJobStatus(job.status, job.queuePosition, job.message)
            current.copy(
                isSubmitting = true,
                isSubmitRequestInFlight = false,
                activeSubmissionJobId = job.jobId,
                submitStatusMessage = statusMessage,
                submitErrorMessage = null,
                submitResult = current.submitResult,
                submitInlineErrorMessage = null,
                submitRetryAllowed = false,
            )
        }
    }

    private fun applySubmitError(message: String, pendingResult: SubmitProblemResponseDto? = null) {
        _state.update { current ->
            current.copy(
                isSubmitting = false,
                isSubmitRequestInFlight = false,
                submitStatusMessage = null,
                submitErrorMessage = message,
                submitResult = pendingResult ?: current.submitResult,
                submitInlineErrorMessage = if (pendingResult?.onchainRecorded == false) {
                    message
                } else {
                    null
                },
                submitRetryAllowed = pendingResult?.onchainRecorded == false &&
                    pendingResult.onchainSimulationError.isNullOrBlank(),
            )
        }
    }

    private fun applyConfirmedSubmission(jobId: Long, result: SubmitProblemResponseDto) {
        participationSyncStore.notifyChanged()
        _state.update { current ->
            current.copy(
                isSubmitting = false,
                isSubmitRequestInFlight = false,
                activeSubmissionJobId = jobId,
                submitStatusMessage = null,
                submitErrorMessage = null,
                submitResult = result,
                submitInlineErrorMessage = null,
                submitRetryAllowed = false,
            )
        }
    }

    private suspend fun submitAcceptedResultOnChain(
        jobId: Long,
        result: SubmitProblemResponseDto,
    ): SubmitProblemResponseDto {
        result.onchainSimulationError?.takeIf { it.isNotBlank() }?.let { error(it) }
        val walletId = walletSessionStore.currentWalletId()
            ?: error("Reconnect wallet before confirming this submission.")
        val walletAddress = walletSessionStore.currentWalletAddress()
            ?: error("Reconnect wallet before confirming this submission.")
        val pendingResult = result.copy(onchainRecorded = false)
        val txHash = if (pendingResult.txHash.isNotBlank()) {
            pendingResult.txHash
        } else {
            val walletTransaction = pendingResult.walletTransaction
                ?: error("Submission transaction payload is missing.")
            _state.update { current ->
                current.copy(
                    isSubmitting = true,
                    activeSubmissionJobId = jobId,
                    submitStatusMessage = uiText(Res.string.problem_details_submit_status_confirm_wallet),
                    submitErrorMessage = null,
                    submitResult = pendingResult,
                    submitRetryAllowed = false,
                )
            }
            walletProvider.sendTransaction(
                walletId = walletId,
                walletAddress = walletAddress,
                request = WalletTransactionRequest(
                    to = walletTransaction.to,
                    data = walletTransaction.data,
                    valueHex = walletTransaction.valueHex,
                ),
            )
        }

        val sentResult = pendingResult.copy(txHash = txHash)
        _state.update { current ->
            current.copy(
                isSubmitting = true,
                activeSubmissionJobId = jobId,
                submitStatusMessage = uiText(Res.string.problem_details_submit_status_tx_sent_waiting_network),
                submitErrorMessage = null,
                submitResult = sentResult,
                submitRetryAllowed = false,
            )
        }
        val receipt = walletProvider.waitForTransactionReceipt(walletId, txHash)
        _state.update { current ->
            current.copy(
                isSubmitting = true,
                activeSubmissionJobId = jobId,
                submitStatusMessage = uiText(Res.string.problem_details_submit_status_confirm_backend),
                submitErrorMessage = null,
                submitResult = sentResult.copy(txHash = receipt.transactionHash),
                submitRetryAllowed = false,
            )
        }
        return confirmSubmissionOnChainUseCase(
            submissionId = result.submissionId,
            txHash = receipt.transactionHash,
        )
    }

    private suspend fun pollSubmissionJudgeJob(jobId: Long) {
        while (activeScope().isActive) {
            val job = runCatching { getSubmissionJudgeJobUseCase(jobId) }
                .getOrElse { error ->
                    applySubmitError(extractReadableErrorMessage(error))
                    return
                }
            when (SubmissionJobUiStatus.from(job.status)) {
                SubmissionJobUiStatus.Queued -> {
                    applySubmissionJobState(job)
                    delay(SUBMISSION_JOB_POLL_INTERVAL_MS)
                }

                SubmissionJobUiStatus.Running -> {
                    applySubmissionJobState(job)
                    delay(SUBMISSION_JOB_POLL_INTERVAL_MS)
                }

                SubmissionJobUiStatus.Accepted -> {
                    val result = job.submissionResult
                        ?: throw IllegalStateException("Judge finished without submission result.")
                    if (result.onchainRecorded) {
                        applyConfirmedSubmission(job.jobId, result)
                        return
                    }
                    runCatching { submitAcceptedResultOnChain(job.jobId, result) }
                        .onSuccess { confirmed ->
                            applyConfirmedSubmission(job.jobId, confirmed)
                        }
                        .onFailure { error ->
                            if (error is CancellationException) {
                                return
                            }
                            applySubmitError(
                                message = extractReadableErrorMessage(error),
                                pendingResult = _state.value.submitResult ?: result,
                            )
                        }
                    return
                }

                SubmissionJobUiStatus.Rejected -> {
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
                            submitInlineErrorMessage = null,
                            submitRetryAllowed = false,
                        )
                    }
                    return
                }

                SubmissionJobUiStatus.Error -> {
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            isSubmitRequestInFlight = false,
                            submitStatusMessage = null,
                            submitErrorMessage = job.message ?: "Judge zakończył się błędem.",
                            submitResult = null,
                            submitInlineErrorMessage = null,
                            activeSubmissionJobId = job.jobId,
                            submitRetryAllowed = job.retryAllowed,
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
    extractEmbeddedJsonObject(raw)?.let { jsonObject ->
        decodeWalletRevertData(jsonObject)?.let { return humanizeSubmitValidationMessage(it) }
        extractPreferredJsonMessage(jsonObject)?.let { return humanizeSubmitValidationMessage(it) }
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

private fun humanizeSubmissionJobStatus(
    status: String,
    queuePosition: Int?,
    message: String?,
): ProblemDetailsUiText {
    message?.trim()?.takeIf { it.isNotBlank() }?.let { return ProblemDetailsUiText.Raw(it) }
    return when (status.trim().lowercase()) {
        "queued" -> {
            if (queuePosition != null && queuePosition > 0) {
                uiText(Res.string.problem_details_submit_status_queue_waiting_position, queuePosition)
            } else {
                uiText(Res.string.problem_details_submit_status_queue_waiting)
            }
        }

        "running" -> uiText(Res.string.problem_details_submit_status_processing)
        else -> ProblemDetailsUiText.Raw(status)
    }
}

private fun uiText(resource: StringResource, vararg args: Any): ProblemDetailsUiText {
    return ProblemDetailsUiText.Resource(resource = resource, args = args.toList())
}

private fun extractEmbeddedJsonObject(raw: String): JsonObject? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) {
        return null
    }
    return runCatching {
        Json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
    }.getOrNull()
}

private fun extractPreferredJsonMessage(jsonObject: JsonObject): String? {
    decodeWalletMessages(jsonObject).firstOrNull()?.let { return it }
    return jsonObject["message"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
}

private fun decodeWalletMessages(jsonObject: JsonObject): List<String> {
    val walletError = jsonObject["walletError"]?.jsonObject ?: return emptyList()
    return walletError["messages"]
        ?.jsonArray
        ?.mapNotNull { entry -> entry.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotBlank() } }
        ?.sortedByDescending(::walletErrorMessageScore)
        .orEmpty()
}

private fun decodeWalletRevertData(jsonObject: JsonObject): String? {
    val revertData = jsonObject["walletError"]
        ?.jsonObject
        ?.get("revertData")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.startsWith("0x") && it.length >= 10 }
        ?: return null
    val selector = revertData.take(10)
    val argumentData = "0x${revertData.removePrefix("0x").drop(8)}"
    return when (selector) {
        WALLET_SELECTOR_PARTICIPANT_NOT_JOINED ->
            "ParticipantNotJoined(): wallet is not joined to this competition."

        WALLET_SELECTOR_SUBMISSION_WINDOW_CLOSED ->
            "SubmissionWindowClosed(): the submission deadline has already passed on-chain."

        WALLET_SELECTOR_INVALID_SIGNATURE ->
            "InvalidSignature(): operator signature does not match the submission payload."

        WALLET_SELECTOR_COMPETITION_NOT_OPEN ->
            "CompetitionNotOpen(): the competition is no longer open for recording submissions."

        WALLET_SELECTOR_INVALID_COMPETITION ->
            "InvalidCompetition(): competition id was rejected by the contract."

        WALLET_SELECTOR_INVALID_SUBMISSION ->
            "InvalidSubmission(): submission payload was rejected by the contract."

        WALLET_SELECTOR_INVALID_HASH ->
            "InvalidHash(): one of the prepared bytes32 hashes was rejected by the contract."

        WALLET_SELECTOR_SUBMISSION_ALREADY_RECORDED -> {
            decodeAbiUint256(argumentData)?.let {
                "SubmissionAlreadyRecorded($it): this on-chain submission id is already used."
            } ?: "SubmissionAlreadyRecorded(): this on-chain submission id is already used."
        }

        else -> null
    }
}

private fun walletErrorMessageScore(message: String): Int {
    val normalized = message.lowercase()
    return when {
        "submissionalreadyrecorded" in normalized -> 300
        "participantnotjoined" in normalized -> 250
        "invalidsignature" in normalized -> 250
        "execution reverted" in normalized -> 200
        "revert" in normalized -> 150
        "internal json-rpc error" in normalized -> 10
        else -> 50 + message.length
    }
}

private fun decodeAbiUint256(dataHex: String): Long? {
    val raw = dataHex.removePrefix("0x")
    if (raw.length < 64) {
        return null
    }
    return runCatching { raw.takeLast(64).toULong(16).toLong() }.getOrNull()
}

private val SUBMIT_BLOCKED_PATTERN =
    Regex("""Submission blocked: (\d+)/(\d+) tests did not pass\. Solution was not submitted\.""")

private const val SUBMISSION_JOB_POLL_INTERVAL_MS = 1_000L
private const val WALLET_SELECTOR_INVALID_COMPETITION = "0x1bb8aef5"
private const val WALLET_SELECTOR_INVALID_SUBMISSION = "0xf956cee4"
private const val WALLET_SELECTOR_INVALID_HASH = "0x0af806e0"
private const val WALLET_SELECTOR_COMPETITION_NOT_OPEN = "0x8d2bc609"
private const val WALLET_SELECTOR_PARTICIPANT_NOT_JOINED = "0x1bfa705d"
private const val WALLET_SELECTOR_SUBMISSION_WINDOW_CLOSED = "0x6533356a"
private const val WALLET_SELECTOR_SUBMISSION_ALREADY_RECORDED = "0x0d837dc5"
private const val WALLET_SELECTOR_INVALID_SIGNATURE = "0x8baa579f"

private enum class SubmissionJobUiStatus {
    Queued,
    Running,
    Accepted,
    Rejected,
    Error;

    companion object {
        fun from(raw: String): SubmissionJobUiStatus {
            return when (raw.trim().lowercase()) {
                "queued" -> Queued
                "running" -> Running
                "accepted" -> Accepted
                "rejected" -> Rejected
                else -> Error
            }
        }
    }
}
