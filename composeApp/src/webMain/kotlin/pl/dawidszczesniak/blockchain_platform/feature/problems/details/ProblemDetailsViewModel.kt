package pl.dawidszczesniak.blockchain_platform.feature.problems.details

import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmitProblemCodeUseCase

data class ProblemDetailsGateState(
    val isMembershipLoading: Boolean = true,
    val isJoined: Boolean = false,
    val isJoining: Boolean = false,
    val joinErrorMessage: String? = null,
    val registeredParticipants: Int? = null,
    val isRunning: Boolean = false,
    val runErrorMessage: String? = null,
    val runResult: RunProblemResponseDto? = null,
    val isSubmitting: Boolean = false,
    val submitErrorMessage: String? = null,
    val submitResult: SubmitProblemResponseDto? = null,
)

class ProblemDetailsViewModel(
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
    private val joinProblemUseCase: JoinProblemUseCase,
    private val runProblemCodeUseCase: RunProblemCodeUseCase,
    private val submitProblemCodeUseCase: SubmitProblemCodeUseCase,
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
                joinErrorMessage = null,
                registeredParticipants = initialRegistered,
                isRunning = false,
                runErrorMessage = null,
                runResult = if (isNewProblem) null else current.runResult,
                isSubmitting = false,
                submitErrorMessage = null,
                submitResult = if (isNewProblem) null else current.submitResult,
            )
        }
        if (!isLoggedIn) {
            _state.update { current ->
                current.copy(
                    isMembershipLoading = false,
                    isJoined = false,
                    isRunning = false,
                    isSubmitting = false,
                )
            }
            return
        }

        activeScope().launch {
            runCatching { getParticipationProblemsUseCase() }
                .onSuccess { participationProblems ->
                    joinedProblemIds.clear()
                    joinedProblemIds.addAll(participationProblems.map { it.id })
                    val joined = joinedProblemIds.contains(problemId)
                    val shouldProbeJoinForStartedCompetition =
                        !joined && initialRegisteredParticipants >= requiredParticipants
                    val probedJoinResult = if (shouldProbeJoinForStartedCompetition) {
                        runCatching { joinProblemUseCase(problemId) }.getOrNull()
                    } else {
                        null
                    }
                    if (probedJoinResult != null) {
                        joinedProblemIds.add(problemId)
                    }
                    _state.update { current ->
                        current.copy(
                            isMembershipLoading = false,
                            isJoined = joined || probedJoinResult != null,
                            joinErrorMessage = null,
                            registeredParticipants = max(
                                current.registeredParticipants ?: 0,
                                probedJoinResult?.registeredParticipants ?: 0,
                            ),
                        )
                    }
                }
                .onFailure {
                    val shouldProbeJoinForStartedCompetition =
                        initialRegisteredParticipants >= requiredParticipants
                    val probedJoinResult = if (shouldProbeJoinForStartedCompetition) {
                        runCatching { joinProblemUseCase(problemId) }.getOrNull()
                    } else {
                        null
                    }
                    if (probedJoinResult != null) {
                        joinedProblemIds.add(problemId)
                    }
                    _state.update { current ->
                        current.copy(
                            isMembershipLoading = false,
                            isJoined = current.isJoined || probedJoinResult != null,
                            registeredParticipants = max(
                                current.registeredParticipants ?: 0,
                                probedJoinResult?.registeredParticipants ?: 0,
                            ),
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
                joinErrorMessage = null,
            )
        }
        activeScope().launch {
            runCatching { joinProblemUseCase(problemId) }
                .onSuccess { result ->
                    joinedProblemIds.add(problemId)
                    _state.update { current ->
                        current.copy(
                            isJoining = false,
                            isJoined = true,
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
                            joinErrorMessage = extractReadableErrorMessage(error),
                        )
                    }
                }
        }
    }

    fun run(problemId: Int, sourceCode: String) {
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
                submitErrorMessage = null,
                submitResult = null,
            )
        }
        activeScope().launch {
            runCatching { runProblemCodeUseCase(problemId, sourceCode) }
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

    fun submit(problemId: Int, sourceCode: String) {
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
                submitErrorMessage = null,
                runErrorMessage = null,
                runResult = null,
            )
        }
        activeScope().launch {
            runCatching { submitProblemCodeUseCase(problemId, sourceCode) }
                .onSuccess { submitResult ->
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            submitErrorMessage = null,
                            submitResult = submitResult,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    _state.update { current ->
                        current.copy(
                            isSubmitting = false,
                            submitErrorMessage = extractReadableErrorMessage(error),
                            submitResult = null,
                        )
                    }
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
            return parsed
        }
    }
    return raw
}
