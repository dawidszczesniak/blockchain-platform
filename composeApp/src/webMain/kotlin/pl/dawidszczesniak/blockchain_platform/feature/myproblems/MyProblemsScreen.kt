package pl.dawidszczesniak.blockchain_platform.feature.myproblems

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
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
import blockchain_platform.composeapp.generated.resources.my_problems_title
import blockchain_platform.composeapp.generated.resources.my_problems_waiting
import blockchain_platform.composeapp.generated.resources.my_problem_registration_ends
import blockchain_platform.composeapp.generated.resources.loading_more
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun MyProblemsScreen(onCreateProblem: () -> Unit) {
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<MyProblemsViewModel>() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.my_problems_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!state.isEmpty) {
                Spacer(Modifier.weight(1f))
                TypeFilterRow(
                    current = state.filter,
                    onChange = {
                        viewModel.onIntent(MyProblemsIntent.ChangeFilter(it))
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.isLoading) {
            Text(
                text = stringResource(Res.string.loading_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (state.isEmpty) {
            EmptyMyProblems(onCreateProblem = onCreateProblem)
        } else {
            state.pageItems.forEachIndexed { index, problem ->
                MyProblemCard(problem = problem)
                if (index < state.pageItems.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            PaginationRow(
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                onPageSelected = { viewModel.onIntent(MyProblemsIntent.ChangePage(it)) }
            )
        }
    }
}

@Composable
private fun MyProblemCard(problem: CreatedProblem) {
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
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.wrapContentSize(Alignment.TopEnd)
        ) {
            OutlinedButton(onClick = { expanded = true }) {
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
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (page in 1..totalPages) {
                val selected = page == currentPage
                if (selected) {
                    Button(onClick = { onPageSelected(page) }) {
                        Text(
                            text = page.toString(),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { onPageSelected(page) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
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
    val participants = stringResource(
        Res.string.my_problem_participants,
        problem.registeredParticipants,
        problem.requiredParticipants
    )
    return when (problem.status) {
        CreatedProblemStatus.Started -> listOf(
            participants,
            stringResource(Res.string.my_problem_started_on, problem.startedOn.orEmpty()),
            stringResource(Res.string.my_problem_submitted, problem.submissions)
        )
        CreatedProblemStatus.Waiting -> listOf(
            participants,
            stringResource(Res.string.my_problem_registration_ends, problem.registrationEnds.orEmpty()),
            stringResource(Res.string.my_problem_submitted, 0)
        )
        CreatedProblemStatus.Completed -> listOf(
            participants,
            stringResource(Res.string.my_problem_finished_on, problem.finishedOn.orEmpty()),
            stringResource(Res.string.my_problem_submitted, problem.submissions),
            stringResource(Res.string.my_problem_winner, problem.winner.orEmpty())
        )
        CreatedProblemStatus.Expired -> listOf(
            participants,
            stringResource(Res.string.my_problem_time_elapsed, problem.timeElapsed.orEmpty()),
            stringResource(Res.string.my_problem_submitted, 0)
        )
    }
}
