@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.feature.problems.details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.days_count
import blockchain_platform.composeapp.generated.resources.operation_cancel
import blockchain_platform.composeapp.generated.resources.problem_details_back
import blockchain_platform.composeapp.generated.resources.problem_details_action_cancel_loading
import blockchain_platform.composeapp.generated.resources.problem_details_action_settle_loading
import blockchain_platform.composeapp.generated.resources.problem_details_constraints_title
import blockchain_platform.composeapp.generated.resources.problem_details_editor_language
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_full_body
import blockchain_platform.composeapp.generated.resources.problem_details_editor_locked_full_title
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
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_actual
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_expected
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_hidden_failed
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_passed
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_status_error
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_status_failed
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_status_passed
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_status_timeout
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_test_label
import blockchain_platform.composeapp.generated.resources.problem_details_run_result_time
import blockchain_platform.composeapp.generated.resources.problem_details_run_running
import blockchain_platform.composeapp.generated.resources.problem_details_submit_consensus
import blockchain_platform.composeapp.generated.resources.problem_details_submit_error_prefix
import blockchain_platform.composeapp.generated.resources.problem_details_submit_hash
import blockchain_platform.composeapp.generated.resources.problem_details_submit_onchain_error_title
import blockchain_platform.composeapp.generated.resources.problem_details_submit_proxy_address
import blockchain_platform.composeapp.generated.resources.problem_details_submit_explorer
import blockchain_platform.composeapp.generated.resources.problem_details_submit_tx_hash
import blockchain_platform.composeapp.generated.resources.problem_details_submit_runtime
import blockchain_platform.composeapp.generated.resources.problem_details_submit_result_title
import blockchain_platform.composeapp.generated.resources.problem_details_submit_sandbox_result_hash
import blockchain_platform.composeapp.generated.resources.problem_details_submit_submitting
import blockchain_platform.composeapp.generated.resources.problem_details_submit_popup_ok
import blockchain_platform.composeapp.generated.resources.problem_details_submit_popup_retry
import blockchain_platform.composeapp.generated.resources.problem_details_submit_popup_retry_loading
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
import blockchain_platform.composeapp.generated.resources.problem_details_run_error_console_title
import blockchain_platform.composeapp.generated.resources.problem_details_statement
import blockchain_platform.composeapp.generated.resources.problem_details_title
import kotlin.math.max
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.feature.platform.formatAmountWithSymbol
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemExample
import pl.dawidszczesniak.blockchain_platform.feature.problems.domain.ProblemSummary
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemTestResultDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.ui.AppScrollbarTrack
import pl.dawidszczesniak.blockchain_platform.ui.AppVerticalScrollbar
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
        val decodedDescription = decodeProblemDescription(problem.description)
        ProblemStatementContent(
            statement = decodedDescription.statement,
            constraints = problem.constraints.ifBlank { decodedDescription.constraints },
            examples = if (problem.examples.isNotEmpty()) {
                problem.examples
            } else {
                decodedDescription.examples
            },
        )
    }
    val gateState by viewModel.state.collectAsState()
    val competitionActionState by viewModel.competitionActionState.collectAsState()
    LaunchedEffect(problem.id, isLoggedIn) {
        viewModel.load(
            problemId = problem.id,
            initialRegisteredParticipants = problem.registeredParticipants,
            requiredParticipants = problem.requiredParticipants,
            isLoggedIn = isLoggedIn,
        )
        viewModel.loadCompetitionLifecycle(problem.id)
    }

    val registeredParticipants = max(
        problem.registeredParticipants,
        gateState.registeredParticipants ?: 0,
    )
    val hasReachedParticipantLimit = registeredParticipants >= problem.requiredParticipants
    val editorOverlayState = EditorOverlayState(
        show = gateState.isMembershipLoading || !gateState.isJoined || !hasReachedParticipantLimit,
        isLoadingMembership = gateState.isMembershipLoading,
        requiresJoin = !gateState.isMembershipLoading && !gateState.isJoined && !hasReachedParticipantLimit,
        competitionFull = !gateState.isMembershipLoading && !gateState.isJoined && hasReachedParticipantLimit,
        waitingForStart = !gateState.isMembershipLoading && gateState.isJoined && !hasReachedParticipantLimit,
        isJoining = gateState.isJoining,
        joinStatusMessage = gateState.joinStatusMessage,
        joinErrorMessage = gateState.joinErrorMessage,
        registeredParticipants = registeredParticipants,
        requiredParticipants = problem.requiredParticipants,
    )

    var solutionCode by remember(problem.id) { mutableStateOf(defaultSolutionTemplate()) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
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
                            showScrollbar = false,
                            problem = problem,
                            statementContent = statementContent,
                            registeredParticipants = registeredParticipants,
                            competitionActionState = competitionActionState,
                            isLoggedIn = isLoggedIn,
                            onRequireLogin = onRequireLogin,
                            onSettle = { viewModel.settleCompetition(problem.id) },
                            onCancel = { viewModel.cancelCompetition(problem.id) },
                        )
                        CodeEditorPane(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 320.dp),
                            showScrollbar = false,
                            code = solutionCode,
                            onCodeChange = { solutionCode = it },
                            fillEditorSpace = false,
                            overlayState = editorOverlayState,
                            isRunning = gateState.isRunning,
                            runErrorMessage = gateState.runErrorMessage,
                            runResult = gateState.runResult,
                            isSubmitting = gateState.isSubmitting,
                            submitErrorMessage = gateState.submitErrorMessage,
                            submitResult = gateState.submitResult,
                            submitInlineErrorMessage = gateState.submitInlineErrorMessage,
                            onRun = { viewModel.run(problem.id, solutionCode, "kotlin") },
                            onSubmit = { viewModel.submit(problem.id, solutionCode, "kotlin") },
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
                            showScrollbar = true,
                            problem = problem,
                            statementContent = statementContent,
                            registeredParticipants = registeredParticipants,
                            competitionActionState = competitionActionState,
                            isLoggedIn = isLoggedIn,
                            onRequireLogin = onRequireLogin,
                            onSettle = { viewModel.settleCompetition(problem.id) },
                            onCancel = { viewModel.cancelCompetition(problem.id) },
                        )
                        CodeEditorPane(
                            modifier = Modifier
                                .width(paneWidth)
                                .fillMaxHeight(),
                            showScrollbar = true,
                            code = solutionCode,
                            onCodeChange = { solutionCode = it },
                            fillEditorSpace = true,
                            overlayState = editorOverlayState,
                            isRunning = gateState.isRunning,
                            runErrorMessage = gateState.runErrorMessage,
                            runResult = gateState.runResult,
                            isSubmitting = gateState.isSubmitting,
                            submitErrorMessage = gateState.submitErrorMessage,
                            submitResult = gateState.submitResult,
                            submitInlineErrorMessage = gateState.submitInlineErrorMessage,
                            onRun = { viewModel.run(problem.id, solutionCode, "kotlin") },
                            onSubmit = { viewModel.submit(problem.id, solutionCode, "kotlin") },
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
}

