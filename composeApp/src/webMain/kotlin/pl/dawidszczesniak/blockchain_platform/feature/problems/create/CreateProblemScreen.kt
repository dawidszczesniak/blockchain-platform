package pl.dawidszczesniak.blockchain_platform.feature.problems.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.create_problem_action_create
import blockchain_platform.composeapp.generated.resources.create_problem_action_creating
import blockchain_platform.composeapp.generated.resources.create_problem_add_test
import blockchain_platform.composeapp.generated.resources.create_problem_constraints_label
import blockchain_platform.composeapp.generated.resources.create_problem_copy_error
import blockchain_platform.composeapp.generated.resources.create_problem_date_picker_confirm
import blockchain_platform.composeapp.generated.resources.create_problem_date_picker_dismiss
import blockchain_platform.composeapp.generated.resources.create_problem_description_label
import blockchain_platform.composeapp.generated.resources.create_problem_entry_fee_label
import blockchain_platform.composeapp.generated.resources.create_problem_join_until_label
import blockchain_platform.composeapp.generated.resources.create_problem_participants_label
import blockchain_platform.composeapp.generated.resources.create_problem_profit_entry_fee
import blockchain_platform.composeapp.generated.resources.create_problem_profit_net
import blockchain_platform.composeapp.generated.resources.create_problem_profit_note
import blockchain_platform.composeapp.generated.resources.create_problem_profit_participants
import blockchain_platform.composeapp.generated.resources.create_problem_profit_platform_fee
import blockchain_platform.composeapp.generated.resources.create_problem_profit_prize
import blockchain_platform.composeapp.generated.resources.create_problem_profit_title
import blockchain_platform.composeapp.generated.resources.create_problem_prize_label
import blockchain_platform.composeapp.generated.resources.create_problem_reference_solution_label
import blockchain_platform.composeapp.generated.resources.create_problem_run_all
import blockchain_platform.composeapp.generated.resources.create_problem_run_output
import blockchain_platform.composeapp.generated.resources.create_problem_run_running
import blockchain_platform.composeapp.generated.resources.create_problem_run_status
import blockchain_platform.composeapp.generated.resources.create_problem_run_test
import blockchain_platform.composeapp.generated.resources.create_problem_submit_failed
import blockchain_platform.composeapp.generated.resources.create_problem_submit_success
import blockchain_platform.composeapp.generated.resources.create_problem_submit_until_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_collapse
import blockchain_platform.composeapp.generated.resources.create_problem_test_expand
import blockchain_platform.composeapp.generated.resources.create_problem_test_hidden_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_input_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_remove
import blockchain_platform.composeapp.generated.resources.create_problem_tests_title
import blockchain_platform.composeapp.generated.resources.create_problem_title_label
import blockchain_platform.composeapp.generated.resources.create_problem_validation_date_order
import blockchain_platform.composeapp.generated.resources.create_problem_validation_integer
import blockchain_platform.composeapp.generated.resources.create_problem_validation_min_public_tests
import blockchain_platform.composeapp.generated.resources.create_problem_validation_non_negative
import blockchain_platform.composeapp.generated.resources.create_problem_validation_positive
import blockchain_platform.composeapp.generated.resources.create_problem_validation_required
import blockchain_platform.composeapp.generated.resources.create_problem_validation_run_required
import blockchain_platform.composeapp.generated.resources.create_problem_validation_ready
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.platform.copyTextToClipboard

