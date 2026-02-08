package pl.dawidszczesniak.blockchain_platform.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlin.reflect.KClass
import pl.dawidszczesniak.blockchain_platform.di.LocalKoin

interface Store {
    fun close()
}

@Composable
fun <T : Store> rememberStore(clazz: KClass<T>): T {
    val koin = LocalKoin.current
    val store: T = remember(clazz) { koin.get(clazz) }
    DisposableEffect(store) {
        onDispose { store.close() }
    }
    return store
}

@Composable
inline fun <reified T : Store> rememberStore(): T = rememberStore(T::class)
