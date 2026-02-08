package pl.dawidszczesniak.blockchain_platform.presentation.created

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
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetCreatedProblems
import pl.dawidszczesniak.blockchain_platform.presentation.Store
import kotlin.math.max

enum class CreatedProblemsFilter {
    All,
    Started,
    Waiting,
    Completed,
    Expired,
}

data class CreatedProblemsState(
    val items: List<CreatedProblem> = emptyList(),
    val pageItems: List<CreatedProblem> = emptyList(),
    val filter: CreatedProblemsFilter = CreatedProblemsFilter.All,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface CreatedProblemsIntent {
    data object Refresh : CreatedProblemsIntent
    data class ChangeFilter(val filter: CreatedProblemsFilter) : CreatedProblemsIntent
    data class ChangePage(val page: Int) : CreatedProblemsIntent
}

private sealed interface CreatedProblemsAction {
    data object LoadingStarted : CreatedProblemsAction
    data class DataLoaded(val items: List<CreatedProblem>) : CreatedProblemsAction
    data class LoadingFailed(val message: String?) : CreatedProblemsAction
    data class FilterChanged(val filter: CreatedProblemsFilter) : CreatedProblemsAction
    data class PageChanged(val page: Int) : CreatedProblemsAction
}

class CreatedProblemsStore(
    private val getCreatedProblems: GetCreatedProblems,
    private val pageSize: Int = 20,
) : Store {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(CreatedProblemsState())
    val state: StateFlow<CreatedProblemsState> = _state.asStateFlow()
    private val intents = MutableSharedFlow<CreatedProblemsIntent>(extraBufferCapacity = 64)

    init {
        scope.launch {
            intents.collect { handleIntent(it) }
        }
        dispatch(CreatedProblemsIntent.Refresh)
    }

    fun dispatch(intent: CreatedProblemsIntent) {
        intents.tryEmit(intent)
    }

    private fun handleIntent(intent: CreatedProblemsIntent) {
        when (intent) {
            CreatedProblemsIntent.Refresh -> {
                reduce(CreatedProblemsAction.LoadingStarted)
                scope.launch {
                    runCatching { getCreatedProblems() }
                        .onSuccess { problems ->
                            reduce(CreatedProblemsAction.DataLoaded(problems))
                        }
                        .onFailure { error ->
                            reduce(CreatedProblemsAction.LoadingFailed(error.message))
                        }
                }
            }
            is CreatedProblemsIntent.ChangeFilter -> {
                reduce(CreatedProblemsAction.FilterChanged(intent.filter))
            }
            is CreatedProblemsIntent.ChangePage -> {
                reduce(CreatedProblemsAction.PageChanged(intent.page))
            }
        }
    }

    private fun reduce(action: CreatedProblemsAction) {
        _state.update { current ->
            when (action) {
                CreatedProblemsAction.LoadingStarted -> current.copy(isLoading = true, errorMessage = null)
                is CreatedProblemsAction.DataLoaded -> current.copy(
                    items = action.items,
                    isLoading = false,
                    errorMessage = null
                ).recalculate(pageSize)
                is CreatedProblemsAction.LoadingFailed -> current.copy(
                    isLoading = false,
                    errorMessage = action.message
                ).recalculate(pageSize)
                is CreatedProblemsAction.FilterChanged -> current.copy(
                    filter = action.filter,
                    currentPage = 1
                ).recalculate(pageSize)
                is CreatedProblemsAction.PageChanged -> current.copy(
                    currentPage = action.page
                ).recalculate(pageSize)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}

private fun CreatedProblemsState.recalculate(pageSize: Int): CreatedProblemsState {
    val ordered = items.sortedBy { statusOrder(it.status) }
    val filtered = when (filter) {
        CreatedProblemsFilter.All -> ordered
        CreatedProblemsFilter.Started -> ordered.filter { it.status == CreatedProblemStatus.Started }
        CreatedProblemsFilter.Waiting -> ordered.filter { it.status == CreatedProblemStatus.Waiting }
        CreatedProblemsFilter.Completed -> ordered.filter { it.status == CreatedProblemStatus.Completed }
        CreatedProblemsFilter.Expired -> ordered.filter { it.status == CreatedProblemStatus.Expired }
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

private fun statusOrder(status: CreatedProblemStatus): Int {
    return when (status) {
        CreatedProblemStatus.Started -> 0
        CreatedProblemStatus.Waiting -> 1
        CreatedProblemStatus.Completed -> 2
        CreatedProblemStatus.Expired -> 3
    }
}
