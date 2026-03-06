package pl.dawidszczesniak.blockchain_platform.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.settings_future
import blockchain_platform.composeapp.generated.resources.settings_placeholder
import blockchain_platform.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun SettingsScreen() {
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<SettingsViewModel>() }
    val state by viewModel.state.collectAsState()

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
            if (state.showPlaceholder) {
                Text(stringResource(Res.string.settings_placeholder))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.settings_future),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
