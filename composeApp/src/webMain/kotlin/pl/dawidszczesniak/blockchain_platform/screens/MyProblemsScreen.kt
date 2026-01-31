package pl.dawidszczesniak.blockchain_platform.screens

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
import androidx.compose.runtime.LaunchedEffect
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
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface
import kotlin.math.max

private const val PAGE_SIZE = 20
private const val TOTAL_PROBLEMS = 80
private const val SHOW_EMPTY_STATE = false

private enum class ProblemStatus {
    Started,
    Waiting,
    Completed,
    Expired,
}

private enum class TypeFilter {
    All,
    Started,
    Waiting,
    Completed,
    Expired,
}

private data class MyProblem(
    val title: String,
    val status: ProblemStatus,
    val details: List<String>,
)

@Composable
fun MyProblemsScreen(onCreateProblem: () -> Unit) {
    val problems = if (SHOW_EMPTY_STATE) {
        emptyList()
    } else {
        List(TOTAL_PROBLEMS) { index ->
            val status = ProblemStatus.entries[index % ProblemStatus.entries.size]
            val required = 20 + (index % 5)
            val participants = when (status) {
                ProblemStatus.Started,
                ProblemStatus.Completed -> required
                ProblemStatus.Waiting -> (required - 1 - (index % 4)).coerceAtLeast(1)
                ProblemStatus.Expired -> 0
            }
            val submissions = when (status) {
                ProblemStatus.Started -> (index * 3 % required).coerceAtLeast(1)
                ProblemStatus.Completed -> (required - (index % 4)).coerceAtLeast(1)
                ProblemStatus.Waiting -> 0
                ProblemStatus.Expired -> 0
            }
            val day = (index % 28) + 1
            val dayLabel = day.toString().padStart(2, '0')
            val title = when (status) {
                ProblemStatus.Started -> "Started #${index + 1} — Neural net compression"
                ProblemStatus.Waiting -> "Waiting #${index + 1} — Proof batching"
                ProblemStatus.Completed -> "Completed #${index + 1} — Oracle aggregation"
                ProblemStatus.Expired -> "Expired #${index + 1} — ZK proof relay"
            }
            val details = when (status) {
                ProblemStatus.Started -> listOf(
                    stringResource(Res.string.my_problem_participants, participants, required),
                    stringResource(Res.string.my_problem_started_on, "2026-01-$dayLabel"),
                    stringResource(Res.string.my_problem_submitted, submissions)
                )
                ProblemStatus.Waiting -> listOf(
                    stringResource(Res.string.my_problem_participants, participants, required),
                    stringResource(Res.string.my_problem_registration_ends, "2026-02-$dayLabel"),
                    stringResource(Res.string.my_problem_submitted, 0)
                )
                ProblemStatus.Completed -> listOf(
                    stringResource(Res.string.my_problem_participants, participants, required),
                    stringResource(Res.string.my_problem_finished_on, "2026-01-$dayLabel"),
                    stringResource(Res.string.my_problem_submitted, submissions),
                    stringResource(
                        Res.string.my_problem_winner,
                        "0x${(index + 10).toString(16)}…${(index * 7 + 3).toString(16)}"
                    )
                )
                ProblemStatus.Expired -> listOf(
                    stringResource(Res.string.my_problem_participants, participants, required),
                    stringResource(Res.string.my_problem_time_elapsed, "2026-01-$dayLabel"),
                    stringResource(Res.string.my_problem_submitted, 0)
                )
            }
            MyProblem(
                title = title,
                status = status,
                details = details
            )
        }
    }

    var filter by remember { mutableStateOf(TypeFilter.All) }
    var currentPage by remember { mutableStateOf(1) }

    val sorted = problems.sortedBy { statusOrder(it.status) }
    val filtered = when (filter) {
        TypeFilter.All -> sorted
        TypeFilter.Started -> sorted.filter { it.status == ProblemStatus.Started }
        TypeFilter.Waiting -> sorted.filter { it.status == ProblemStatus.Waiting }
        TypeFilter.Completed -> sorted.filter { it.status == ProblemStatus.Completed }
        TypeFilter.Expired -> sorted.filter { it.status == ProblemStatus.Expired }
    }
    val totalPages = max(1, (filtered.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val pageIndex = currentPage.coerceIn(1, totalPages)
    val pageProblems = filtered.drop((pageIndex - 1) * PAGE_SIZE).take(PAGE_SIZE)

    LaunchedEffect(filter, totalPages) {
        if (currentPage > totalPages) currentPage = totalPages
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            surfaceAlpha = 0.65f,
            borderAlpha = 0.34f
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
                if (filtered.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    TypeFilterRow(
                        current = filter,
                        onChange = {
                            filter = it
                            currentPage = 1
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                EmptyMyProblems(onCreateProblem = onCreateProblem)
            } else {
                pageProblems.forEachIndexed { index, problem ->
                    MyProblemCard(problem = problem)
                    if (index < pageProblems.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                PaginationRow(
                    currentPage = pageIndex,
                    totalPages = totalPages,
                    onPageSelected = { currentPage = it }
                )
            }
        }
    }
}

@Composable
private fun MyProblemCard(problem: MyProblem) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        surfaceAlpha = 0.74f,
        borderAlpha = 0.4f
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
            problem.details.forEach { detail ->
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
    current: TypeFilter,
    onChange: (TypeFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.my_problems_filter_label)
    val buttonText = when (current) {
        TypeFilter.All -> stringResource(Res.string.my_problems_filter_all)
        TypeFilter.Started -> stringResource(Res.string.my_problems_started)
        TypeFilter.Waiting -> stringResource(Res.string.my_problems_waiting)
        TypeFilter.Completed -> stringResource(Res.string.my_problems_completed)
        TypeFilter.Expired -> stringResource(Res.string.my_problems_expired)
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
                TypeFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (option) {
                                    TypeFilter.All -> stringResource(Res.string.my_problems_filter_all)
                                    TypeFilter.Started -> stringResource(Res.string.my_problems_started)
                                    TypeFilter.Waiting -> stringResource(Res.string.my_problems_waiting)
                                    TypeFilter.Completed -> stringResource(Res.string.my_problems_completed)
                                    TypeFilter.Expired -> stringResource(Res.string.my_problems_expired)
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
private fun statusLabel(status: ProblemStatus): String {
    return when (status) {
        ProblemStatus.Started -> stringResource(Res.string.my_problems_started)
        ProblemStatus.Waiting -> stringResource(Res.string.my_problems_waiting)
        ProblemStatus.Completed -> stringResource(Res.string.my_problems_completed)
        ProblemStatus.Expired -> stringResource(Res.string.my_problems_expired)
    }
}

private fun statusOrder(status: ProblemStatus): Int {
    return when (status) {
        ProblemStatus.Started -> 0
        ProblemStatus.Waiting -> 1
        ProblemStatus.Completed -> 2
        ProblemStatus.Expired -> 3
    }
}
