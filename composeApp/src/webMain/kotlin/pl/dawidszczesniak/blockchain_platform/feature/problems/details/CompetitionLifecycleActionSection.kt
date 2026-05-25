@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.feature.problems.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.problem_details_action_cancel
import blockchain_platform.composeapp.generated.resources.problem_details_action_cancel_loading
import blockchain_platform.composeapp.generated.resources.problem_details_action_settle
import blockchain_platform.composeapp.generated.resources.problem_details_action_settle_loading
import blockchain_platform.composeapp.generated.resources.problem_details_action_title
import blockchain_platform.composeapp.generated.resources.problem_details_action_unlock_at
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

@Composable
internal fun CompetitionLifecycleActionSection(
    problem: ProblemSummary,
    registeredParticipants: Int,
    uiState: CompetitionLifecycleActionState,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onSettle: () -> Unit,
    onCancel: () -> Unit,
) {
    if (problem.onchainCompetitionId == null) {
        return
    }
    val effectiveSettlementStatus = uiState.settlementStatusOverride ?: problem.onchainSettlementStatus
    if (!effectiveSettlementStatus.isNullOrBlank() && effectiveSettlementStatus != "pending") {
        return
    }

    val nowMs = rememberCurrentEpochMillis()
    val submitUnlockAtMs = problem.submitDeadlineEpochSeconds?.times(1000L)
    val settleUnlocked = submitUnlockAtMs != null && nowMs > submitUnlockAtMs

    val joinUnlockAtMs = problem.joinDeadlineEpochSeconds?.times(1000L)
    val cancelUnlockAtMs = when {
        registeredParticipants < problem.requiredParticipants -> joinUnlockAtMs
        else -> submitUnlockAtMs
    }
    val cancelUnlocked = cancelUnlockAtMs != null && nowMs > cancelUnlockAtMs
    val currentWallet = uiState.currentWalletAddress.normalizeWalletForComparison()
    val creatorWallet = problem.creatorWalletAddress.normalizeWalletForComparison()
    val operatorWallet = uiState.operatorWalletAddress.normalizeWalletForComparison()
    val canSeeCancel = currentWallet != null && (currentWallet == creatorWallet || currentWallet == operatorWallet)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.problem_details_action_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = {
                        if (isLoggedIn) {
                            onSettle()
                        } else {
                            onRequireLogin()
                        }
                    },
                    modifier = Modifier.align(Alignment.Start),
                    enabled = !uiState.isSettling && !uiState.isCancelling && settleUnlocked,
                ) {
                    Text(
                        if (uiState.isSettling) {
                            stringResource(Res.string.problem_details_action_settle_loading)
                        } else {
                            stringResource(Res.string.problem_details_action_settle)
                        }
                    )
                }
                if (!settleUnlocked && submitUnlockAtMs != null) {
                    Text(
                        text = stringResource(
                            Res.string.problem_details_action_unlock_at,
                            formatUnlockAt(submitUnlockAtMs),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canSeeCancel) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = {
                            if (isLoggedIn) {
                                onCancel()
                            } else {
                                onRequireLogin()
                            }
                        },
                        modifier = Modifier.align(Alignment.Start),
                        enabled = !uiState.isSettling && !uiState.isCancelling && cancelUnlocked,
                    ) {
                        Text(
                            if (uiState.isCancelling) {
                                stringResource(Res.string.problem_details_action_cancel_loading)
                            } else {
                                stringResource(Res.string.problem_details_action_cancel)
                            }
                        )
                    }
                    if (!cancelUnlocked && cancelUnlockAtMs != null) {
                        Text(
                            text = stringResource(
                                Res.string.problem_details_action_unlock_at,
                                formatUnlockAt(cancelUnlockAtMs),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!uiState.statusMessage.isNullOrBlank()) {
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!uiState.errorMessage.isNullOrBlank()) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun rememberCurrentEpochMillis(): Long {
    var value by remember { mutableLongStateOf(currentEpochMillisForLifecycle()) }
    LaunchedEffect(Unit) {
        while (true) {
            value = currentEpochMillisForLifecycle()
            delay(1_000L)
        }
    }
    return value
}

private fun String?.normalizeWalletForComparison(): String? {
    return this
        ?.trim()
        ?.removePrefix("0x")
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }
        ?.let { "0x$it" }
}

private fun formatUnlockAt(epochMillis: Long): String {
    return formatUnlockAtLifecycleJs(epochMillis.toDouble())
}

@JsFun("() => Date.now()")
private external fun currentEpochMillisLifecycleJs(): Double

@JsFun("(epochMillis) => { const date = new Date(Number(epochMillis)); const pad2 = (value) => String(value).padStart(2, '0'); return pad2(date.getDate()) + '.' + pad2(date.getMonth() + 1) + '.' + date.getFullYear() + ', ' + pad2(date.getHours()) + ':' + pad2(date.getMinutes()); }")
private external fun formatUnlockAtLifecycleJs(epochMillis: Double): String

private fun currentEpochMillisForLifecycle(): Long = currentEpochMillisLifecycleJs().toLong()
