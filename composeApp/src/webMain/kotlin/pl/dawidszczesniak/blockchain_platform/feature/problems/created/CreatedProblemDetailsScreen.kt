package pl.dawidszczesniak.blockchain_platform.feature.problems.created

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.days_count
import blockchain_platform.composeapp.generated.resources.my_problem_finished_on
import blockchain_platform.composeapp.generated.resources.my_problem_participants
import blockchain_platform.composeapp.generated.resources.my_problem_registration_ends
import blockchain_platform.composeapp.generated.resources.my_problem_started_on
import blockchain_platform.composeapp.generated.resources.my_problem_submitted
import blockchain_platform.composeapp.generated.resources.my_problem_time_elapsed
import blockchain_platform.composeapp.generated.resources.my_problem_winner
import blockchain_platform.composeapp.generated.resources.my_problems_completed
import blockchain_platform.composeapp.generated.resources.my_problems_expired
import blockchain_platform.composeapp.generated.resources.my_problems_started
import blockchain_platform.composeapp.generated.resources.my_problems_waiting
import blockchain_platform.composeapp.generated.resources.nav_my_problems
import blockchain_platform.composeapp.generated.resources.problem_details_back
import blockchain_platform.composeapp.generated.resources.problem_details_constraints_title
import blockchain_platform.composeapp.generated.resources.problem_details_example_input
import blockchain_platform.composeapp.generated.resources.problem_details_example_label
import blockchain_platform.composeapp.generated.resources.problem_details_example_output
import blockchain_platform.composeapp.generated.resources.problem_details_examples_title
import blockchain_platform.composeapp.generated.resources.problem_details_requirement_entry_fee
import blockchain_platform.composeapp.generated.resources.problem_details_requirement_join
import blockchain_platform.composeapp.generated.resources.problem_details_requirement_participants
import blockchain_platform.composeapp.generated.resources.problem_details_requirement_prize
import blockchain_platform.composeapp.generated.resources.problem_details_requirement_start
import blockchain_platform.composeapp.generated.resources.problem_details_requirement_submit
import blockchain_platform.composeapp.generated.resources.problem_details_statement
import blockchain_platform.composeapp.generated.resources.problem_details_title
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblem
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.CreatedProblemStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemExample
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.statement.ProblemStatementContent
import pl.dawidszczesniak.blockchain_platform.feature.problems.statement.decodeProblemDescription
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface
import kotlin.math.max
import kotlin.math.min

