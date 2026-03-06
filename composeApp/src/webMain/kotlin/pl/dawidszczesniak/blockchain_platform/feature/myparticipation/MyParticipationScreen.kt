package pl.dawidszczesniak.blockchain_platform.feature.myparticipation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
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
import blockchain_platform.composeapp.generated.resources.loading_more
import blockchain_platform.composeapp.generated.resources.participation_empty_action
import blockchain_platform.composeapp.generated.resources.participation_empty_body
import blockchain_platform.composeapp.generated.resources.participation_empty_title
import blockchain_platform.composeapp.generated.resources.participation_filter_all
import blockchain_platform.composeapp.generated.resources.participation_filter_label
import blockchain_platform.composeapp.generated.resources.participation_filter_pending
import blockchain_platform.composeapp.generated.resources.participation_filter_submitted
import blockchain_platform.composeapp.generated.resources.participation_attempts
import blockchain_platform.composeapp.generated.resources.participation_participants
import blockchain_platform.composeapp.generated.resources.participation_submission
import blockchain_platform.composeapp.generated.resources.participation_submission_pending
import blockchain_platform.composeapp.generated.resources.participation_submission_sent
import blockchain_platform.composeapp.generated.resources.participation_time_left
import blockchain_platform.composeapp.generated.resources.participation_title
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.domain.model.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun MyParticipationScreen(onBrowseProblems: () -> Unit) {
    val listState = rememberLazyListState()
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<MyParticipationViewModel>() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()

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
            if (!state.isEmpty) {
                Spacer(Modifier.weight(1f))
                MyParticipationFilterRow(
                    current = state.filter,
                    onChange = {
                        viewModel.onIntent(MyParticipationIntent.ChangeFilter(it))
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
            EmptyMyParticipation(onBrowseProblems = onBrowseProblems)
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.pageItems) { item ->
                    MyParticipationCard(problem = item)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    PaginationRow(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        onPageSelected = { viewModel.onIntent(MyParticipationIntent.ChangePage(it)) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MyParticipationCard(problem: ParticipationProblem) {
    val submissionLabel = stringResource(
        if (problem.status == ParticipationStatus.Submitted) {
            Res.string.participation_submission_sent
        } else {
            Res.string.participation_submission_pending
        }
    )
    val details = listOf(
        stringResource(Res.string.participation_time_left, problem.timeLeftLabel),
        stringResource(Res.string.participation_participants, problem.participants),
        stringResource(Res.string.participation_attempts, problem.attemptsCount),
        stringResource(Res.string.participation_submission, submissionLabel)
    )

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
            details.forEach { detail ->
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
private fun MyParticipationFilterRow(
    current: MyParticipationFilter,
    onChange: (MyParticipationFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.participation_filter_label)
    val buttonText = when (current) {
        MyParticipationFilter.All -> stringResource(Res.string.participation_filter_all)
        MyParticipationFilter.Submitted -> stringResource(Res.string.participation_filter_submitted)
        MyParticipationFilter.NotSubmitted -> stringResource(Res.string.participation_filter_pending)
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
                MyParticipationFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (option) {
                                    MyParticipationFilter.All -> stringResource(Res.string.participation_filter_all)
                                    MyParticipationFilter.Submitted -> stringResource(Res.string.participation_filter_submitted)
                                    MyParticipationFilter.NotSubmitted -> stringResource(Res.string.participation_filter_pending)
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
private fun EmptyMyParticipation(onBrowseProblems: () -> Unit) {
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