@Composable
fun CreateProblemScreen() {
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<CreateProblemViewModel>() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 980.dp

            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CreateProblemForm(
                        state = state,
                        onPrizeChange = { viewModel.onIntent(CreateProblemIntent.PrizeChanged(it)) },
                        onParticipantsChange = { viewModel.onIntent(CreateProblemIntent.ParticipantsChanged(it)) },
                        onEntryFeeChange = { viewModel.onIntent(CreateProblemIntent.EntryFeeChanged(it)) },
                        onTitleChange = { viewModel.onIntent(CreateProblemIntent.TitleChanged(it)) },
                        onDescriptionChange = { viewModel.onIntent(CreateProblemIntent.DescriptionChanged(it)) },
                        onConstraintsChange = { viewModel.onIntent(CreateProblemIntent.ConstraintsChanged(it)) },
                        onReferenceSolutionChange = { viewModel.onIntent(CreateProblemIntent.ReferenceSolutionChanged(it)) },
                        onAddTest = { viewModel.onIntent(CreateProblemIntent.AddTest) },
                        onToggleTest = { id -> viewModel.onIntent(CreateProblemIntent.ToggleTest(id)) },
                        onRemoveTest = { id -> viewModel.onIntent(CreateProblemIntent.RemoveTest(id)) },
                        onTestInputChange = { id, value ->
                            viewModel.onIntent(CreateProblemIntent.TestInputChanged(id, value))
                        },
                        onTestHiddenChange = { id, value ->
                            viewModel.onIntent(CreateProblemIntent.TestHiddenChanged(id, value))
                        },
                        onRunSingleTest = { id -> viewModel.onIntent(CreateProblemIntent.RunSingleTest(id)) },
                        onRunAllTests = { viewModel.onIntent(CreateProblemIntent.RunAllTests) },
                        onJoinUntilChange = { viewModel.onIntent(CreateProblemIntent.JoinUntilChanged(it)) },
                        onSubmitUntilChange = { viewModel.onIntent(CreateProblemIntent.SubmitUntilChanged(it)) },
                        onSubmit = { viewModel.onIntent(CreateProblemIntent.Submit) },
                    )
                    ProfitPanel(state = state)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    CreateProblemForm(
                        modifier = Modifier.weight(2f),
                        state = state,
                        onPrizeChange = { viewModel.onIntent(CreateProblemIntent.PrizeChanged(it)) },
                        onParticipantsChange = { viewModel.onIntent(CreateProblemIntent.ParticipantsChanged(it)) },
                        onEntryFeeChange = { viewModel.onIntent(CreateProblemIntent.EntryFeeChanged(it)) },
                        onTitleChange = { viewModel.onIntent(CreateProblemIntent.TitleChanged(it)) },
                        onDescriptionChange = { viewModel.onIntent(CreateProblemIntent.DescriptionChanged(it)) },
                        onConstraintsChange = { viewModel.onIntent(CreateProblemIntent.ConstraintsChanged(it)) },
                        onReferenceSolutionChange = { viewModel.onIntent(CreateProblemIntent.ReferenceSolutionChanged(it)) },
                        onAddTest = { viewModel.onIntent(CreateProblemIntent.AddTest) },
                        onToggleTest = { id -> viewModel.onIntent(CreateProblemIntent.ToggleTest(id)) },
                        onRemoveTest = { id -> viewModel.onIntent(CreateProblemIntent.RemoveTest(id)) },
                        onTestInputChange = { id, value ->
                            viewModel.onIntent(CreateProblemIntent.TestInputChanged(id, value))
                        },
                        onTestHiddenChange = { id, value ->
                            viewModel.onIntent(CreateProblemIntent.TestHiddenChanged(id, value))
                        },
                        onRunSingleTest = { id -> viewModel.onIntent(CreateProblemIntent.RunSingleTest(id)) },
                        onRunAllTests = { viewModel.onIntent(CreateProblemIntent.RunAllTests) },
                        onJoinUntilChange = { viewModel.onIntent(CreateProblemIntent.JoinUntilChanged(it)) },
                        onSubmitUntilChange = { viewModel.onIntent(CreateProblemIntent.SubmitUntilChanged(it)) },
                        onSubmit = { viewModel.onIntent(CreateProblemIntent.Submit) },
                    )
                    ProfitPanel(
                        modifier = Modifier.weight(1f),
                        state = state
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProblemForm(
    modifier: Modifier = Modifier,
    state: CreateProblemState,
    onPrizeChange: (String) -> Unit,
    onParticipantsChange: (String) -> Unit,
    onEntryFeeChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConstraintsChange: (String) -> Unit,
    onReferenceSolutionChange: (String) -> Unit,
    onAddTest: () -> Unit,
    onToggleTest: (Int) -> Unit,
    onRemoveTest: (Int) -> Unit,
    onTestInputChange: (Int, String) -> Unit,
    onTestHiddenChange: (Int, Boolean) -> Unit,
    onRunSingleTest: (Int) -> Unit,
    onRunAllTests: () -> Unit,
    onJoinUntilChange: (LocalDate) -> Unit,
    onSubmitUntilChange: (LocalDate) -> Unit,
    onSubmit: () -> Unit,
) {
    val validation = state.validation
    val prizeError = validationMessage(
        visible = state.submitAttempted,
        error = validation.prize,
    )
    val participantsError = validationMessage(
        visible = state.submitAttempted,
        error = validation.participants,
    )
    val entryFeeError = validationMessage(
        visible = state.submitAttempted,
        error = validation.entryFee,
    )
    val titleError = validationMessage(
        visible = state.submitAttempted,
        error = validation.title,
    )
    val descriptionError = validationMessage(
        visible = state.submitAttempted,
        error = validation.description,
    )
    val referenceSolutionError = validationMessage(
        visible = state.submitAttempted,
        error = validation.referenceSolution,
    )
    val joinUntilError = validationMessage(
        visible = state.submitAttempted,
        error = validation.joinUntilDate,
    )
    val submitUntilError = validationMessage(
        visible = state.submitAttempted,
        error = validation.submitUntilDate,
    )
    val publicTestsError = validationMessage(
        visible = state.submitAttempted,
        error = validation.publicTests,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(Res.string.create_problem_title_label)) },
            modifier = Modifier.fillMaxWidth(),
            isError = titleError != null,
            supportingText = {
                if (titleError != null) {
                    Text(titleError)
                }
            },
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(Res.string.create_problem_description_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
            isError = descriptionError != null,
            supportingText = {
                if (descriptionError != null) {
                    Text(descriptionError)
                }
            },
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = state.prize,
                onValueChange = onPrizeChange,
                label = { Text(stringResource(Res.string.create_problem_prize_label)) },
                modifier = Modifier.weight(1f),
                isError = prizeError != null,
                supportingText = {
                    if (prizeError != null) {
                        Text(prizeError)
                    }
                },
            )
            OutlinedTextField(
                value = state.participants,
                onValueChange = onParticipantsChange,
                label = { Text(stringResource(Res.string.create_problem_participants_label)) },
                modifier = Modifier.weight(1f),
                isError = participantsError != null,
                supportingText = {
                    if (participantsError != null) {
                        Text(participantsError)
                    }
                },
            )
            OutlinedTextField(
                value = state.entryFee,
                onValueChange = onEntryFeeChange,
                label = { Text(stringResource(Res.string.create_problem_entry_fee_label)) },
                modifier = Modifier.weight(1f),
                isError = entryFeeError != null,
                supportingText = {
                    if (entryFeeError != null) {
                        Text(entryFeeError)
                    }
                },
            )
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.constraints,
            onValueChange = onConstraintsChange,
            label = { Text(stringResource(Res.string.create_problem_constraints_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6,
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.referenceSolutionCode,
            onValueChange = onReferenceSolutionChange,
            label = { Text(stringResource(Res.string.create_problem_reference_solution_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            maxLines = 14,
            isError = referenceSolutionError != null,
            supportingText = {
                if (referenceSolutionError != null) {
                    Text(referenceSolutionError)
                }
            },
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    Res.string.create_problem_tests_title,
                    state.tests.size,
                    MAX_CREATE_PROBLEM_TESTS
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onRunAllTests,
                enabled = !state.isRunningAllTests && state.runningTestIds.isEmpty() && !state.isSubmitting,
            ) {
                Text(
                    stringResource(
                        if (state.isRunningAllTests) {
                            Res.string.create_problem_run_running
                        } else {
                            Res.string.create_problem_run_all
                        }
                    )
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAddTest,
                enabled = state.canAddTest
            ) {
                Text(stringResource(Res.string.create_problem_add_test))
            }
        }
        if (publicTestsError != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = publicTestsError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (state.isValidationFresh) {
                    Res.string.create_problem_validation_ready
                } else {
                    Res.string.create_problem_validation_run_required
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.isValidationFresh) Color(0xFF33C97A) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.runErrorMessage?.let { runErrorMessage ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = runErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(2.dp))
            TextButton(onClick = { copyToClipboard(runErrorMessage) }) {
                Text(stringResource(Res.string.create_problem_copy_error))
            }
        }
        Spacer(Modifier.height(8.dp))

        state.tests.forEachIndexed { index, test ->
            key(test.id) {
                TestCaseCard(
                    index = index,
                    test = test,
                    onToggle = { onToggleTest(test.id) },
                    canRemove = state.tests.size > MIN_CREATE_PROBLEM_TESTS,
                    onRemove = { onRemoveTest(test.id) },
                    onInputChange = { value -> onTestInputChange(test.id, value) },
                    onHiddenChange = { value -> onTestHiddenChange(test.id, value) },
                    onRun = { onRunSingleTest(test.id) },
                    isRunning = state.isRunningAllTests || test.id in state.runningTestIds,
                    runResult = state.testRunResultsById[test.id],
                    validation = if (state.submitAttempted) {
                        validation.testsById[test.id]
                    } else {
                        null
                    }
                )
            }
            if (index < state.tests.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        DatePickerField(
            value = state.joinUntilDate,
            label = stringResource(Res.string.create_problem_join_until_label),
            onValueChange = onJoinUntilChange,
            errorText = joinUntilError,
        )
        Spacer(Modifier.height(10.dp))

        DatePickerField(
            value = state.submitUntilDate,
            label = stringResource(Res.string.create_problem_submit_until_label),
            onValueChange = onSubmitUntilChange,
            errorText = submitUntilError,
        )
        if (state.submitFailed) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (state.requiresFreshValidationForSubmit) {
                    stringResource(Res.string.create_problem_validation_run_required)
                } else {
                    state.submitErrorMessage ?: stringResource(Res.string.create_problem_submit_failed)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.submitSuccessProblemId != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.create_problem_submit_success),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF33C97A),
            )
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit
        ) {
            Text(
                stringResource(
                    if (state.isSubmitting) {
                        Res.string.create_problem_action_creating
                    } else {
                        Res.string.create_problem_action_create
                    }
                )
            )
        }
    }
}

@Composable
private fun validationMessage(
    visible: Boolean,
    error: CreateProblemValidationError?,
): String? {
    if (!visible || error == null) {
        return null
    }
    return stringResource(
        when (error) {
            CreateProblemValidationError.Required -> Res.string.create_problem_validation_required
            CreateProblemValidationError.InvalidInteger -> Res.string.create_problem_validation_integer
            CreateProblemValidationError.MustBePositive -> Res.string.create_problem_validation_positive
            CreateProblemValidationError.MustBeNonNegative -> Res.string.create_problem_validation_non_negative
            CreateProblemValidationError.SubmitBeforeJoin -> Res.string.create_problem_validation_date_order
            CreateProblemValidationError.MinPublicTests -> Res.string.create_problem_validation_min_public_tests
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: LocalDate?,
    label: String,
    onValueChange: (LocalDate) -> Unit,
    errorText: String?,
) {
    var isDialogVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = {},
        label = { Text(text = label) },
        readOnly = true,
        isError = errorText != null,
        supportingText = {
            if (errorText != null) {
                Text(errorText)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused) {
                    isDialogVisible = true
                    focusManager.clearFocus()
                }
            },
    )

    if (isDialogVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { isDialogVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis
                            ?.let(::utcMillisToLocalDate)
                            ?.let(onValueChange)
                        isDialogVisible = false
                    }
                ) {
                    Text(text = stringResource(Res.string.create_problem_date_picker_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogVisible = false }) {
                    Text(text = stringResource(Res.string.create_problem_date_picker_dismiss))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun utcMillisToLocalDate(millis: Long): LocalDate {
    return Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date
}

private fun copyToClipboard(value: String) {
    runCatching<Unit> {
        copyTextToClipboard(value)
        Unit
    }
}

@Composable
private fun ProfitPanel(
    modifier: Modifier = Modifier,
    state: CreateProblemState,
) {
    val netColor = when {
        state.netRevenue > 0 -> Color(0xFF33C97A)
        state.netRevenue < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.create_problem_profit_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.create_problem_profit_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_prize),
            value = formatAmount(state.prizeValue)
        )
        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_entry_fee),
            value = formatAmount(state.entryFeeValue)
        )
        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_participants),
            value = state.participantsValue.toString()
        )
        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_platform_fee),
            value = formatAmount(state.platformFee)
        )
        Spacer(Modifier.height(8.dp))
        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_net),
            value = formatAmount(state.netRevenue),
            highlight = true,
            valueColor = netColor
        )
    }
}