@Composable
fun CreatedProblemDetailsScreen(
    problem: ProblemSummary,
    createdProblem: CreatedProblem,
    onBackToMyProblems: () -> Unit,
) {
    val statementContent = remember(problem.id, problem.description, problem.constraints, problem.examples) {
        val decodedLegacy = decodeProblemDescription(problem.description)
        ProblemStatementContent(
            statement = decodedLegacy.statement,
            constraints = problem.constraints.ifBlank { decodedLegacy.constraints },
            examples = if (problem.examples.isNotEmpty()) {
                problem.examples
            } else {
                decodedLegacy.examples
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBackToMyProblems) {
                Text(stringResource(Res.string.problem_details_back))
            }
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = { },
                label = { Text(stringResource(Res.string.problem_details_title)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                ),
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 980.dp

            if (compact) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CreatedProblemStatementPane(
                        modifier = Modifier.fillMaxWidth(),
                        problem = problem,
                        statementContent = statementContent,
                    )
                    CreatedProblemOwnerPane(
                        modifier = Modifier.fillMaxWidth(),
                        problem = problem,
                        createdProblem = createdProblem,
                    )
                }
            } else {
                val paneWidth = (maxWidth - 14.dp) / 2
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CreatedProblemStatementPane(
                        modifier = Modifier
                            .width(paneWidth)
                            .fillMaxHeight(),
                        problem = problem,
                        statementContent = statementContent,
                    )
                    CreatedProblemOwnerPane(
                        modifier = Modifier
                            .width(paneWidth)
                            .fillMaxHeight(),
                        problem = problem,
                        createdProblem = createdProblem,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatedProblemStatementPane(
    modifier: Modifier,
    problem: ProblemSummary,
    statementContent: ProblemStatementContent,
) {
    val detailsScroll = rememberScrollState()

    AppSurface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(detailsScroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = problem.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.problem_details_statement),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = statementContent.statement,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (statementContent.constraints.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.problem_details_constraints_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = statementContent.constraints,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (statementContent.examples.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.problem_details_examples_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                statementContent.examples.forEachIndexed { index, example ->
                    CreatedProblemExampleBlock(index = index, example = example)
                }
            }
        }
    }
}

@Composable
private fun CreatedProblemOwnerPane(
    modifier: Modifier,
    problem: ProblemSummary,
    createdProblem: CreatedProblem,
) {
    val detailsScroll = rememberScrollState()
    val required = max(1, createdProblem.requiredParticipants)
    val registered = min(createdProblem.registeredParticipants, required)
    val progress = registered.toFloat() / required.toFloat()

    AppSurface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(detailsScroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                text = stringResource(Res.string.nav_my_problems),
                style = MaterialTheme.typography.titleMedium,
            )
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = { },
                    label = { Text(createdProblemStatusLabel(createdProblem.status)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    ),
                )
            }

            CreatedProblemDetailLine(
                stringResource(
                    Res.string.my_problem_participants,
                    registered,
                    createdProblem.requiredParticipants,
                )
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            CreatedProblemDetailLine(stringResource(Res.string.my_problem_submitted, createdProblem.submissions))
            CreatedProblemDetailLine(
                stringResource(Res.string.problem_details_requirement_prize, problem.prizeAmount)
            )
            CreatedProblemDetailLine(
                stringResource(Res.string.problem_details_requirement_entry_fee, problem.entryFeeAmount)
            )
            CreatedProblemDetailLine(
                stringResource(
                    Res.string.problem_details_requirement_start,
                    stringResource(Res.string.days_count, problem.daysToStart),
                )
            )
            CreatedProblemDetailLine(
                stringResource(Res.string.problem_details_requirement_join, problem.joinUntilLabel)
            )
            CreatedProblemDetailLine(
                stringResource(Res.string.problem_details_requirement_submit, problem.submitUntilLabel)
            )
            createdProblemDetailLines(createdProblem).forEach { line ->
                CreatedProblemDetailLine(line)
            }
        }
    }
}

@Composable
private fun CreatedProblemExampleBlock(
    index: Int,
    example: ProblemExample,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.problem_details_example_label, index + 1),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(Res.string.problem_details_example_input, example.input),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.problem_details_example_output, example.output),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreatedProblemDetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun createdProblemStatusLabel(status: CreatedProblemStatus): String {
    return when (status) {
        CreatedProblemStatus.Started -> stringResource(Res.string.my_problems_started)
        CreatedProblemStatus.Waiting -> stringResource(Res.string.my_problems_waiting)
        CreatedProblemStatus.Completed -> stringResource(Res.string.my_problems_completed)
        CreatedProblemStatus.Expired -> stringResource(Res.string.my_problems_expired)
    }
}

@Composable
private fun createdProblemDetailLines(problem: CreatedProblem): List<String> {
    return buildList {
        problem.startedOn?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(Res.string.my_problem_started_on, it))
        }
        problem.finishedOn?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(Res.string.my_problem_finished_on, it))
        }
        problem.registrationEnds?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(Res.string.my_problem_registration_ends, it))
        }
        problem.timeElapsed?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(Res.string.my_problem_time_elapsed, it))
        }
        problem.winner?.takeIf { it.isNotBlank() }?.let {
            add(stringResource(Res.string.my_problem_winner, it))
        }
    }
}
