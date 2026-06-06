package pl.dawidszczesniak.blockchain_platform.feature.problems.details

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.problem_details_reference_code_title
import blockchain_platform.composeapp.generated.resources.problem_details_reference_consensus
import blockchain_platform.composeapp.generated.resources.problem_details_reference_memory
import blockchain_platform.composeapp.generated.resources.problem_details_reference_runtime
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary

@Composable
internal fun PublicReferenceSolutionSection(problem: ProblemSummary) {
    if (problem.referenceSolutionCode.isBlank()) {
        return
    }

    Text(
        text = stringResource(Res.string.problem_details_reference_code_title),
        style = MaterialTheme.typography.titleSmall,
    )
    problem.referenceRuntimeMs?.let { runtimeMs ->
        Text(
            text = stringResource(Res.string.problem_details_reference_runtime, runtimeMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    problem.referenceMemoryUsedKb?.let { memoryUsedKb ->
        Text(
            text = stringResource(Res.string.problem_details_reference_memory, memoryUsedKb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    problem.referenceConsensusNodes?.let { consensusNodes ->
        Text(
            text = stringResource(Res.string.problem_details_reference_consensus, consensusNodes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 420.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        val horizontalScroll = rememberScrollState()
        val verticalScroll = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .horizontalScroll(horizontalScroll)
                .verticalScroll(verticalScroll),
        ) {
            SelectionContainer {
                Text(
                    text = problem.referenceSolutionCode,
                    modifier = Modifier.widthIn(min = 0.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                )
            }
        }
    }
}