@Composable
internal fun ProblemDetailsOverlay(
    gateState: ProblemDetailsGateState?,
    competitionActionState: CompetitionLifecycleActionState,
    onCancelCompetitionAction: () -> Unit,
    onCancelJoin: () -> Unit,
    onCancelRun: () -> Unit,
    onCancelSubmit: () -> Unit,
    onDismissSubmitPopup: () -> Unit,
    onRetrySubmit: () -> Unit,
) {
    when {
        competitionActionState.isSettling || competitionActionState.isCancelling -> {
            ProblemDetailsProgressOverlay(
                message = competitionActionState.statusMessage?.takeIf { it.isNotBlank() }
                    ?: if (competitionActionState.isSettling) {
                        stringResource(Res.string.problem_details_action_settle_loading)
                    } else {
                        stringResource(Res.string.problem_details_action_cancel_loading)
                    },
                onCancel = onCancelCompetitionAction,
            )
        }

        gateState?.isJoining == true -> {
            ProblemDetailsProgressOverlay(
                message = gateState.joinStatusMessage?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.problem_details_editor_locked_join_action_loading),
                onCancel = onCancelJoin,
            )
        }

        gateState?.isRunning == true -> {
            ProblemDetailsProgressOverlay(
                message = stringResource(Res.string.problem_details_run_running),
                onCancel = onCancelRun,
            )
        }

        gateState?.isSubmitting == true || gateState?.isSubmitRequestInFlight == true -> {
            ProblemDetailsSubmitOverlay(
                isLoading = true,
                message = resolveProblemDetailsUiText(gateState.submitStatusMessage)?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.problem_details_submit_submitting),
                errorMessage = null,
                retryAllowed = false,
                retryInFlight = false,
                onCancel = onCancelSubmit,
                onDismiss = { },
                onRetry = { },
            )
        }

        !gateState?.submitErrorMessage.isNullOrBlank() -> {
            ProblemDetailsSubmitOverlay(
                isLoading = false,
                message = null,
                errorMessage = gateState.submitErrorMessage,
                retryAllowed = gateState.submitRetryAllowed,
                retryInFlight = false,
                onDismiss = onDismissSubmitPopup,
                onRetry = onRetrySubmit,
            )
        }
    }
}

