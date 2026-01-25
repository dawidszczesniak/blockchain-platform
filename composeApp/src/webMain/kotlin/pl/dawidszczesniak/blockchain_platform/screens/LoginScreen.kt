package pl.dawidszczesniak.blockchain_platform.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.login_connect_wallet
import blockchain_platform.composeapp.generated.resources.login_subtitle
import blockchain_platform.composeapp.generated.resources.login_title
import org.jetbrains.compose.resources.stringResource
import pl.dawidszczesniak.blockchain_platform.ui.AppHeader
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppHeader(
            title = stringResource(Res.string.login_title),
            subtitle = stringResource(Res.string.login_subtitle)
        )

        AppSurface(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onLogin) {
                    Text(stringResource(Res.string.login_connect_wallet))
                }
            }
        }
    }
}
