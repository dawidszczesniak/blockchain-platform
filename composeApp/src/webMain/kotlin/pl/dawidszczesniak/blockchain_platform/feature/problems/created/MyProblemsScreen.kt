package pl.dawidszczesniak.blockchain_platform.feature.problems.created

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.details_action
import blockchain_platform.composeapp.generated.resources.my_problem_finished_on
import blockchain_platform.composeapp.generated.resources.my_problem_participants
import blockchain_platform.composeapp.generated.resources.my_problem_started_on
import blockchain_platform.composeapp.generated.resources.my_problem_submitted
import blockchain_platform.composeapp.generated.resources.my_problem_time_elapsed
import blockchain_platform.composeapp.generated.resources.my_problem_winner
import blockchain_platform.composeapp.generated.resources.my_problems_empty_action
import blockchain_platform.composeapp.generated.resources.my_problems_empty_body
import blockchain_platform.composeapp.generated.resources.my_problems_empty_title
import blockchain_platform.composeapp.generated.resources.my_problems_completed
import blockchain_platform.composeapp.generated.resources.my_problems_expired
import blockchain_platform.composeapp.generated.resources.my_problems_filter_all
import blockchain_platform.composeapp.generated.resources.my_problems_filter_label
import blockchain_platform.composeapp.generated.resources.my_problems_started
import blockchain_platform.composeapp.generated.resources.my_problems_waiting
import blockchain_platform.composeapp.generated.resources.my_problem_registration_ends
import blockchain_platform.composeapp.generated.resources.nav_my_problems
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.ui.AppInlineLoader
import pl.dawidszczesniak.blockchain_platform.ui.AppPanelLoader
import pl.dawidszczesniak.blockchain_platform.ui.AppScrollbarTrack
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface
import pl.dawidszczesniak.blockchain_platform.ui.AppVerticalScrollbar
import kotlin.math.max
import kotlin.math.min

@Composable
fun MyProblemsScreen(
    onCreateProblem: () -> Unit,
    onOpenProblem: (CreatedProblem, (Boolean) -> Unit) -> Unit,
) {
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<MyProblemsViewModel>() }
    val listState = rememberLazyListState()
    var openingProblemId by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.currentPage, state.filter) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.nav_my_problems),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            if (!state.isEmpty) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    TypeFilterRow(
                        current = state.filter,
                        onChange = {
                            viewModel.onIntent(MyProblemsIntent.ChangeFilter(it))
                        }
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            if (!state.isLoading && state.isEmpty) {
                EmptyMyProblems(onCreateProblem = onCreateProblem)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 720.dp)
                ) {
                    if (state.isLoading) {
                        AppPanelLoader(minHeight = 260.dp)
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(end = 44.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.pageItems) { problem ->
                                MyProblemCard(
                                    problem = problem,
                                    isOpening = openingProblemId == problem.id,
                                    onOpenProblem = {
                                        openingProblemId = problem.id
                                        onOpenProblem(problem) { opened ->
                                            if (!opened) {
                                                openingProblemId = null
                                            }
                                        }
                                    },
                                )
                            }
                            item {
                                Spacer(Modifier.height(8.dp))
                                PaginationRow(
                                    currentPage = state.currentPage,
                                    totalPages = state.totalPages,
                                    onPageSelected = { viewModel.onIntent(MyProblemsIntent.ChangePage(it)) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    if (!state.isLoading) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(end = 6.dp)
                        ) {
                            AppScrollbarTrack(
                                modifier = Modifier
                                    .fillMaxHeight()
                            )

                            AppVerticalScrollbar(
                                adapter = rememberScrollbarAdapter(listState),
                                modifier = Modifier
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyProblemCard(
    problem: CreatedProblem,
    isOpening: Boolean,
    onOpenProblem: () -> Unit,
) {
    val required = max(1, problem.requiredParticipants)
    val registered = min(problem.registeredParticipants, required)
    val progress = registered.toFloat() / required.toFloat()
    AppSurface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = problem.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            AssistChip(
                onClick = { },
                label = { Text(statusLabel(problem.status)) },
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
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            detailsFor(problem).forEach { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                Res.string.my_problem_participants,
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
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onOpenProblem,
                enabled = !isOpening,
            ) {
                if (isOpening) {
                    AppInlineLoader()
                } else {
                    Text(stringResource(Res.string.details_action))
                }
            }
        }
    }
}

@Composable
private fun EmptyMyProblems(onCreateProblem: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.my_problems_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.my_problems_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCreateProblem) {
            Text(stringResource(Res.string.my_problems_empty_action))
        }
    }
}

@Composable
private fun TypeFilterRow(
    current: MyProblemsFilter,
    onChange: (MyProblemsFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.my_problems_filter_label)
    val buttonText = when (current) {
        MyProblemsFilter.All -> stringResource(Res.string.my_problems_filter_all)
        MyProblemsFilter.Started -> stringResource(Res.string.my_problems_started)
        MyProblemsFilter.Waiting -> stringResource(Res.string.my_problems_waiting)
        MyProblemsFilter.Completed -> stringResource(Res.string.my_problems_completed)
        MyProblemsFilter.Expired -> stringResource(Res.string.my_problems_expired)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.material3.ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                    brush = SolidColor(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                )
            ) {
                Text(buttonText)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = 0.dp, y = 6.dp)
            ) {
                MyProblemsFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (option) {
                                    MyProblemsFilter.All -> stringResource(Res.string.my_problems_filter_all)
                                    MyProblemsFilter.Started -> stringResource(Res.string.my_problems_started)
                                    MyProblemsFilter.Waiting -> stringResource(Res.string.my_problems_waiting)
                                    MyProblemsFilter.Completed -> stringResource(Res.string.my_problems_completed)
                                    MyProblemsFilter.Expired -> stringResource(Res.string.my_problems_expired)
                                }
                            )
                        },
                        onClick = {
                            expanded = false
                            onChange(option)
                        }
                    )
                }
            }
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
private fun statusLabel(status: CreatedProblemStatus): String {
    return when (status) {
        CreatedProblemStatus.Started -> stringResource(Res.string.my_problems_started)
        CreatedProblemStatus.Waiting -> stringResource(Res.string.my_problems_waiting)
        CreatedProblemStatus.Completed -> stringResource(Res.string.my_problems_completed)
        CreatedProblemStatus.Expired -> stringResource(Res.string.my_problems_expired)
    }
}

@Composable
private fun detailsFor(problem: CreatedProblem): List<String> {
    return when (problem.status) {
        CreatedProblemStatus.Started -> listOf(
            stringResource(Res.string.my_problem_started_on, problem.startedOn.orEmpty()),
            stringResource(Res.string.my_problem_submitted, problem.submissions)
        )
        CreatedProblemStatus.Waiting -> listOf(
            stringResource(Res.string.my_problem_registration_ends, problem.registrationEnds.orEmpty()),
            stringResource(Res.string.my_problem_submitted, 0)
        )
        CreatedProblemStatus.Completed -> listOf(
            stringResource(Res.string.my_problem_finished_on, problem.finishedOn.orEmpty()),
            stringResource(Res.string.my_problem_submitted, problem.submissions),
            stringResource(Res.string.my_problem_winner, problem.winner.orEmpty())
        )
        CreatedProblemStatus.Expired -> listOf(
            stringResource(Res.string.my_problem_time_elapsed, problem.timeElapsed.orEmpty()),
            stringResource(Res.string.my_problem_submitted, 0)
        )
    }
}
