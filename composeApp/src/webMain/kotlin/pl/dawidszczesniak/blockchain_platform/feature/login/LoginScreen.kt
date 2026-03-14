package pl.dawidszczesniak.blockchain_platform.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import blockchain_platform.composeapp.generated.resources.Res
import blockchain_platform.composeapp.generated.resources.login_connect_wallet
import blockchain_platform.composeapp.generated.resources.login_subtitle
import blockchain_platform.composeapp.generated.resources.login_title
import blockchain_platform.composeapp.generated.resources.login_wallet_not_found
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image as SkiaImage
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin
import pl.dawidszczesniak.blockchain_platform.ui.AppSurface

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val koin = LocalKoin.current
    val viewModel = remember { koin.get<LoginViewModel>() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsState()
    var walletMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppSurface(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.login_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                Box {
                    Button(
                        enabled = !state.isConnectingWallet && !state.isLoadingWallets,
                        onClick = {
                            if (state.wallets.isEmpty()) {
                                viewModel.refreshWallets()
                            } else {
                                walletMenuExpanded = true
                            }
                        }
                    ) {
                        Text(stringResource(Res.string.login_connect_wallet))
                    }
                    DropdownMenu(
                        expanded = walletMenuExpanded,
                        onDismissRequest = { walletMenuExpanded = false },
                    ) {
                        state.wallets.forEach { wallet ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        WalletIcon(wallet)
                                        Text(wallet.name)
                                    }
                                },
                                onClick = {
                                    walletMenuExpanded = false
                                    viewModel.connectWallet(wallet.id, onSuccess = onLogin)
                                },
                            )
                        }
                    }
                }

                if (state.isLoadingWallets) {
                    Spacer(Modifier.height(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                if (!state.isLoadingWallets && state.wallets.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(Res.string.login_wallet_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletIcon(wallet: LoginWalletOption) {
    val officialIconBitmap = remember(wallet.iconUri) {
        loadWalletIconBitmap(wallet.iconUri)
    }
    if (officialIconBitmap != null) {
        Image(
            bitmap = officialIconBitmap,
            contentDescription = "${wallet.name} icon",
            modifier = Modifier.size(22.dp),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Spacer(Modifier.size(22.dp))
}

@OptIn(ExperimentalEncodingApi::class)
private fun loadWalletIconBitmap(iconDataUrl: String?): ImageBitmap? {
    val normalized = iconDataUrl?.trim().orEmpty()
    if (normalized.isBlank()) {
        return null
    }
    walletIconBitmapCache[normalized]?.let { cached ->
        return cached
    }
    if (walletIconBitmapCache.containsKey(normalized)) {
        return null
    }

    val base64Payload = normalized.substringAfter("base64,", missingDelimiterValue = "")
    if (base64Payload.isBlank()) {
        walletIconBitmapCache[normalized] = null
        return null
    }
    val bitmap = runCatching {
        SkiaImage.makeFromEncoded(Base64.decode(base64Payload)).toComposeImageBitmap()
    }.getOrNull()
    walletIconBitmapCache[normalized] = bitmap
    return bitmap
}

private val walletIconBitmapCache = mutableMapOf<String, ImageBitmap?>()
