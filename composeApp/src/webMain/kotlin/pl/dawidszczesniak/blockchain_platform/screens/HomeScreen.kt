package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import blockchain_platform.composeapp.generated.resources.home_stat_block_note
import blockchain_platform.composeapp.generated.resources.home_stat_block_title
import blockchain_platform.composeapp.generated.resources.home_stat_block_value
import blockchain_platform.composeapp.generated.resources.home_stat_nodes_note
import blockchain_platform.composeapp.generated.resources.home_stat_nodes_title
import blockchain_platform.composeapp.generated.resources.home_stat_nodes_value
import blockchain_platform.composeapp.generated.resources.home_stat_status_note
import blockchain_platform.composeapp.generated.resources.home_stat_status_title
import blockchain_platform.composeapp.generated.resources.home_stat_status_value
import blockchain_platform.composeapp.generated.resources.home_update_body_1
import blockchain_platform.composeapp.generated.resources.home_update_body_2
import blockchain_platform.composeapp.generated.resources.home_update_body_3
import blockchain_platform.composeapp.generated.resources.home_update_title_1
import blockchain_platform.composeapp.generated.resources.home_update_title_2
import blockchain_platform.composeapp.generated.resources.home_update_title_3
import blockchain_platform.composeapp.generated.resources.home_updates_title
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun HomeScreen(onNavigateToProblems: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        HeroSection(onNavigateToProblems = onNavigateToProblems)
        // TODO(backend): Replace dashboard stats with real metrics from backend.
        StatsSection()
        // TODO(backend): Replace updates feed with backend data.
        UpdatesSection()
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
private fun StatsSection() {
    BoxWithConstraints {
        val stacked = maxWidth < 900.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = stringResource(Res.string.home_stat_status_title),
                    value = stringResource(Res.string.home_stat_status_value),
                    note = stringResource(Res.string.home_stat_status_note),
                    badge = "S"
                )
                StatCard(
                    title = stringResource(Res.string.home_stat_nodes_title),
                    value = stringResource(Res.string.home_stat_nodes_value),
                    note = stringResource(Res.string.home_stat_nodes_note),
                    badge = "N"
                )
                StatCard(
                    title = stringResource(Res.string.home_stat_block_title),
                    value = stringResource(Res.string.home_stat_block_value),
                    note = stringResource(Res.string.home_stat_block_note),
                    badge = "#"
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.home_stat_status_title),
                    value = stringResource(Res.string.home_stat_status_value),
                    note = stringResource(Res.string.home_stat_status_note),
                    badge = "S"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.home_stat_nodes_title),
                    value = stringResource(Res.string.home_stat_nodes_value),
                    note = stringResource(Res.string.home_stat_nodes_note),
                    badge = "N"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.home_stat_block_title),
                    value = stringResource(Res.string.home_stat_block_value),
                    note = stringResource(Res.string.home_stat_block_note),
                    badge = "#"
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
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UpdatesSection() {
    BoxWithConstraints {
        val stacked = maxWidth < 900.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                UpdatesList()
                CommunityCard()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.home_updates_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier.weight(1.2f),
                            contentAlignment = Alignment.TopStart
                        ) {
                            UpdatesList(
                                modifier = Modifier.fillMaxWidth(),
                                showHeader = false
                            )
                        }
                        Box(
                            modifier = Modifier.weight(0.8f),
                            contentAlignment = Alignment.TopStart
                        ) {
                            CommunityCard(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesList(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
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
        // TODO(backend): Replace mock updates with real backend entries.
        val updates = listOf(
            stringResource(Res.string.home_update_title_1) to stringResource(Res.string.home_update_body_1),
            stringResource(Res.string.home_update_title_2) to stringResource(Res.string.home_update_body_2),
            stringResource(Res.string.home_update_title_3) to stringResource(Res.string.home_update_body_3),
        )
        updates.forEachIndexed { index, (title, body) ->
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
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (index < updates.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
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
