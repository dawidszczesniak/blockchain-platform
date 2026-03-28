package pl.dawidszczesniak.blockchain_platform.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.settings_interface_language
import blockchain_platform.composeapp.generated.resources.settings_language_english
import blockchain_platform.composeapp.generated.resources.settings_language_polish
import blockchain_platform.composeapp.generated.resources.settings_not_connected
import blockchain_platform.composeapp.generated.resources.settings_title
import blockchain_platform.composeapp.generated.resources.settings_wallet_address
import blockchain_platform.composeapp.generated.resources.settings_wallet_section
import blockchain_platform.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.ui.AppPanelLoader
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun SettingsScreen() {
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<SettingsViewModel>() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppSurface(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(Res.string.settings_wallet_section),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            if (state.isWalletLoading) {
                AppPanelLoader(minHeight = 72.dp)
            } else {
                WalletAddressRow(
                    label = stringResource(Res.string.settings_wallet_address),
                    value = state.walletAddress ?: stringResource(Res.string.settings_not_connected),
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(Res.string.settings_interface_language),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LanguageButton(
                    label = stringResource(Res.string.settings_language_polish),
                    selected = state.selectedLanguage == UiLanguage.Polish,
                    onClick = { viewModel.onIntent(SettingsIntent.SelectLanguage(UiLanguage.Polish)) },
                )
                LanguageButton(
                    label = stringResource(Res.string.settings_language_english),
                    selected = state.selectedLanguage == UiLanguage.English,
                    onClick = { viewModel.onIntent(SettingsIntent.SelectLanguage(UiLanguage.English)) },
                )
            }
        }
    }
}

@Composable
private fun WalletAddressRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LanguageButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}
