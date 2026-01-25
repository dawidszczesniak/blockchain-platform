package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Ustawienia", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Placeholder ustawień.")
                Text("Tu później: adres portfela, RPC, itp.")
            }
        }
    }
}