@Composable
private fun ProblemStatementPane(
    modifier: Modifier,
    showScrollbar: Boolean,
    problem: ProblemSummary,
    statementContent: ProblemStatementContent,
    registeredParticipants: Int,
    competitionActionState: CompetitionLifecycleActionState,
    isLoggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onSettle: () -> Unit,
    onCancel: () -> Unit,
) {
    val detailsScroll = rememberScrollState()

    AppSurface(modifier = modifier) {
        ScrollablePane(
            scrollState = detailsScroll,
            showScrollbar = showScrollbar,
        ) { contentModifier ->
            Column(
                modifier = contentModifier,
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
                if (problem.referenceSolutionCode.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    PublicReferenceSolutionSection(problem = problem)
                }
                Spacer(Modifier.height(4.dp))
                DetailLine(stringResource(Res.string.problem_details_requirement_participants, problem.requiredParticipants))
                DetailLine(
                    stringResource(
                        Res.string.problem_details_requirement_prize,
                        formatAmountWithSymbol(problem.paymentAsset, problem.prizeAmountAtomic),
                    ),
                )
                DetailLine(
                    stringResource(
                        Res.string.problem_details_requirement_entry_fee,
                        formatAmountWithSymbol(problem.paymentAsset, problem.entryFeeAmountAtomic),
                    ),
                )
                DetailLine(
                    stringResource(
                        Res.string.problem_details_requirement_start,
                        stringResource(Res.string.days_count, problem.daysToStart),
                    ),
                )
                DetailLine(stringResource(Res.string.problem_details_requirement_join, problem.joinUntilLabel))
                DetailLine(stringResource(Res.string.problem_details_requirement_submit, problem.submitUntilLabel))
                CompetitionLifecycleActionSection(
                    problem = problem,
                    registeredParticipants = registeredParticipants,
                    uiState = competitionActionState,
                    isLoggedIn = isLoggedIn,
                    onRequireLogin = onRequireLogin,
                    onSettle = onSettle,
                    onCancel = onCancel,
                )
            }
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
        }
    }
}

