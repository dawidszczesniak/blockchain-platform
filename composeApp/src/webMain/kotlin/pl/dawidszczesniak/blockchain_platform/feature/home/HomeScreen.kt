package pl.dawidszczesniak.blockchain_platform.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.home_community_body
import blockchain_platform.composeapp.generated.resources.home_community_cta
import blockchain_platform.composeapp.generated.resources.home_community_title
import blockchain_platform.composeapp.generated.resources.home_hero_description
import blockchain_platform.composeapp.generated.resources.home_hero_primary_cta
import blockchain_platform.composeapp.generated.resources.home_hero_secondary_cta
import blockchain_platform.composeapp.generated.resources.home_hero_title_emphasis
import blockchain_platform.composeapp.generated.resources.home_hero_title_lead
import blockchain_platform.composeapp.generated.resources.home_stat_block_title
import blockchain_platform.composeapp.generated.resources.home_stat_nodes_note
import blockchain_platform.composeapp.generated.resources.home_stat_nodes_title
import blockchain_platform.composeapp.generated.resources.home_stat_status_note
import blockchain_platform.composeapp.generated.resources.home_stat_status_title
import blockchain_platform.composeapp.generated.resources.home_updates_title
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.ui.AppInlineLoader
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun HomeScreen(onNavigateToProblems: () -> Unit) {
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<HomeViewModel>() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()

    if (!state.showFullDashboardContent) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "dashboard",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (state.showHeroSection) {
            HeroSection(onNavigateToProblems = onNavigateToProblems)
        }
        if (state.showStatsSection) {
            StatsSection(state = state)
        }
        if (state.showLatestChallengesSection || state.showJoinBuildersSection) {
            UpdatesSection(
                state = state,
                showLatestChallenges = state.showLatestChallengesSection,
                showJoinBuilders = state.showJoinBuildersSection,
            )
        }
    }
}

@Composable
private fun HeroSection(onNavigateToProblems: () -> Unit) {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp)
    ) {
        HeroCopy(onNavigateToProblems = onNavigateToProblems)
    }
}

@Composable
private fun HeroCopy(
    modifier: Modifier = Modifier,
    onNavigateToProblems: () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.home_hero_title_lead),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(Res.string.home_hero_title_emphasis),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.home_hero_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onNavigateToProblems,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.home_hero_primary_cta))
            }
            OutlinedButton(
                onClick = { },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
            ) {
                Text(stringResource(Res.string.home_hero_secondary_cta))
            }
        }
    }
}

@Composable
private fun StatsSection(state: HomeState) {
    val hasError = state.errorMessage != null
    val activeChallengesValue = metricValue(
        value = state.activeChallenges,
        isLoading = state.isLoading,
        hasError = hasError,
    )
    val submissionsTodayValue = metricValue(
        value = state.submissionsToday,
        isLoading = state.isLoading,
        hasError = hasError,
    )
    val prizePoolValue = prizePoolLabel(
        label = state.prizePoolLabel,
        isLoading = state.isLoading,
        hasError = hasError,
    )
    val submissionsTrend = submissionsTrendLabel(
        submissionsDayOverDayPercent = state.submissionsDayOverDayPercent,
        isLoading = state.isLoading,
        hasError = hasError,
    )

    BoxWithConstraints {
        val stacked = maxWidth < 900.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = stringResource(Res.string.home_stat_status_title),
                    value = activeChallengesValue,
                    note = stringResource(Res.string.home_stat_status_note),
                    badge = "S",
                    isLoading = state.isLoading,
                )
                StatCard(
                    title = stringResource(Res.string.home_stat_nodes_title),
                    value = prizePoolValue,
                    note = stringResource(Res.string.home_stat_nodes_note),
                    badge = "N",
                    isLoading = state.isLoading,
                )
                StatCard(
                    title = stringResource(Res.string.home_stat_block_title),
                    value = submissionsTodayValue,
                    note = submissionsTrend,
                    badge = "#",
                    isLoading = state.isLoading,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.home_stat_status_title),
                    value = activeChallengesValue,
                    note = stringResource(Res.string.home_stat_status_note),
                    badge = "S",
                    isLoading = state.isLoading,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.home_stat_nodes_title),
                    value = prizePoolValue,
                    note = stringResource(Res.string.home_stat_nodes_note),
                    badge = "N",
                    isLoading = state.isLoading,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.home_stat_block_title),
                    value = submissionsTodayValue,
                    note = submissionsTrend,
                    badge = "#",
                    isLoading = state.isLoading,
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    note: String,
    badge: String,
    isLoading: Boolean,
) {
    AppSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.width(12.dp))
            if (isLoading) {
                AppInlineLoader()
            } else {
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UpdatesSection(
    state: HomeState,
    showLatestChallenges: Boolean,
    showJoinBuilders: Boolean,
) {
    if (!showLatestChallenges && !showJoinBuilders) {
        return
    }

    BoxWithConstraints {
        val stacked = maxWidth < 900.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (showLatestChallenges) {
                    UpdatesList(state = state)
                }
                if (showJoinBuilders) {
                    CommunityCard()
                }
            }
        } else {
            if (showLatestChallenges && showJoinBuilders) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(Res.string.home_updates_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier.weight(1.2f),
                                contentAlignment = Alignment.TopStart
                            ) {
                                UpdatesList(
                                    modifier = Modifier.fillMaxWidth(),
                                    showHeader = false,
                                    state = state,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(0.8f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.TopStart
                            ) {
                                CommunityCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            } else if (showLatestChallenges) {
                UpdatesList(
                    modifier = Modifier.fillMaxWidth(),
                    showHeader = true,
                    state = state,
                )
            } else if (showJoinBuilders) {
                CommunityCard(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun UpdatesList(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    state: HomeState,
) {
    Column(modifier = modifier) {
        if (showHeader) {
            Text(
                text = stringResource(Res.string.home_updates_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
        }
        val updates = state.updates
        if (updates.isEmpty()) {
            val emptyMessage = when {
                state.isLoading -> null
                state.errorMessage != null -> "Updates unavailable."
                else -> "No updates yet."
            }
            AppSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (state.isLoading) {
                    AppInlineLoader()
                } else {
                    Text(
                        text = emptyMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        updates.forEachIndexed { index, update ->
            AppSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(update.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(update.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (index < updates.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun metricValue(
    value: Int?,
    isLoading: Boolean,
    hasError: Boolean,
): String {
    return when {
        value != null -> value.toString()
        isLoading -> "..."
        hasError -> "N/A"
        else -> "0"
    }
}

private fun prizePoolLabel(
    label: String?,
    isLoading: Boolean,
    hasError: Boolean,
): String {
    return when {
        !label.isNullOrBlank() -> label
        isLoading -> "..."
        hasError -> "N/A"
        else -> "0"
    }
}

private fun submissionsTrendLabel(
    submissionsDayOverDayPercent: Int?,
    isLoading: Boolean,
    hasError: Boolean,
): String {
    return when {
        submissionsDayOverDayPercent != null && submissionsDayOverDayPercent >= 0 ->
            "+$submissionsDayOverDayPercent% vs previous day"

        submissionsDayOverDayPercent != null ->
            "$submissionsDayOverDayPercent% vs previous day"

        isLoading -> "Loading trend..."
        hasError -> "Trend unavailable."
        else -> "No previous-day data."
    }
}


@Composable
private fun CommunityCard(modifier: Modifier = Modifier) {
    AppSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(40.dp))
                    .border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DC",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.home_community_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.home_community_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
            ) {
                Text(stringResource(Res.string.home_community_cta))
            }
        }
    }
}
