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
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.create_problem_action_placeholder
import blockchain_platform.composeapp.generated.resources.create_problem_add_test
import blockchain_platform.composeapp.generated.resources.create_problem_description_label
import blockchain_platform.composeapp.generated.resources.create_problem_entry_fee_label
import blockchain_platform.composeapp.generated.resources.create_problem_join_until_label
import blockchain_platform.composeapp.generated.resources.create_problem_participants_label
import blockchain_platform.composeapp.generated.resources.create_problem_prize_label
import blockchain_platform.composeapp.generated.resources.create_problem_submit_until_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_code_label
import blockchain_platform.composeapp.generated.resources.create_problem_test_collapse
import blockchain_platform.composeapp.generated.resources.create_problem_test_expand
import blockchain_platform.composeapp.generated.resources.create_problem_test_label
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
        AppSurface(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.create_problem_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = prize,
                onValueChange = { prize = it },
                label = { Text(stringResource(Res.string.create_problem_prize_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = participants,
                onValueChange = { participants = it },
                label = { Text(stringResource(Res.string.create_problem_participants_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = entryFee,
                onValueChange = { entryFee = it },
                label = { Text(stringResource(Res.string.create_problem_entry_fee_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
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
                    onClick = {
                        if (tests.size < MAX_TESTS) {
                            tests.add(TestDraft(id = nextTestId++, code = "", expanded = true))
                        }
                    },
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
                        onToggle = {
                            tests[index] = test.copy(expanded = !test.expanded)
                        },
                        onCodeChange = { value ->
                            tests[index] = test.copy(code = value)
                        }
                    )
                }
                if (index < tests.lastIndex) {
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = joinUntilDate,
                onValueChange = { joinUntilDate = it },
                label = { Text(stringResource(Res.string.create_problem_join_until_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = submitUntilDate,
                onValueChange = { submitUntilDate = it },
                label = { Text(stringResource(Res.string.create_problem_submit_until_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { /* TODO: call backend */ },
                enabled = false
            ) {
                Text(stringResource(Res.string.create_problem_action_placeholder))
            }
        }
    }
}

@Composable
private fun TestCaseCard(
    index: Int,
    test: TestDraft,
    onToggle: () -> Unit,
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
