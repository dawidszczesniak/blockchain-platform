package pl.dawidszczesniak.blockchain_platform.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.create_problem_action_placeholder
import blockchain_platform.composeapp.generated.resources.create_problem_add_test
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
import blockchain_platform.composeapp.generated.resources.create_problem_submit_until_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_code_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_collapse
import blockchain_platform.composeapp.generated.resources.create_problem_test_expand
import blockchain_platform.composeapp.generated.resources.create_problem_test_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_remove
import blockchain_platform.composeapp.generated.resources.create_problem_tests_title
import blockchain_platform.composeapp.generated.resources.create_problem_title
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.presentation.create.CreateProblemIntent
import pl.dawidszczesniak.blockchain_platform.presentation.create.CreateProblemState
import pl.dawidszczesniak.blockchain_platform.presentation.create.CreateProblemTest
import pl.dawidszczesniak.blockchain_platform.presentation.create.CreateProblemViewModel
import pl.dawidszczesniak.blockchain_platform.presentation.create.MAX_CREATE_PROBLEM_TESTS
import pl.dawidszczesniak.blockchain_platform.presentation.create.formatAmount

@Composable
fun CreateProblemScreen() {
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<CreateProblemViewModel>() }
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
                        onPrizeChange = {
                            viewModel.onIntent(CreateProblemIntent.PrizeChanged(it))
                        },
                        onParticipantsChange = {
                            viewModel.onIntent(CreateProblemIntent.ParticipantsChanged(it))
                        },
                        onEntryFeeChange = {
                            viewModel.onIntent(CreateProblemIntent.EntryFeeChanged(it))
                        },
                        onDescriptionChange = {
                            viewModel.onIntent(CreateProblemIntent.DescriptionChanged(it))
                        },
                        onAddTest = {
                            viewModel.onIntent(CreateProblemIntent.AddTest)
                        },
                        onToggleTest = { id ->
                            viewModel.onIntent(CreateProblemIntent.ToggleTest(id))
                        },
                        onRemoveTest = { id ->
                            viewModel.onIntent(CreateProblemIntent.RemoveTest(id))
                        },
                        onTestCodeChange = { id, value ->
                            viewModel.onIntent(CreateProblemIntent.TestCodeChanged(id, value))
                        },
                        onJoinUntilChange = {
                            viewModel.onIntent(CreateProblemIntent.JoinUntilChanged(it))
                        },
                        onSubmitUntilChange = {
                            viewModel.onIntent(CreateProblemIntent.SubmitUntilChanged(it))
                        }
                    )
                    ProfitPanel(state = state)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    CreateProblemForm(
                        modifier = Modifier.weight(2f),
                        state = state,
                        onPrizeChange = {
                            viewModel.onIntent(CreateProblemIntent.PrizeChanged(it))
                        },
                        onParticipantsChange = {
                            viewModel.onIntent(CreateProblemIntent.ParticipantsChanged(it))
                        },
                        onEntryFeeChange = {
                            viewModel.onIntent(CreateProblemIntent.EntryFeeChanged(it))
                        },
                        onDescriptionChange = {
                            viewModel.onIntent(CreateProblemIntent.DescriptionChanged(it))
                        },
                        onAddTest = {
                            viewModel.onIntent(CreateProblemIntent.AddTest)
                        },
                        onToggleTest = { id ->
                            viewModel.onIntent(CreateProblemIntent.ToggleTest(id))
                        },
                        onRemoveTest = { id ->
                            viewModel.onIntent(CreateProblemIntent.RemoveTest(id))
                        },
                        onTestCodeChange = { id, value ->
                            viewModel.onIntent(CreateProblemIntent.TestCodeChanged(id, value))
                        },
                        onJoinUntilChange = {
                            viewModel.onIntent(CreateProblemIntent.JoinUntilChanged(it))
                        },
                        onSubmitUntilChange = {
                            viewModel.onIntent(CreateProblemIntent.SubmitUntilChanged(it))
                        }
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
    onDescriptionChange: (String) -> Unit,
    onAddTest: () -> Unit,
    onToggleTest: (Int) -> Unit,
    onRemoveTest: (Int) -> Unit,
    onTestCodeChange: (Int, String) -> Unit,
    onJoinUntilChange: (String) -> Unit,
    onSubmitUntilChange: (String) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.create_problem_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.prize,
            onValueChange = onPrizeChange,
            label = { Text(stringResource(Res.string.create_problem_prize_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.participants,
            onValueChange = onParticipantsChange,
            label = { Text(stringResource(Res.string.create_problem_participants_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.entryFee,
            onValueChange = onEntryFeeChange,
            label = { Text(stringResource(Res.string.create_problem_entry_fee_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(Res.string.create_problem_description_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8
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
                onClick = onAddTest,
                enabled = state.canAddTest
            ) {
                Text(stringResource(Res.string.create_problem_add_test))
            }
        }
        Spacer(Modifier.height(8.dp))

        state.tests.forEachIndexed { index, test ->
            key(test.id) {
                TestCaseCard(
                    index = index,
                    test = test,
                    onToggle = { onToggleTest(test.id) },
                    canRemove = state.tests.size > 1,
                    onRemove = { onRemoveTest(test.id) },
                    onCodeChange = { value -> onTestCodeChange(test.id, value) }
                )
            }
            if (index < state.tests.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.joinUntilDate,
            onValueChange = onJoinUntilChange,
            label = { Text(stringResource(Res.string.create_problem_join_until_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = state.submitUntilDate,
            onValueChange = onSubmitUntilChange,
            label = { Text(stringResource(Res.string.create_problem_submit_until_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { /* TODO(backend): call create-problem API */ },
            enabled = false
        ) {
            Text(stringResource(Res.string.create_problem_action_placeholder))
        }
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
    onCodeChange: (String) -> Unit,
) {
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
                    value = test.code,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(Res.string.create_problem_test_code_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10
                )
            }
        }
    }
}