@Composable
private fun CodeEditorPane(
    modifier: Modifier,
    showScrollbar: Boolean,
    code: String,
    onCodeChange: (String) -> Unit,
    fillEditorSpace: Boolean,
    overlayState: EditorOverlayState,
    isRunning: Boolean,
    runErrorMessage: String?,
    runResult: RunProblemResponseDto?,
    isSubmitting: Boolean,
    submitErrorMessage: String?,
    submitResult: SubmitProblemResponseDto?,
    submitInlineErrorMessage: String?,
    onRun: () -> Unit,
    onSubmit: () -> Unit,
    onJoinRequest: () -> Unit,
) {
    val editorBlocked = overlayState.show
    val paneScroll = rememberScrollState()
    val editorShape = RoundedCornerShape(8.dp)

    Box(modifier = modifier) {
        AppSurface(modifier = Modifier.fillMaxSize()) {
            ScrollablePane(
                scrollState = paneScroll,
                showScrollbar = showScrollbar,
            ) { contentModifier ->
                Column(
                    modifier = contentModifier,
                ) {
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
                        text = "${stringResource(Res.string.problem_details_editor_language)}: Kotlin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedTextField(
                            value = code,
                            onValueChange = onCodeChange,
                            modifier = editorModifier.clip(editorShape),
                            enabled = !editorBlocked,
                            shape = editorShape,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            minLines = 16,
                            maxLines = 50,
                        )
                        if (overlayState.show) {
                            LockedEditorOverlay(
                                modifier = Modifier.matchParentSize(),
                                shape = editorShape,
                                overlayState = overlayState,
                                onJoinRequest = onJoinRequest,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onRun,
                            enabled = !editorBlocked && !isRunning && !isSubmitting,
                        ) {
                            Text(stringResource(Res.string.problem_details_editor_run))
                        }
                        Button(
                            onClick = onSubmit,
                            enabled = !editorBlocked && !isRunning && !isSubmitting,
                        ) {
                            Text(stringResource(Res.string.problem_details_editor_submit))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!runErrorMessage.isNullOrBlank()) {
                        SelectionContainer {
                            Text(
                                text = stringResource(Res.string.problem_details_run_error_console_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        ErrorConsoleMessage(
                            message = runErrorMessage,
                            isError = true,
                        )
                    } else if (runResult != null) {
                        RunResultsPane(result = runResult)
                    } else if (submitResult != null) {
                        SubmitResultPane(
                            result = submitResult,
                            onchainErrorMessage = submitInlineErrorMessage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedEditorOverlay(
    modifier: Modifier,
    shape: RoundedCornerShape,
    overlayState: EditorOverlayState,
    onJoinRequest: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
    ) {
        OutlinedCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 8.dp,
                bottomEnd = 8.dp,
            ),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LockedEditorOverlayContent(
                    overlayState = overlayState,
                    onJoinRequest = onJoinRequest,
                )
            }
        }
    }
}

@Composable
private fun LockedEditorOverlayContent(
    overlayState: EditorOverlayState,
    onJoinRequest: () -> Unit,
) {
    when {
        overlayState.isLoadingMembership -> {
            CircularProgressIndicator()
            Text(
                text = stringResource(Res.string.problem_details_editor_locked_loading),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        overlayState.requiresJoin -> {
            Text(
                text = stringResource(Res.string.problem_details_editor_locked_join_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    Res.string.problem_details_editor_locked_join_body,
                    overlayState.registeredParticipants,
                    overlayState.requiredParticipants,
                ),
                modifier = Modifier.fillMaxWidth(),
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

        overlayState.competitionFull -> {
            Text(
                text = stringResource(Res.string.problem_details_editor_locked_full_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    Res.string.problem_details_editor_locked_full_body,
                    overlayState.registeredParticipants,
                    overlayState.requiredParticipants,
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        overlayState.waitingForStart -> {
            Text(
                text = stringResource(Res.string.problem_details_editor_locked_waiting_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    Res.string.problem_details_editor_locked_waiting_body,
                    overlayState.registeredParticipants,
                    overlayState.requiredParticipants,
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (!overlayState.joinErrorMessage.isNullOrBlank()) {
        Text(
            text = overlayState.joinErrorMessage,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ColumnScope.ScrollablePane(
    scrollState: ScrollState,
    showScrollbar: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    if (showScrollbar) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
        ) {
            content(
                Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp)
                    .verticalScroll(scrollState),
            )
            PaneScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }
    } else {
        content(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        )
    }
}

@Composable
private fun PaneScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    AppScrollbarTrack(
        modifier = modifier,
    )
    AppVerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
    )
}

@Composable
internal fun ProblemDetailsProgressOverlay(
    message: String,
    onCancel: () -> Unit,
) {
    val loadingDotsCount = rememberJoinOverlayLoadingDotsCount()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedCard(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 380.dp)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp).width(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = joinOverlayMessageWithDots(
                        message = message,
                        loadingDotsCount = loadingDotsCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(Res.string.operation_cancel))
                }
            }
        }
    }
}

@Composable
private fun ProblemDetailsSubmitOverlay(
    isLoading: Boolean,
    message: String?,
    errorMessage: String?,
    retryAllowed: Boolean,
    retryInFlight: Boolean,
    onCancel: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val loadingDotsCount = rememberJoinOverlayLoadingDotsCount()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedCard(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 420.dp)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp).width(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = joinOverlayMessageWithDots(
                            message = message.orEmpty(),
                            loadingDotsCount = loadingDotsCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (onCancel != null) {
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(Res.string.operation_cancel))
                        }
                    }
                } else {
                    ErrorConsoleMessage(
                        message = errorMessage.orEmpty(),
                        isError = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                        ) {
                            Text(stringResource(Res.string.problem_details_submit_popup_ok))
                        }
                        if (retryAllowed) {
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onRetry, enabled = !retryInFlight) {
                                Text(
                                    if (retryInFlight) {
                                        stringResource(Res.string.problem_details_submit_popup_retry_loading)
                                    } else {
                                        stringResource(Res.string.problem_details_submit_popup_retry)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberJoinOverlayLoadingDotsCount(): Int {
    var dotsCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            dotsCount = (dotsCount + 1) % 4
            delay(350)
        }
    }
    return dotsCount
}

@Composable
private fun joinOverlayMessageWithDots(
    message: String,
    loadingDotsCount: Int,
) = buildAnnotatedString {
    append(message.withoutTrailingEllipsis())
    append(" ")
    repeat(3) { index ->
        withStyle(
            SpanStyle(
                color = if (index < loadingDotsCount) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Color.Transparent
                },
            ),
        ) {
            append(".")
        }
    }
}

private fun String.withoutTrailingEllipsis(): String {
    return trimEnd().removeSuffix("...").trimEnd('.').trimEnd()
}

@Composable
private fun resolveProblemDetailsUiText(message: ProblemDetailsUiText?): String? {
    return when (message) {
        null -> null
        is ProblemDetailsUiText.Raw -> message.value
        is ProblemDetailsUiText.Resource -> stringResource(message.resource, *message.args.toTypedArray())
    }
}

@Composable
private fun SubmitResultPane(
    result: SubmitProblemResponseDto,
    onchainErrorMessage: String?,
) {
    val summaryMemoryUsedKb = result.memoryUsedKb ?: result.results.mapNotNull { it.memoryUsedKb }.maxOrNull()
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.problem_details_submit_result_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.problem_details_run_result_passed, result.passed, result.total),
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.allPassed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = stringResource(
                    Res.string.problem_details_submit_consensus,
                    result.consensusReached,
                    result.consensusRequired,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.problem_details_submit_runtime, result.runtimeMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summaryMemoryUsedKb?.let { memoryUsedKb ->
                Text(
                    text = "Memory: ${memoryUsedKb} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(Res.string.problem_details_submit_hash, result.commitmentHash),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.problem_details_submit_sandbox_result_hash, result.sandboxResultHash),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.sandboxImageHash?.takeIf { it.isNotBlank() }?.let { imageHash ->
                Text(
                    text = "Sandbox image hash: $imageHash",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    Res.string.problem_details_submit_proxy_address,
                    result.proxyAddress,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.txHash.takeIf { it.isNotBlank() }?.let { txHash ->
                Text(
                    text = stringResource(Res.string.problem_details_submit_tx_hash, txHash),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.explorerUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Text(
                    text = stringResource(Res.string.problem_details_submit_explorer, url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    onchainErrorMessage?.takeIf { it.isNotBlank() }?.let { message ->
        Spacer(Modifier.height(6.dp))
        SelectionContainer {
            Text(
                text = stringResource(Res.string.problem_details_submit_onchain_error_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(4.dp))
        ErrorConsoleMessage(
            message = message,
            isError = true,
        )
    }
}

@Composable
private fun RunResultsPane(result: RunProblemResponseDto) {
    val summaryMemoryUsedKb = result.memoryUsedKb ?: result.results.mapNotNull { it.memoryUsedKb }.maxOrNull()
    SelectionContainer {
        Column {
            Text(
                text = stringResource(Res.string.problem_details_run_result_passed, result.passed, result.total),
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.allPassed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Runtime: ${result.runtimeMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summaryMemoryUsedKb?.let { memoryUsedKb ->
                Text(
                    text = "Memory: ${memoryUsedKb} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        result.results.forEach { testResult ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.problem_details_run_result_test_label, testResult.index),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = runStatusLabel(testResult),
                            style = MaterialTheme.typography.bodySmall,
                            color = runStatusColor(testResult),
                        )
                        Text(
                            text = stringResource(
                                Res.string.problem_details_run_result_time,
                                testResult.executionTimeMs,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        testResult.memoryUsedKb?.let { memoryUsedKb ->
                            Text(
                                text = "Memory: ${memoryUsedKb} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (testResult.hidden && !testResult.passed && testResult.message.isNullOrBlank()) {
                            Text(
                                text = stringResource(Res.string.problem_details_run_result_hidden_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!testResult.hidden) {
                            testResult.expectedOutput?.takeIf { it.isNotBlank() }?.let { expected ->
                                Text(
                                    text = stringResource(Res.string.problem_details_run_result_expected, expected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            testResult.actualOutput?.let { actual ->
                                Text(
                                    text = stringResource(Res.string.problem_details_run_result_actual, actual),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                testResult.message?.takeIf { it.isNotBlank() }?.let { message ->
                    val status = testResult.status.uppercase()
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (testResult.hidden && status == "FAILED") {
                            SelectionContainer {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            ErrorConsoleMessage(
                                message = message,
                                isError = !testResult.passed,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorConsoleMessage(
    message: String,
    isError: Boolean,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(verticalScrollState),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState),
                ) {
                    SelectionContainer {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun runStatusLabel(testResult: RunProblemTestResultDto): String {
    return when (testResult.status.uppercase()) {
        "PASSED" -> stringResource(Res.string.problem_details_run_result_status_passed)
        "FAILED" -> stringResource(Res.string.problem_details_run_result_status_failed)
        "TIMEOUT" -> stringResource(Res.string.problem_details_run_result_status_timeout)
        else -> stringResource(Res.string.problem_details_run_result_status_error)
    }
}

@Composable
private fun runStatusColor(testResult: RunProblemTestResultDto): androidx.compose.ui.graphics.Color {
    return when (testResult.status.uppercase()) {
        "PASSED" -> MaterialTheme.colorScheme.primary
        "FAILED" -> MaterialTheme.colorScheme.error
        "TIMEOUT" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.error
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

private fun defaultSolutionTemplate(): String {
    return """
        fun solve(input: String): String {
            return input
        }
    """.trimIndent()
}


private data class EditorOverlayState(
    val show: Boolean,
    val isLoadingMembership: Boolean,
    val requiresJoin: Boolean,
    val competitionFull: Boolean,
    val waitingForStart: Boolean,
    val isJoining: Boolean,
    val joinStatusMessage: String?,
    val joinErrorMessage: String?,
    val registeredParticipants: Int,
    val requiredParticipants: Int,
)
