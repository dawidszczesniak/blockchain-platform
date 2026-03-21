package pl.dawidszczesniak.blockchain_platform.feature.problems.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.days_count
import blockchain_platform.composeapp.generated.resources.problem_details_back
import blockchain_platform.composeapp.generated.resources.problem_details_constraints_title
import blockchain_platform.composeapp.generated.resources.problem_details_editor_language
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_join_action
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_join_action_loading
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_join_body
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_join_title
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_loading
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_waiting_body
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_waiting_title
import blockchain_platform.composeapp.generated.resources.problem_details_editor_run
import blockchain_platform.composeapp.generated.resources.problem_details_editor_submit
import blockchain_platform.composeapp.generated.resources.problem_details_editor_title
import blockchain_platform.composeapp.generated.resources.problem_details_example_explanation
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
import kotlin.math.max
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemExample
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.statement.ProblemStatementContent
import pl.dawidszczesniak.blockchain_platform.feature.problems.statement.decodeProblemDescription
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun ProblemDetailsScreen(
    problem: ProblemSummary,
    viewModel: ProblemDetailsViewModel,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onBackToProblems: () -> Unit,
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
    val gateState by viewModel.state.collectAsState()
    LaunchedEffect(problem.id, isLoggedIn) {
        viewModel.load(
            problemId = problem.id,
            initialRegisteredParticipants = problem.registeredParticipants,
            requiredParticipants = problem.requiredParticipants,
            isLoggedIn = isLoggedIn,
        )
    }

    val registeredParticipants = max(
        problem.registeredParticipants,
        gateState.registeredParticipants ?: 0,
    )
    val hasCompetitionStarted = registeredParticipants >= problem.requiredParticipants
    val editorOverlayState = EditorOverlayState(
        show = gateState.isMembershipLoading || !gateState.isJoined || !hasCompetitionStarted,
        isLoadingMembership = gateState.isMembershipLoading,
        requiresJoin = !gateState.isMembershipLoading && !gateState.isJoined,
        waitingForStart = !gateState.isMembershipLoading && gateState.isJoined && !hasCompetitionStarted,
        isJoining = gateState.isJoining,
        joinErrorMessage = gateState.joinErrorMessage,
        registeredParticipants = registeredParticipants,
        requiredParticipants = problem.requiredParticipants,
    )

    var solutionCode by remember(problem.id) { mutableStateOf(defaultSolutionTemplate(problem)) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBackToProblems) {
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
                    ProblemStatementPane(
                        modifier = Modifier.fillMaxWidth(),
                        problem = problem,
                        statementContent = statementContent,
                    )
                    CodeEditorPane(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp),
                        code = solutionCode,
                        onCodeChange = { solutionCode = it },
                        fillEditorSpace = false,
                        overlayState = editorOverlayState,
                        onJoinRequest = {
                            if (isLoggedIn) {
                                viewModel.join(problem.id)
                            } else {
                                onRequireLogin()
                            }
                        },
                    )
                }
            } else {
                val paneWidth = (maxWidth - 14.dp) / 2
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProblemStatementPane(
                        modifier = Modifier
                            .width(paneWidth)
                            .fillMaxHeight(),
                        problem = problem,
                        statementContent = statementContent,
                    )
                    CodeEditorPane(
                        modifier = Modifier
                            .width(paneWidth)
                            .fillMaxHeight(),
                        code = solutionCode,
                        onCodeChange = { solutionCode = it },
                        fillEditorSpace = true,
                        overlayState = editorOverlayState,
                        onJoinRequest = {
                            if (isLoggedIn) {
                                viewModel.join(problem.id)
                            } else {
                                onRequireLogin()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProblemStatementPane(
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
                    ExampleBlock(index = index, example = example)
                }
            }
            Spacer(Modifier.height(4.dp))
            DetailLine(stringResource(Res.string.problem_details_requirement_participants, problem.requiredParticipants))
            DetailLine(stringResource(Res.string.problem_details_requirement_prize, problem.prizeAmount))
            DetailLine(stringResource(Res.string.problem_details_requirement_entry_fee, problem.entryFeeAmount))
            DetailLine(
                stringResource(
                    Res.string.problem_details_requirement_start,
                    stringResource(Res.string.days_count, problem.daysToStart),
                ),
            )
            DetailLine(stringResource(Res.string.problem_details_requirement_join, problem.joinUntilLabel))
            DetailLine(stringResource(Res.string.problem_details_requirement_submit, problem.submitUntilLabel))
        }
    }
}

@Composable
private fun ExampleBlock(
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
            Text(
                text = stringResource(Res.string.problem_details_example_explanation, example.explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeEditorPane(
    modifier: Modifier,
    code: String,
    onCodeChange: (String) -> Unit,
    fillEditorSpace: Boolean,
    overlayState: EditorOverlayState,
    onJoinRequest: () -> Unit,
) {
    val editorBlocked = overlayState.show

    Box(modifier = modifier) {
        AppSurface(modifier = Modifier.fillMaxSize()) {
            val editorModifier = if (fillEditorSpace) {
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.76f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)
            }

            Text(
                text = stringResource(Res.string.problem_details_editor_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.problem_details_editor_language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = editorModifier,
                enabled = !editorBlocked,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                minLines = 16,
                maxLines = 50,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { },
                    enabled = !editorBlocked,
                ) {
                    Text(stringResource(Res.string.problem_details_editor_run))
                }
                Button(
                    onClick = { },
                    enabled = !editorBlocked,
                ) {
                    Text(stringResource(Res.string.problem_details_editor_submit))
                }
            }
        }

        if (overlayState.show) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
            )
            OutlinedCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        overlayState.isLoadingMembership -> {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(Res.string.problem_details_editor_locked_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }

                        overlayState.requiresJoin -> {
                            Text(
                                text = stringResource(Res.string.problem_details_editor_locked_join_title),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(Res.string.problem_details_editor_locked_join_body),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = onJoinRequest,
                                enabled = !overlayState.isJoining,
                            ) {
                                Text(
                                    if (overlayState.isJoining) {
                                        stringResource(Res.string.problem_details_editor_locked_join_action_loading)
                                    } else {
                                        stringResource(Res.string.problem_details_editor_locked_join_action)
                                    },
                                )
                            }
                        }

                        overlayState.waitingForStart -> {
                            Text(
                                text = stringResource(Res.string.problem_details_editor_locked_waiting_title),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(
                                    Res.string.problem_details_editor_locked_waiting_body,
                                    overlayState.registeredParticipants,
                                    overlayState.requiredParticipants,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (!overlayState.joinErrorMessage.isNullOrBlank()) {
                        Text(
                            text = overlayState.joinErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun defaultSolutionTemplate(problem: ProblemSummary): String {
    return """
        fun solve(input: String): String {
            // ${problem.title}
            // TODO: implement your solution
            return input
        }
    """.trimIndent()
}

private data class EditorOverlayState(
    val show: Boolean,
    val isLoadingMembership: Boolean,
    val requiresJoin: Boolean,
    val waitingForStart: Boolean,
    val isJoining: Boolean,
    val joinErrorMessage: String?,
    val registeredParticipants: Int,
    val requiredParticipants: Int,
)
