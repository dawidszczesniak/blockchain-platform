@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.dawidszczesniak.blockchain_platform.di

import kotlin.JsFun
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.js.Promise
import org.koin.dsl.module
import pl.dawidszczesniak.blockchain_platform.config.AppConfig
import pl.dawidszczesniak.blockchain_platform.config.AppConfigProvider
import pl.dawidszczesniak.blockchain_platform.data.BackendDashboardRepository
import pl.dawidszczesniak.blockchain_platform.data.BackendProblemRepository
import pl.dawidszczesniak.blockchain_platform.data.DashboardRepository
import pl.dawidszczesniak.blockchain_platform.domain.repository.ProblemRepository
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetProblemListUseCase
import pl.dawidszczesniak.blockchain_platform.app.AppViewModel
import pl.dawidszczesniak.blockchain_platform.feature.create.CreateProblemViewModel
import pl.dawidszczesniak.blockchain_platform.feature.home.HomeViewModel
import pl.dawidszczesniak.blockchain_platform.feature.login.LoginViewModel
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendHealthViewModel
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendMaintenanceViewModel
import pl.dawidszczesniak.blockchain_platform.feature.myparticipation.MyParticipationViewModel
import pl.dawidszczesniak.blockchain_platform.feature.myproblems.MyProblemsViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.ProblemsListViewModel
import pl.dawidszczesniak.blockchain_platform.feature.settings.SettingsViewModel

fun appModules() = module {
    single { AppConfigProvider.config }
    single<ProblemRepository> {
        BackendProblemRepository(
            apiBaseUrl = get<AppConfig>().apiBaseUrl,
            fetchText = ::fetchBackendText,
        )
    }
    single<DashboardRepository> {
        BackendDashboardRepository(
            apiBaseUrl = get<AppConfig>().apiBaseUrl,
            fetchText = ::fetchBackendText,
        )
    }
    factory { GetProblemListUseCase(get()) }
    factory { GetCreatedProblemsUseCase(get()) }
    factory { GetParticipationProblemsUseCase(get()) }
    factory { AppViewModel() }
    factory { HomeViewModel(get()) }
    factory { LoginViewModel() }
    factory { SettingsViewModel() }
    factory { BackendHealthViewModel(get()) }
    factory { BackendMaintenanceViewModel() }
    factory { CreateProblemViewModel() }
    factory { ProblemsListViewModel(get()) }
    factory { MyProblemsViewModel(get()) }
    factory { MyParticipationViewModel(get()) }
}

private suspend fun fetchBackendText(url: String): String {
    return suspendCoroutine { continuation ->
        fetchText(url).then(
            onFulfilled = { body ->
                continuation.resume(jsAnyToString(body))
                body
            },
            onRejected = { error ->
                continuation.resumeWithException(
                    IllegalStateException("Failed to fetch '$url'.")
                )
                error
            }
        )
    }
}

@JsFun("(url) => fetch(url, { method: 'GET', cache: 'no-store' }).then(r => { if (!r.ok) { throw new Error('HTTP ' + r.status); } return r.text(); })")
private external fun fetchText(url: String): Promise<JsAny?>

@JsFun("(value) => String(value)")
private external fun jsAnyToString(value: JsAny?): String
