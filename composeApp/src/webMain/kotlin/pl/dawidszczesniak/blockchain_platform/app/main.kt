package pl.dawidszczesniak.blockchain_platform.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import pl.dawidszczesniak.blockchain_platform.di.initDi
import pl.dawidszczesniak.blockchain_platform.di.KoinProvider

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koin = initDi()
    ComposeViewport {
        KoinProvider(koin) {
            App()
        }
    }
}
