package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.create_problem_action_placeholder
import blockchain_platform.composeapp.generated.resources.create_problem_entry_fee_label
import blockchain_platform.composeapp.generated.resources.create_problem_join_until_label
import blockchain_platform.composeapp.generated.resources.create_problem_participants_label
import blockchain_platform.composeapp.generated.resources.create_problem_prize_label
import blockchain_platform.composeapp.generated.resources.create_problem_submit_until_label
import blockchain_platform.composeapp.generated.resources.create_problem_title
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun CreateProblemScreen() {
    var prize by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf("") }
    var entryFee by remember { mutableStateOf("") }
    var joinUntilDate by remember { mutableStateOf("") }
    var submitUntilDate by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
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
