package pl.dawidszczesniak.blockchain_platform.di

import org.koin.core.Koin
import org.koin.core.context.startKoin

fun initDi(): Koin {
    return startKoin {
        modules(appModules())
    }.koin
}
