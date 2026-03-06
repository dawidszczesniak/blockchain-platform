package pl.dawidszczesniak.blockchain_platform.feature.problems

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
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetProblemListUseCase

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

class ProblemsListViewModel(
    private val getProblemListUseCase: GetProblemListUseCase,
    private val pageSize: Int = 20,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ProblemsListState())
    val state: StateFlow<ProblemsListState> = _state.asStateFlow()

    init {
        onIntent(ProblemsIntent.Refresh)
    }

    fun onIntent(intent: ProblemsIntent) {
        when (intent) {
            ProblemsIntent.Refresh -> {
                refresh()
            }

            is ProblemsIntent.ChangeSort -> {
                _state.update { current ->
                    current.copy(
                        sortOption = intent.option,
                        currentPage = 1
                    ).recalculate(pageSize)
                }
            }

            is ProblemsIntent.ChangePage -> {
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
            runCatching { getProblemListUseCase() }
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
        ProblemsSortOption.Newest -> problems.sortedByDescending { it.id }
        ProblemsSortOption.Oldest -> problems.sortedBy { it.id }
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
