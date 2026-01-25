package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Zaloguj się", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text("Na razie wydmuszka logowania portfelem.")
            Spacer(Modifier.height(20.dp))
            Button(onClick = onLogin) {
                Text("Połącz portfel (mock)")
            }
        }
    }
}
