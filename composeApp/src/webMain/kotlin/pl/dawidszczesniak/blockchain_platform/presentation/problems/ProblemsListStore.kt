package pl.dawidszczesniak.blockchain_platform.presentation.problems

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
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetProblemSummaries
import pl.dawidszczesniak.blockchain_platform.presentation.Store
import kotlin.math.max

enum class ProblemsSortOption {
    Newest,
    Oldest,
    StartSoonest,
    StartLatest,
    PrizeHighest,
    PrizeLowest,
    EntryFeeHighest,
    EntryFeeLowest,
    RequiredMost,
    RequiredLeast,
    RegisteredMost,
    RegisteredLeast,
    ProgressMost,
    ProgressLeast,
    JoinEndsSoonest,
    JoinEndsLatest,
}

data class ProblemsListState(
    val items: List<ProblemSummary> = emptyList(),
    val pageItems: List<ProblemSummary> = emptyList(),
    val sortOption: ProblemsSortOption = ProblemsSortOption.Newest,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface ProblemsIntent {
    data object Refresh : ProblemsIntent
    data class ChangeSort(val option: ProblemsSortOption) : ProblemsIntent
    data class ChangePage(val page: Int) : ProblemsIntent
}

private sealed interface ProblemsAction {
    data object LoadingStarted : ProblemsAction
    data class DataLoaded(val items: List<ProblemSummary>) : ProblemsAction
    data class LoadingFailed(val message: String?) : ProblemsAction
    data class SortChanged(val option: ProblemsSortOption) : ProblemsAction
    data class PageChanged(val page: Int) : ProblemsAction
}

class ProblemsListStore(
    private val getProblemSummaries: GetProblemSummaries,
    private val pageSize: Int = 20,
) : Store {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ProblemsListState())
    val state: StateFlow<ProblemsListState> = _state.asStateFlow()
    private val intents = MutableSharedFlow<ProblemsIntent>(extraBufferCapacity = 64)

    init {
        scope.launch {
            intents.collect { handleIntent(it) }
        }
        dispatch(ProblemsIntent.Refresh)
    }

    fun dispatch(intent: ProblemsIntent) {
        intents.tryEmit(intent)
    }

    private fun handleIntent(intent: ProblemsIntent) {
        when (intent) {
            ProblemsIntent.Refresh -> {
                reduce(ProblemsAction.LoadingStarted)
                scope.launch {
                    runCatching { getProblemSummaries() }
                        .onSuccess { problems ->
                            reduce(ProblemsAction.DataLoaded(problems))
                        }
                        .onFailure { error ->
                            reduce(ProblemsAction.LoadingFailed(error.message))
                        }
                }
            }
            is ProblemsIntent.ChangeSort -> {
                reduce(ProblemsAction.SortChanged(intent.option))
            }
            is ProblemsIntent.ChangePage -> {
                reduce(ProblemsAction.PageChanged(intent.page))
            }
        }
    }

    private fun reduce(action: ProblemsAction) {
        _state.update { current ->
            when (action) {
                ProblemsAction.LoadingStarted -> current.copy(isLoading = true, errorMessage = null)
                is ProblemsAction.DataLoaded -> current.copy(
                    items = action.items,
                    isLoading = false,
                    errorMessage = null
                ).recalculate(pageSize)
                is ProblemsAction.LoadingFailed -> current.copy(
                    isLoading = false,
                    errorMessage = action.message
                ).recalculate(pageSize)
                is ProblemsAction.SortChanged -> current.copy(
                    sortOption = action.option,
                    currentPage = 1
                ).recalculate(pageSize)
                is ProblemsAction.PageChanged -> current.copy(
                    currentPage = action.page
                ).recalculate(pageSize)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}

private fun ProblemsListState.recalculate(pageSize: Int): ProblemsListState {
    val sorted = sortProblems(items, sortOption)
    val totalPages = max(1, (sorted.size + pageSize - 1) / pageSize)
    val pageIndex = currentPage.coerceIn(1, totalPages)
    val startIndex = (pageIndex - 1) * pageSize
    val pageItems = sorted.drop(startIndex).take(pageSize)
    return copy(
        pageItems = pageItems,
        currentPage = pageIndex,
        totalPages = totalPages,
        totalCount = sorted.size,
        isEmpty = sorted.isEmpty(),
    )
}

private fun sortProblems(
    problems: List<ProblemSummary>,
    sortOption: ProblemsSortOption,
): List<ProblemSummary> {
    return when (sortOption) {
        ProblemsSortOption.Newest -> problems.sortedByDescending { it.createdOrder }
        ProblemsSortOption.Oldest -> problems.sortedBy { it.createdOrder }
        ProblemsSortOption.StartSoonest -> problems.sortedBy { it.daysToStart }
        ProblemsSortOption.StartLatest -> problems.sortedByDescending { it.daysToStart }
        ProblemsSortOption.PrizeHighest -> problems.sortedByDescending { it.prizeAmount }
        ProblemsSortOption.PrizeLowest -> problems.sortedBy { it.prizeAmount }
        ProblemsSortOption.EntryFeeHighest -> problems.sortedByDescending { it.entryFeeAmount }
        ProblemsSortOption.EntryFeeLowest -> problems.sortedBy { it.entryFeeAmount }
        ProblemsSortOption.RequiredMost -> problems.sortedByDescending { it.requiredParticipants }
        ProblemsSortOption.RequiredLeast -> problems.sortedBy { it.requiredParticipants }
        ProblemsSortOption.RegisteredMost -> problems.sortedByDescending { it.registeredParticipants }
        ProblemsSortOption.RegisteredLeast -> problems.sortedBy { it.registeredParticipants }
        ProblemsSortOption.ProgressMost -> problems.sortedByDescending {
            progressValue(it.registeredParticipants, it.requiredParticipants)
        }
        ProblemsSortOption.ProgressLeast -> problems.sortedBy {
            progressValue(it.registeredParticipants, it.requiredParticipants)
        }
        ProblemsSortOption.JoinEndsSoonest -> problems.sortedBy { it.daysToJoinEnd }
        ProblemsSortOption.JoinEndsLatest -> problems.sortedByDescending { it.daysToJoinEnd }
    }
}

private fun progressValue(registered: Int, required: Int): Float {
    return if (required == 0) 0f else registered.toFloat() / required.toFloat()
}
