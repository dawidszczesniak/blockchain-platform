package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.days_count
import blockchain_platform.composeapp.generated.resources.details_coming_soon
import blockchain_platform.composeapp.generated.resources.info_entry_fee
import blockchain_platform.composeapp.generated.resources.info_prize
import blockchain_platform.composeapp.generated.resources.info_start
import blockchain_platform.composeapp.generated.resources.participants_summary
import blockchain_platform.composeapp.generated.resources.problem_title_template
import blockchain_platform.composeapp.generated.resources.problems_mock_info
import blockchain_platform.composeapp.generated.resources.problems_empty_action
import blockchain_platform.composeapp.generated.resources.problems_empty_body
import blockchain_platform.composeapp.generated.resources.problems_empty_title
import blockchain_platform.composeapp.generated.resources.problems_title
import blockchain_platform.composeapp.generated.resources.registration_and_submission
import blockchain_platform.composeapp.generated.resources.sort_label
import blockchain_platform.composeapp.generated.resources.sort_option_entry_fee_highest
import blockchain_platform.composeapp.generated.resources.sort_option_entry_fee_lowest
import blockchain_platform.composeapp.generated.resources.sort_option_join_ends_latest
import blockchain_platform.composeapp.generated.resources.sort_option_join_ends_soonest
import blockchain_platform.composeapp.generated.resources.sort_option_newest
import blockchain_platform.composeapp.generated.resources.sort_option_oldest
import blockchain_platform.composeapp.generated.resources.sort_option_prize_highest
import blockchain_platform.composeapp.generated.resources.sort_option_prize_lowest
import blockchain_platform.composeapp.generated.resources.sort_option_progress_least
import blockchain_platform.composeapp.generated.resources.sort_option_progress_most
import blockchain_platform.composeapp.generated.resources.sort_option_registered_least
import blockchain_platform.composeapp.generated.resources.sort_option_registered_most
import blockchain_platform.composeapp.generated.resources.sort_option_required_least
import blockchain_platform.composeapp.generated.resources.sort_option_required_most
import blockchain_platform.composeapp.generated.resources.sort_option_start_latest
import blockchain_platform.composeapp.generated.resources.sort_option_start_soonest
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface
import kotlin.math.max
import kotlin.math.min

private const val PAGE_SIZE = 20
private const val INITIAL_CREATED_ORDER = 1000
private const val TOTAL_PROBLEMS = 87
private const val SHOW_EMPTY_STATE = false

private data class FakeProblem(
    val id: Int,
    val createdOrder: Int,
    val titleLetter: Char,
    val prizeAmount: Int,
    val entryFeeAmount: Int,
    val requiredParticipants: Int,
    val registeredParticipants: Int,
    val daysToStart: Int,
    val daysToJoinEnd: Int,
    val joinUntilLabel: String,
    val submitUntilLabel: String,
)

