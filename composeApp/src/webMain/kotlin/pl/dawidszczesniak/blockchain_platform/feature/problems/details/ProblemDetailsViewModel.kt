package pl.dawidszczesniak.blockchain_platform.feature.problems.details

import kotlin.math.max
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
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCase

data class ProblemDetailsGateState(
    val isMembershipLoading: Boolean = true,
    val isJoined: Boolean = false,
    val isJoining: Boolean = false,
    val joinErrorMessage: String? = null,
    val registeredParticipants: Int? = null,
)

class ProblemDetailsViewModel(
    private val getParticipationProblemsUseCase: GetParticipationProblemsUseCase,
    private val joinProblemUseCase: JoinProblemUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val joinedProblemIds = mutableSetOf<Int>()
    private val _state = MutableStateFlow(ProblemDetailsGateState())
    val state: StateFlow<ProblemDetailsGateState> = _state.asStateFlow()

    fun load(
        problemId: Int,
        initialRegisteredParticipants: Int,
        requiredParticipants: Int,
        isLoggedIn: Boolean,
    ) {
        if (!isLoggedIn) {
            joinedProblemIds.clear()
        }
        val cachedJoined = isLoggedIn && joinedProblemIds.contains(problemId)
        _state.update { current ->
            current.copy(
                isMembershipLoading = isLoggedIn,
                isJoined = cachedJoined,
                isJoining = false,
                joinErrorMessage = null,
                registeredParticipants = max(current.registeredParticipants ?: 0, initialRegisteredParticipants),
            )
        }
        if (!isLoggedIn) {
            _state.update { current ->
                current.copy(
                    isMembershipLoading = false,
                    isJoined = false,
                )
            }
            return
        }

        scope.launch {
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
        scope.launch {
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

    fun close() {
        scope.cancel()
    }
}

private fun extractReadableErrorMessage(error: Throwable): String {
    val raw = error.message?.trim().orEmpty()
    if (raw.isEmpty()) {
        return "Failed to join this problem."
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
