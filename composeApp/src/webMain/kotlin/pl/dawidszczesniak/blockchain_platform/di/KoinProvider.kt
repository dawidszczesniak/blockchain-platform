package pl.dawidszczesniak.blockchain_platform.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.core.Koin

val LocalKoin = staticCompositionLocalOf<Koin> {
    error("Koin not initialized")
}

@Composable
fun KoinProvider(
    koin: Koin,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalKoin provides koin, content = content)
}
