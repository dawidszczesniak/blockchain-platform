package pl.dawidszczesniak.blockchain_platform.presentation.participation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetParticipationProblems

enum class ParticipationFilter {
    All,
    Submitted,
    NotSubmitted,
}

data class ParticipationState(
    val items: List<ParticipationProblem> = emptyList(),
    val pageItems: List<ParticipationProblem> = emptyList(),
    val filter: ParticipationFilter = ParticipationFilter.All,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface ParticipationIntent {
    data object Refresh : ParticipationIntent
    data class ChangeFilter(val filter: ParticipationFilter) : ParticipationIntent
    data class ChangePage(val page: Int) : ParticipationIntent
}

class ParticipationViewModel(
    private val getParticipationProblems: GetParticipationProblems,
    private val pageSize: Int = 20,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ParticipationState())
    val state: StateFlow<ParticipationState> = _state.asStateFlow()

    init {
        onIntent(ParticipationIntent.Refresh)
    }

    fun onIntent(intent: ParticipationIntent) {
        when (intent) {
            ParticipationIntent.Refresh -> {
                refresh()
            }

            is ParticipationIntent.ChangeFilter -> {
                _state.update { current ->
                    current.copy(
                        filter = intent.filter,
                        currentPage = 1
                    ).recalculate(pageSize)
                }
            }

            is ParticipationIntent.ChangePage -> {
                _state.update { current ->
                    current.copy(
                        currentPage = intent.page
                    ).recalculate(pageSize)
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun refresh() {
        _state.update { current -> current.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            runCatching { getParticipationProblems() }
                .onSuccess { problems ->
                    _state.update { current ->
                        current.copy(
                            items = problems,
                            isLoading = false,
                            errorMessage = null
                        ).recalculate(pageSize)
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = error.message
                        ).recalculate(pageSize)
                    }
                }
        }
    }
}

private fun ParticipationState.recalculate(pageSize: Int): ParticipationState {
    val filtered = when (filter) {
        ParticipationFilter.All -> items
        ParticipationFilter.Submitted -> items.filter { it.status == ParticipationStatus.Submitted }
        ParticipationFilter.NotSubmitted -> items.filter { it.status == ParticipationStatus.NotSubmitted }
    }
    val totalPages = max(1, (filtered.size + pageSize - 1) / pageSize)
    val pageIndex = currentPage.coerceIn(1, totalPages)
    val pageItems = filtered.drop((pageIndex - 1) * pageSize).take(pageSize)
    return copy(
        pageItems = pageItems,
        currentPage = pageIndex,
        totalPages = totalPages,
        totalCount = filtered.size,
        isEmpty = filtered.isEmpty(),
    )
}