private enum class SortOption {
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

@Composable
fun ProblemsListScreen(onCreateProblem: () -> Unit) {
    val listState = rememberLazyListState()

    val allProblems = remember {
        if (SHOW_EMPTY_STATE) {
            emptyList()
        } else {
            generateFakeProblems(
                startId = 1,
                startCreatedOrder = INITIAL_CREATED_ORDER,
                count = TOTAL_PROBLEMS
            )
        }
    }

    var sortOption by remember { mutableStateOf(SortOption.Newest) }
    val sortedProblems = remember(allProblems, sortOption) {
        sortProblems(allProblems, sortOption)
    }
    val isEmpty = sortedProblems.isEmpty()

    var currentPage by remember { mutableStateOf(1) }
    val totalPages = max(1, (sortedProblems.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val pageIndex = currentPage.coerceIn(1, totalPages)
    val startIndex = (pageIndex - 1) * PAGE_SIZE
    val pageProblems = remember(sortedProblems, pageIndex) {
        sortedProblems.drop(startIndex).take(PAGE_SIZE)
    }

    LaunchedEffect(totalPages) {
        if (currentPage > totalPages) currentPage = totalPages
    }

    LaunchedEffect(pageIndex, sortOption) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppSurface(
            modifier = if (isEmpty) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxSize()
            },
            surfaceAlpha = 0.65f,
            borderAlpha = 0.34f
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.problems_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (sortedProblems.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    SortRow(
                        sortOption = sortOption,
                        onSortChanged = {
                            sortOption = it
                            currentPage = 1
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (!isEmpty) {
                Text(
                    text = stringResource(Res.string.problems_mock_info, allProblems.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(12.dp))
                EmptyProblemList(onCreateProblem = onCreateProblem)
                return@AppSurface
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(end = 44.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pageProblems) { p ->
                        ProblemCard(problem = p, onOpen = { /* TODO */ })
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        PaginationRow(
                            currentPage = pageIndex,
                            totalPages = totalPages,
                            onPageSelected = { currentPage = it }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 6.dp)
                ) {
                    val shape = RoundedCornerShape(8.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                shape = shape
                            )
                    )

                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(8.dp)
                    )
                }
            }

        }
    }
}

@Composable
private fun SortRow(
    sortOption: SortOption,
    onSortChanged: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(Res.string.sort_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(10.dp))

        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = SolidColor(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                )
            ) {
                Text(sortOptionLabel(sortOption))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = 0.dp, y = 6.dp)
            ) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortOptionLabel(option)) },
                        onClick = {
                            expanded = false
                            onSortChanged(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun sortOptionLabel(option: SortOption): String {
    return when (option) {
        SortOption.Newest -> stringResource(Res.string.sort_option_newest)
        SortOption.Oldest -> stringResource(Res.string.sort_option_oldest)
        SortOption.StartSoonest -> stringResource(Res.string.sort_option_start_soonest)
        SortOption.StartLatest -> stringResource(Res.string.sort_option_start_latest)
        SortOption.PrizeHighest -> stringResource(Res.string.sort_option_prize_highest)
        SortOption.PrizeLowest -> stringResource(Res.string.sort_option_prize_lowest)
        SortOption.EntryFeeHighest -> stringResource(Res.string.sort_option_entry_fee_highest)
        SortOption.EntryFeeLowest -> stringResource(Res.string.sort_option_entry_fee_lowest)
        SortOption.RequiredMost -> stringResource(Res.string.sort_option_required_most)
        SortOption.RequiredLeast -> stringResource(Res.string.sort_option_required_least)
        SortOption.RegisteredMost -> stringResource(Res.string.sort_option_registered_most)
        SortOption.RegisteredLeast -> stringResource(Res.string.sort_option_registered_least)
        SortOption.ProgressMost -> stringResource(Res.string.sort_option_progress_most)
        SortOption.ProgressLeast -> stringResource(Res.string.sort_option_progress_least)
        SortOption.JoinEndsSoonest -> stringResource(Res.string.sort_option_join_ends_soonest)
        SortOption.JoinEndsLatest -> stringResource(Res.string.sort_option_join_ends_latest)
    }
}

@Composable
private fun EmptyProblemList(onCreateProblem: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.problems_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.problems_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCreateProblem) {
            Text(stringResource(Res.string.problems_empty_action))
        }
    }
}

