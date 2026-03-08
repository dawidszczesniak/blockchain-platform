package pl.dawidszczesniak.blockchain_platform.di

import org.koin.dsl.module
import pl.dawidszczesniak.blockchain_platform.config.AppConfig
import pl.dawidszczesniak.blockchain_platform.config.AppConfigProvider
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.datasource.DashboardRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.datasource.DashboardRemoteDataSourceImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.datasource.ProblemRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.problems.datasource.ProblemRemoteDataSourceImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemListUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemListUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.app.AppViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemViewModel
import pl.dawidszczesniak.blockchain_platform.feature.home.DashboardConfig
import pl.dawidszczesniak.blockchain_platform.feature.home.HomeViewModel
import pl.dawidszczesniak.blockchain_platform.feature.login.LoginViewModel
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendHealthViewModel
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendMaintenanceViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.participation.MyParticipationViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.created.MyProblemsViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.list.ProblemsListViewModel
import pl.dawidszczesniak.blockchain_platform.feature.settings.SettingsViewModel
import pl.dawidszczesniak.blockchain_platform.network.BrowserHttpTextClient
import pl.dawidszczesniak.blockchain_platform.network.HttpTextClient

fun appModules() = module {
    single { AppConfigProvider.config }
    single<HttpTextClient> { BrowserHttpTextClient() }
    single<ProblemRemoteDataSource> {
        ProblemRemoteDataSourceImpl(
            apiBaseUrl = get<AppConfig>().apiBaseUrl,
            httpTextClient = get(),
        )
    }
    single<ProblemRepository> {
        ProblemRepositoryImpl(remoteDataSource = get())
    }
    single<DashboardRemoteDataSource> {
        DashboardRemoteDataSourceImpl(
            apiBaseUrl = get<AppConfig>().apiBaseUrl,
            httpTextClient = get(),
        )
    }
    single<DashboardRepository> {
        DashboardRepositoryImpl(remoteDataSource = get())
    }
    single { DashboardConfig() }
    factory<GetProblemListUseCase> { GetProblemListUseCaseImpl(get()) }
    factory<GetCreatedProblemsUseCase> { GetCreatedProblemsUseCaseImpl(get()) }
    factory<GetParticipationProblemsUseCase> { GetParticipationProblemsUseCaseImpl(get()) }
    factory<CreateProblemUseCase> { CreateProblemUseCaseImpl(get()) }
    factory<GetDashboardMetricsHistoryUseCase> { GetDashboardMetricsHistoryUseCaseImpl(get()) }
    factory<GetDashboardUpdatesUseCase> { GetDashboardUpdatesUseCaseImpl(get()) }
    factory { AppViewModel() }
    factory { HomeViewModel(get(), get(), get()) }
    factory { LoginViewModel() }
    factory { SettingsViewModel() }
    factory { BackendHealthViewModel(get()) }
    factory { BackendMaintenanceViewModel() }
    factory { CreateProblemViewModel(get()) }
    factory { ProblemsListViewModel(get()) }
    factory { MyProblemsViewModel(get()) }
    factory { MyParticipationViewModel(get()) }
}
