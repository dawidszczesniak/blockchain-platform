package pl.dawidszczesniak.blockchain_platform.presentation.created

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
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetCreatedProblemsUseCase

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

class CreatedProblemsViewModel(
    private val getCreatedProblemsUseCase: GetCreatedProblemsUseCase,
    private val pageSize: Int = 20,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(CreatedProblemsState())
    val state: StateFlow<CreatedProblemsState> = _state.asStateFlow()

    init {
        onIntent(CreatedProblemsIntent.Refresh)
    }

    fun onIntent(intent: CreatedProblemsIntent) {
        when (intent) {
            CreatedProblemsIntent.Refresh -> {
                refresh()
            }

            is CreatedProblemsIntent.ChangeFilter -> {
                _state.update { current ->
                    current.copy(
                        filter = intent.filter,
                        currentPage = 1
                    ).recalculate(pageSize)
                }
            }

            is CreatedProblemsIntent.ChangePage -> {
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
            runCatching { getCreatedProblemsUseCase() }
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
