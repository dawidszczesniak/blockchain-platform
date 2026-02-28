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
import blockchain_platform.composeapp.generated.resources.loading_more
import blockchain_platform.composeapp.generated.resources.participants_summary
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
import pl.dawidszczesniak.blockchain_platform.domain.model.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.presentation.problems.ProblemsIntent
import pl.dawidszczesniak.blockchain_platform.presentation.problems.ProblemsSortOption
import pl.dawidszczesniak.blockchain_platform.presentation.problems.ProblemsListViewModel
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface
import kotlin.math.max
import kotlin.math.min

@Composable
fun ProblemsListScreen(
    viewModel: ProblemsListViewModel,
    onCreateProblem: () -> Unit,
) {
    val listState = rememberLazyListState()
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.currentPage, state.sortOption) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
            if (!state.isEmpty) {
                Spacer(Modifier.weight(1f))
                SortRow(
                    sortOption = state.sortOption,
                    onSortChanged = {
                        viewModel.onIntent(ProblemsIntent.ChangeSort(it))
                    }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (state.isLoading) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.loading_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (!state.isEmpty) {
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(12.dp))
            EmptyProblemList(onCreateProblem = onCreateProblem)
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 44.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.pageItems) { p ->
                    ProblemCard(problem = p, onOpen = { /* TODO */ })
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    PaginationRow(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        onPageSelected = { viewModel.onIntent(ProblemsIntent.ChangePage(it)) }
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

@Composable
private fun SortRow(
    sortOption: ProblemsSortOption,
    onSortChanged: (ProblemsSortOption) -> Unit
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
            ProblemsSortOption.entries.forEach { option ->
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
private fun sortOptionLabel(option: ProblemsSortOption): String {
    return when (option) {
        ProblemsSortOption.Newest -> stringResource(Res.string.sort_option_newest)
        ProblemsSortOption.Oldest -> stringResource(Res.string.sort_option_oldest)
        ProblemsSortOption.StartSoonest -> stringResource(Res.string.sort_option_start_soonest)
        ProblemsSortOption.StartLatest -> stringResource(Res.string.sort_option_start_latest)
        ProblemsSortOption.PrizeHighest -> stringResource(Res.string.sort_option_prize_highest)
        ProblemsSortOption.PrizeLowest -> stringResource(Res.string.sort_option_prize_lowest)
        ProblemsSortOption.EntryFeeHighest -> stringResource(Res.string.sort_option_entry_fee_highest)
        ProblemsSortOption.EntryFeeLowest -> stringResource(Res.string.sort_option_entry_fee_lowest)
        ProblemsSortOption.RequiredMost -> stringResource(Res.string.sort_option_required_most)
        ProblemsSortOption.RequiredLeast -> stringResource(Res.string.sort_option_required_least)
        ProblemsSortOption.RegisteredMost -> stringResource(Res.string.sort_option_registered_most)
        ProblemsSortOption.RegisteredLeast -> stringResource(Res.string.sort_option_registered_least)
        ProblemsSortOption.ProgressMost -> stringResource(Res.string.sort_option_progress_most)
        ProblemsSortOption.ProgressLeast -> stringResource(Res.string.sort_option_progress_least)
        ProblemsSortOption.JoinEndsSoonest -> stringResource(Res.string.sort_option_join_ends_soonest)
        ProblemsSortOption.JoinEndsLatest -> stringResource(Res.string.sort_option_join_ends_latest)
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
    problem: ProblemSummary,
    onOpen: () -> Unit
) {
    val required = max(1, problem.requiredParticipants)
    val registered = min(problem.registeredParticipants, required)
    val progress = registered.toFloat() / required.toFloat()

    AppSurface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            problem.title,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            problem.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
