package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateProblemScreen() {
    var prize by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf("") }
    var entryFee by remember { mutableStateOf("") }
    var joinUntilDate by remember { mutableStateOf("") }
    var submitUntilDate by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Utwórz problem", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = prize,
            onValueChange = { prize = it },
            label = { Text("Nagroda (np. wei / USDC)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = participants,
            onValueChange = { participants = it },
            label = { Text("Liczba uczestników") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = entryFee,
            onValueChange = { entryFee = it },
            label = { Text("Wejściówka (np. wei / USDC)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = joinUntilDate,
            onValueChange = { joinUntilDate = it },
            label = { Text("Koniec rejestracji (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = submitUntilDate,
            onValueChange = { submitUntilDate = it },
            label = { Text("Koniec zgłoszeń (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { /* TODO: call backend */ },
            enabled = false
        ) {
            Text("Utwórz (placeholder)")
        }
    }
}
