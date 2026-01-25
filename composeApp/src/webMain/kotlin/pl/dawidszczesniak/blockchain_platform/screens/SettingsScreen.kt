package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.settings_future
import blockchain_platform.composeapp.generated.resources.settings_placeholder
import blockchain_platform.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(Res.string.settings_placeholder))
                Text(stringResource(Res.string.settings_future))
            }
        }
    }
}
