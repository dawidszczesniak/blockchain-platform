package pl.dawidszczesniak.blockchain_platform.feature.problems.participation

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import blockchain_platform.composeapp.generated.resources.participation_empty_action
import blockchain_platform.composeapp.generated.resources.participation_empty_body
import blockchain_platform.composeapp.generated.resources.participation_empty_title
import blockchain_platform.composeapp.generated.resources.participation_filter_all
import blockchain_platform.composeapp.generated.resources.participation_filter_label
import blockchain_platform.composeapp.generated.resources.participation_filter_pending
import blockchain_platform.composeapp.generated.resources.participation_filter_submitted
import blockchain_platform.composeapp.generated.resources.participation_load_failed
import blockchain_platform.composeapp.generated.resources.participation_attempts
import blockchain_platform.composeapp.generated.resources.participation_participants
import blockchain_platform.composeapp.generated.resources.participation_retry
import blockchain_platform.composeapp.generated.resources.participation_submission
import blockchain_platform.composeapp.generated.resources.participation_submission_pending
import blockchain_platform.composeapp.generated.resources.participation_submission_sent
import blockchain_platform.composeapp.generated.resources.participation_time_left
import blockchain_platform.composeapp.generated.resources.nav_my_participation
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ParticipationStatus
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.ui.AppInlineLoader
import pl.dawidszczesniak.blockchain_platform.ui.AppPanelLoader
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun MyParticipationScreen(
    onBrowseProblems: () -> Unit,
    onOpenProblem: (Int, (Boolean) -> Unit) -> Unit,
) {
    val listState = rememberLazyListState()
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<MyParticipationViewModel>() }
    var openingProblemId by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(MyParticipationIntent.Refresh)
    }
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
                text = stringResource(Res.string.nav_my_participation),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            if (state.items.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    MyParticipationFilterRow(
                        current = state.filter,
                        onChange = {
                            viewModel.onIntent(MyParticipationIntent.ChangeFilter(it))
                        }
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            if (!state.errorMessage.isNullOrBlank()) {
                ParticipationErrorCard(
                    message = state.errorMessage,
                    onRetry = { viewModel.onIntent(MyParticipationIntent.Refresh) }
                )
                Spacer(Modifier.height(18.dp))
            }

            if (!state.isLoading && state.isEmpty && state.errorMessage.isNullOrBlank()) {
                EmptyMyParticipation(onBrowseProblems = onBrowseProblems)
            } else if (state.isLoading || state.pageItems.isNotEmpty()) {
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
                            items(state.pageItems) { item ->
                                MyParticipationCard(
                                    problem = item,
                                    isOpening = openingProblemId == item.id,
                                    onOpenProblem = {
                                        openingProblemId = item.id
                                        onOpenProblem(item.id) { opened ->
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
                                    onPageSelected = { viewModel.onIntent(MyParticipationIntent.ChangePage(it)) }
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
    }
}

@Composable
private fun ParticipationErrorCard(
    message: String?,
    onRetry: () -> Unit,
) {
    AppSurface(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.participation_load_failed),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(Res.string.participation_retry))
        }
    }
}

@Composable
private fun MyParticipationCard(
    problem: ParticipationProblem,
    isOpening: Boolean,
    onOpenProblem: () -> Unit,
) {
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