@Composable
private fun PaginationRow(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit
) {
    if (totalPages <= 1) return

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (page in 1..totalPages) {
                val selected = page == currentPage
                if (selected) {
                    Button(
                        onClick = { onPageSelected(page) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(page.toString())
                    }
                } else {
                    OutlinedButton(
                        onClick = { onPageSelected(page) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(page.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun ProblemCard(
    problem: FakeProblem,
    onOpen: () -> Unit
) {
    val required = max(1, problem.requiredParticipants)
    val registered = min(problem.registeredParticipants, required)
    val progress = registered.toFloat() / required.toFloat()

    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        surfaceAlpha = 0.74f,
        borderAlpha = 0.4f
    ) {
        Text(
            stringResource(
                Res.string.problem_title_template,
                problem.id,
                problem.titleLetter.toString()
            ),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoChip(
                label = stringResource(Res.string.info_prize),
                value = "${problem.prizeAmount} USDC"
            )
            InfoChip(
                label = stringResource(Res.string.info_entry_fee),
                value = "${problem.entryFeeAmount} USDC"
            )
            InfoChip(
                label = stringResource(Res.string.info_start),
                value = stringResource(Res.string.days_count, problem.daysToStart)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            stringResource(
                Res.string.participants_summary,
                registered,
                problem.requiredParticipants
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Text(
            stringResource(
                Res.string.registration_and_submission,
                problem.joinUntilLabel,
                problem.daysToJoinEnd,
                problem.submitUntilLabel
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onOpen, enabled = false) {
                Text(stringResource(Res.string.details_coming_soon))
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    AssistChip(
        onClick = { },
        label = { Text("$label: $value", style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
    )
}

private fun sortProblems(list: List<FakeProblem>, option: SortOption): List<FakeProblem> {
    return when (option) {
        SortOption.Newest -> list.sortedByDescending { it.createdOrder }
        SortOption.Oldest -> list.sortedBy { it.createdOrder }

        SortOption.StartSoonest -> list.sortedBy { it.daysToStart }
        SortOption.StartLatest -> list.sortedByDescending { it.daysToStart }

        SortOption.PrizeHighest -> list.sortedByDescending { it.prizeAmount }
        SortOption.PrizeLowest -> list.sortedBy { it.prizeAmount }

        SortOption.EntryFeeHighest -> list.sortedByDescending { it.entryFeeAmount }
        SortOption.EntryFeeLowest -> list.sortedBy { it.entryFeeAmount }

        SortOption.RequiredMost -> list.sortedByDescending { it.requiredParticipants }
        SortOption.RequiredLeast -> list.sortedBy { it.requiredParticipants }

        SortOption.RegisteredMost -> list.sortedByDescending { it.registeredParticipants }
        SortOption.RegisteredLeast -> list.sortedBy { it.registeredParticipants }

        SortOption.ProgressMost -> list.sortedByDescending { progressRatio(it) }
        SortOption.ProgressLeast -> list.sortedBy { progressRatio(it) }

        SortOption.JoinEndsSoonest -> list.sortedBy { it.daysToJoinEnd }
        SortOption.JoinEndsLatest -> list.sortedByDescending { it.daysToJoinEnd }
    }
}

private fun progressRatio(p: FakeProblem): Float {
    val required = max(1, p.requiredParticipants)
    val registered = min(p.registeredParticipants, required)
    return registered.toFloat() / required.toFloat()
}

private fun generateFakeProblems(
    startId: Int,
    startCreatedOrder: Int,
    count: Int
): List<FakeProblem> {
    return List(count) { i ->
        val id = startId + i
        val createdOrder = startCreatedOrder - i

        val required = 10 + ((id + createdOrder) % 11)
        val registered = min(required, (id * 3 + createdOrder) % (required + 1))

        val prize = 10 + ((id + 2) % 10)
        val entry = 1 + ((id + 1) % 5)

        val daysToJoinEnd = 1 + ((id + createdOrder) % 14)
        val daysToStart = daysToJoinEnd

        val joinDay = (10 + (id % 18)).toString().padStart(2, '0')
        val submitDay = (1 + (id % 20)).toString().padStart(2, '0')

        FakeProblem(
            id = id,
            createdOrder = createdOrder,
            titleLetter = 'A' + (id % 26),
            prizeAmount = prize,
            entryFeeAmount = entry,
            requiredParticipants = required,
            registeredParticipants = registered,
            daysToStart = daysToStart,
            daysToJoinEnd = daysToJoinEnd,
            joinUntilLabel = "2026-02-$joinDay",
            submitUntilLabel = "2026-03-$submitDay"
        )
    }
}
