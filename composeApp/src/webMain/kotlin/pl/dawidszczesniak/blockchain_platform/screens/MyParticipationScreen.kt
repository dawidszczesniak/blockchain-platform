package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface
import kotlin.math.max

private const val PAGE_SIZE = 20
// TODO(backend): Replace mock counts with real backend totals.
private const val TOTAL_PARTICIPATIONS = 80
// TODO(backend): Remove mock toggle when list is fetched from backend.
private const val USE_MOCK_DATA = true

private enum class ParticipationStatus {
    Submitted,
    NotSubmitted,
}

private enum class ParticipationFilter {
    All,
    Submitted,
    NotSubmitted,
}

private data class ParticipationProblem(
    val title: String,
    val status: ParticipationStatus,
    val details: List<String>,
)

@Composable
fun MyParticipationScreen(onBrowseProblems: () -> Unit) {
    val listState = rememberLazyListState()

    // TODO(backend): Fetch participation list for the current user from backend.
    val allProblems = if (USE_MOCK_DATA) {
        List(TOTAL_PARTICIPATIONS) { index ->
            val status = if (index % 2 == 0) {
                ParticipationStatus.Submitted
            } else {
                ParticipationStatus.NotSubmitted
            }
            val dayLeft = (index % 14) + 1
            val participants = 5 + (index % 20)
            val title = if (status == ParticipationStatus.Submitted) {
                "Joined #${index + 1} — Consensus tuning"
            } else {
                "Joined #${index + 1} — Proof batching"
            }
            val submissionLabel = stringResource(
                if (status == ParticipationStatus.Submitted) {
                    Res.string.participation_submission_sent
                } else {
                    Res.string.participation_submission_pending
                }
            )
            ParticipationProblem(
                title = title,
                status = status,
                details = listOf(
                    stringResource(Res.string.participation_time_left, "${dayLeft}d"),
                    stringResource(Res.string.participation_participants, participants),
                    stringResource(Res.string.participation_submission, submissionLabel)
                )
            )
        }
    } else {
        emptyList()
    }

    var filter by remember { mutableStateOf(ParticipationFilter.All) }
    var currentPage by remember { mutableStateOf(1) }

    val filtered = when (filter) {
        ParticipationFilter.All -> allProblems
        ParticipationFilter.Submitted -> allProblems.filter { it.status == ParticipationStatus.Submitted }
        ParticipationFilter.NotSubmitted -> allProblems.filter { it.status == ParticipationStatus.NotSubmitted }
    }
    val totalPages = max(1, (filtered.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val pageIndex = currentPage.coerceIn(1, totalPages)
    val pageProblems = filtered.drop((pageIndex - 1) * PAGE_SIZE).take(PAGE_SIZE)

    LaunchedEffect(filter, totalPages) {
        if (currentPage > totalPages) currentPage = totalPages
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
                text = stringResource(Res.string.participation_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (filtered.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                ParticipationFilterRow(
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
            EmptyParticipation(onBrowseProblems = onBrowseProblems)
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pageProblems) { item ->
                    ParticipationCard(problem = item)
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
        }
    }
}

@Composable
private fun ParticipationCard(problem: ParticipationProblem) {
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
private fun ParticipationFilterRow(
    current: ParticipationFilter,
    onChange: (ParticipationFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.participation_filter_label)
    val buttonText = when (current) {
        ParticipationFilter.All -> stringResource(Res.string.participation_filter_all)
        ParticipationFilter.Submitted -> stringResource(Res.string.participation_filter_submitted)
        ParticipationFilter.NotSubmitted -> stringResource(Res.string.participation_filter_pending)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            OutlinedButton(onClick = { expanded = true }) {
                Text(buttonText)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = 0.dp, y = 6.dp)
            ) {
                ParticipationFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (option) {
                                    ParticipationFilter.All -> stringResource(Res.string.participation_filter_all)
                                    ParticipationFilter.Submitted -> stringResource(Res.string.participation_filter_submitted)
                                    ParticipationFilter.NotSubmitted -> stringResource(Res.string.participation_filter_pending)
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
    Box(
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
                        Text(page.toString(), color = MaterialTheme.colorScheme.onPrimary)
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
private fun EmptyParticipation(onBrowseProblems: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.participation_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.participation_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBrowseProblems) {
            Text(stringResource(Res.string.participation_empty_action))
        }
    }
}

@Composable
private fun statusLabel(status: ParticipationStatus): String {
    return when (status) {
        ParticipationStatus.Submitted -> stringResource(Res.string.participation_filter_submitted)
        ParticipationStatus.NotSubmitted -> stringResource(Res.string.participation_filter_pending)
    }
}