@Composable
private fun ProfitRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    valueColor: Color? = null
) {
    val resolvedColor = valueColor ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = resolvedColor
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TestCaseCard(
    index: Int,
    test: CreateProblemTest,
    onToggle: () -> Unit,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onInputChange: (String) -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    onRun: () -> Unit,
    isRunning: Boolean,
    runResult: CreateProblemTestRunResult?,
    validation: CreateProblemTestValidation?,
) {
    val inputErrorMessage = validationMessage(
        visible = true,
        error = validation?.input,
    )

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.create_problem_test_label, index + 1),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onRun,
                    enabled = !isRunning,
                ) {
                    Text(
                        stringResource(
                            if (isRunning) {
                                Res.string.create_problem_run_running
                            } else {
                                Res.string.create_problem_run_test
                            }
                        )
                    )
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onRemove, enabled = canRemove) {
                    Text(stringResource(Res.string.create_problem_test_remove))
                }
                TextButton(onClick = onToggle) {
                    Text(
                        stringResource(
                            if (test.expanded) {
                                Res.string.create_problem_test_collapse
                            } else {
                                Res.string.create_problem_test_expand
                            }
                        )
                    )
                }
            }

            if (test.expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = test.input,
                    onValueChange = onInputChange,
                    label = { Text(stringResource(Res.string.create_problem_test_input_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 8,
                    isError = inputErrorMessage != null,
                    supportingText = {
                        if (inputErrorMessage != null) {
                            Text(inputErrorMessage)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.create_problem_test_hidden_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = test.isHidden,
                        onCheckedChange = onHiddenChange,
                    )
                }
                runResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            Res.string.create_problem_run_status,
                            result.status,
                            result.executionTimeMs,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (result.status.uppercase()) {
                            "OK" -> Color(0xFF33C97A)
                            "TIMEOUT", "ERROR" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    result.output?.let { output ->
                        if (output.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.create_problem_run_output, output),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    result.message?.let { message ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(2.dp))
                        TextButton(onClick = { copyToClipboard(message) }) {
                            Text(stringResource(Res.string.create_problem_copy_error))
                        }
                    }
                }
            }
        }
    }
}
