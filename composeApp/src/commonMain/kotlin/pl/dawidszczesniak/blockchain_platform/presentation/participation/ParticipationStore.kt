package pl.dawidszczesniak.blockchain_platform.presentation.participation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetParticipationProblems
import pl.dawidszczesniak.blockchain_platform.presentation.Store
import kotlin.math.max

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

private sealed interface ParticipationAction {
    data object LoadingStarted : ParticipationAction
    data class DataLoaded(val items: List<ParticipationProblem>) : ParticipationAction
    data class LoadingFailed(val message: String?) : ParticipationAction
    data class FilterChanged(val filter: ParticipationFilter) : ParticipationAction
    data class PageChanged(val page: Int) : ParticipationAction
}

class ParticipationStore(
    private val getParticipationProblems: GetParticipationProblems,
    private val pageSize: Int = 20,
) : Store {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ParticipationState())
    val state: StateFlow<ParticipationState> = _state.asStateFlow()
    private val intents = MutableSharedFlow<ParticipationIntent>(extraBufferCapacity = 64)

    init {
        scope.launch {
            intents.collect { handleIntent(it) }
        }
        dispatch(ParticipationIntent.Refresh)
    }

    fun dispatch(intent: ParticipationIntent) {
        intents.tryEmit(intent)
    }

    private fun handleIntent(intent: ParticipationIntent) {
        when (intent) {
            ParticipationIntent.Refresh -> {
                reduce(ParticipationAction.LoadingStarted)
                scope.launch {
                    runCatching { getParticipationProblems() }
                        .onSuccess { problems ->
                            reduce(ParticipationAction.DataLoaded(problems))
                        }
                        .onFailure { error ->
                            reduce(ParticipationAction.LoadingFailed(error.message))
                        }
                }
            }
            is ParticipationIntent.ChangeFilter -> {
                reduce(ParticipationAction.FilterChanged(intent.filter))
            }
            is ParticipationIntent.ChangePage -> {
                reduce(ParticipationAction.PageChanged(intent.page))
            }
        }
    }

    private fun reduce(action: ParticipationAction) {
        _state.update { current ->
            when (action) {
                ParticipationAction.LoadingStarted -> current.copy(isLoading = true, errorMessage = null)
                is ParticipationAction.DataLoaded -> current.copy(
                    items = action.items,
                    isLoading = false,
                    errorMessage = null
                ).recalculate(pageSize)
                is ParticipationAction.LoadingFailed -> current.copy(
                    isLoading = false,
                    errorMessage = action.message
                ).recalculate(pageSize)
                is ParticipationAction.FilterChanged -> current.copy(
                    filter = action.filter,
                    currentPage = 1
                ).recalculate(pageSize)
                is ParticipationAction.PageChanged -> current.copy(
                    currentPage = action.page
                ).recalculate(pageSize)
            }
        }
    }

    override fun close() {
        scope.cancel()
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
