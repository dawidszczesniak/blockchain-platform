package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
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
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

private const val MAX_TESTS = 10

private data class TestDraft(
    val id: Int,
    val code: String,
    val expanded: Boolean,
)

@Composable
fun CreateProblemScreen() {
    var prize by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf("") }
    var entryFee by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var joinUntilDate by remember { mutableStateOf("") }
    var submitUntilDate by remember { mutableStateOf("") }

    val tests = remember { mutableStateListOf(TestDraft(id = 1, code = "", expanded = true)) }
    var nextTestId by remember { mutableStateOf(2) }

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
                        prize = prize,
                        onPrizeChange = { prize = it },
                        participants = participants,
                        onParticipantsChange = { participants = it },
                        entryFee = entryFee,
                        onEntryFeeChange = { entryFee = it },
                        description = description,
                        onDescriptionChange = { description = it },
                        tests = tests,
                        onAddTest = { tests.add(TestDraft(id = nextTestId++, code = "", expanded = true)) },
                        onToggleTest = { index ->
                            val test = tests[index]
                            tests[index] = test.copy(expanded = !test.expanded)
                        },
                        onRemoveTest = { index ->
                            if (tests.size > 1) {
                                tests.removeAt(index)
                            }
                        },
                        onTestCodeChange = { index, value ->
                            val test = tests[index]
                            tests[index] = test.copy(code = value)
                        },
                        joinUntilDate = joinUntilDate,
                        onJoinUntilChange = { joinUntilDate = it },
                        submitUntilDate = submitUntilDate,
                        onSubmitUntilChange = { submitUntilDate = it }
                    )
                    ProfitPanel(
                        prize = prize,
                        participants = participants,
                        entryFee = entryFee
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    CreateProblemForm(
                        modifier = Modifier.weight(2f),
                        prize = prize,
                        onPrizeChange = { prize = it },
                        participants = participants,
                        onParticipantsChange = { participants = it },
                        entryFee = entryFee,
                        onEntryFeeChange = { entryFee = it },
                        description = description,
                        onDescriptionChange = { description = it },
                        tests = tests,
                        onAddTest = { tests.add(TestDraft(id = nextTestId++, code = "", expanded = true)) },
                        onToggleTest = { index ->
                            val test = tests[index]
                            tests[index] = test.copy(expanded = !test.expanded)
                        },
                        onRemoveTest = { index ->
                            if (tests.size > 1) {
                                tests.removeAt(index)
                            }
                        },
                        onTestCodeChange = { index, value ->
                            val test = tests[index]
                            tests[index] = test.copy(code = value)
                        },
                        joinUntilDate = joinUntilDate,
                        onJoinUntilChange = { joinUntilDate = it },
                        submitUntilDate = submitUntilDate,
                        onSubmitUntilChange = { submitUntilDate = it }
                    )
                    ProfitPanel(
                        modifier = Modifier.weight(1f),
                        prize = prize,
                        participants = participants,
                        entryFee = entryFee
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProblemForm(
    modifier: Modifier = Modifier,
    prize: String,
    onPrizeChange: (String) -> Unit,
    participants: String,
    onParticipantsChange: (String) -> Unit,
    entryFee: String,
    onEntryFeeChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    tests: List<TestDraft>,
    onAddTest: () -> Unit,
    onToggleTest: (Int) -> Unit,
    onRemoveTest: (Int) -> Unit,
    onTestCodeChange: (Int, String) -> Unit,
    joinUntilDate: String,
    onJoinUntilChange: (String) -> Unit,
    submitUntilDate: String,
    onSubmitUntilChange: (String) -> Unit,
) {
    AppSurface(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.create_problem_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = prize,
            onValueChange = onPrizeChange,
            label = { Text(stringResource(Res.string.create_problem_prize_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = participants,
            onValueChange = onParticipantsChange,
            label = { Text(stringResource(Res.string.create_problem_participants_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = entryFee,
            onValueChange = onEntryFeeChange,
            label = { Text(stringResource(Res.string.create_problem_entry_fee_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = description,
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
                    tests.size,
                    MAX_TESTS
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { if (tests.size < MAX_TESTS) onAddTest() },
                enabled = tests.size < MAX_TESTS
            ) {
                Text(stringResource(Res.string.create_problem_add_test))
            }
        }
        Spacer(Modifier.height(8.dp))

        tests.forEachIndexed { index, test ->
            key(test.id) {
                TestCaseCard(
                    index = index,
                    test = test,
                    onToggle = { onToggleTest(index) },
                    canRemove = tests.size > 1,
                    onRemove = { onRemoveTest(index) },
                    onCodeChange = { value -> onTestCodeChange(index, value) }
                )
            }
            if (index < tests.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = joinUntilDate,
            onValueChange = onJoinUntilChange,
            label = { Text(stringResource(Res.string.create_problem_join_until_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = submitUntilDate,
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
    prize: String,
    participants: String,
    entryFee: String,
) {
    val prizeValue = parseAmount(prize)
    val entryFeeValue = parseAmount(entryFee)
    val participantsValue = participants.trim().toIntOrNull() ?: 0

    val gross = entryFeeValue * participantsValue
    val platformFee = gross * 0.02
    val net = gross - prizeValue - platformFee
    val netColor = when {
        net > 0 -> Color(0xFF33C97A)
        net < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    AppSurface(modifier = modifier.fillMaxWidth()) {
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
            value = formatAmount(prizeValue)
        )
        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_entry_fee),
            value = formatAmount(entryFeeValue)
        )
        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_participants),
            value = participantsValue.toString()
        )
        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_platform_fee),
            value = formatAmount(platformFee)
        )
        Spacer(Modifier.height(8.dp))
        ProfitRow(
            label = stringResource(Res.string.create_problem_profit_net),
            value = formatAmount(net),
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
    valueColor: androidx.compose.ui.graphics.Color? = null
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

private fun parseAmount(value: String): Double {
    val normalized = value.replace(",", ".").trim()
    return normalized.toDoubleOrNull() ?: 0.0
}

private fun formatAmount(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val asLong = rounded.toLong().toDouble()
    return if (kotlin.math.abs(rounded - asLong) < 0.0001) {
        asLong.toLong().toString()
    } else {
        rounded.toString()
    }
}

@Composable
private fun TestCaseCard(
    index: Int,
    test: TestDraft,
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
