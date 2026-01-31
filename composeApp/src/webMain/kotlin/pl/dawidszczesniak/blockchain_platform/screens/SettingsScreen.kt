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
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppSurface(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))
            // TODO(backend): Load real user settings from backend.
            Text(stringResource(Res.string.settings_placeholder))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.settings_future),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
