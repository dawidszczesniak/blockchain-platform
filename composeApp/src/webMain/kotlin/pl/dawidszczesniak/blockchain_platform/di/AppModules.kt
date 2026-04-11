package pl.dawidszczesniak.blockchain_platform.di

import org.koin.dsl.module
import pl.dawidszczesniak.blockchain_platform.config.AppConfig
import pl.dawidszczesniak.blockchain_platform.config.AppConfigProvider
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.datasource.DashboardRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.datasource.DashboardRemoteDataSourceImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.datasource.ProblemRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.problems.datasource.ProblemRemoteDataSourceImpl
import pl.dawidszczesniak.blockchain_platform.feature.login.datasource.LoginRemoteDataSource
import pl.dawidszczesniak.blockchain_platform.feature.login.datasource.LoginRemoteDataSourceImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository
import pl.dawidszczesniak.blockchain_platform.feature.login.repository.LoginRepository
import pl.dawidszczesniak.blockchain_platform.feature.login.repository.LoginRepositoryImpl
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
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmitProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmitProblemCodeUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.app.AppViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.ValidateCreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.ValidateCreateProblemUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.create.CreateProblemViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.details.ProblemDetailsViewModel
import pl.dawidszczesniak.blockchain_platform.feature.home.DashboardConfig
import pl.dawidszczesniak.blockchain_platform.feature.home.HomeViewModel
import pl.dawidszczesniak.blockchain_platform.feature.login.InjectedWalletProvider
import pl.dawidszczesniak.blockchain_platform.feature.login.LoginViewModel
import pl.dawidszczesniak.blockchain_platform.feature.login.LoginUseCase
import pl.dawidszczesniak.blockchain_platform.feature.login.LoginUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.login.WalletProvider
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendHealthViewModel
import pl.dawidszczesniak.blockchain_platform.feature.maintenance.BackendMaintenanceViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.participation.MyParticipationViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.participation.ParticipationSyncStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.created.MyProblemsViewModel
import pl.dawidszczesniak.blockchain_platform.feature.problems.list.ProblemsListViewModel
import pl.dawidszczesniak.blockchain_platform.feature.settings.SettingsViewModel
import pl.dawidszczesniak.blockchain_platform.feature.settings.AppLanguageStore
import pl.dawidszczesniak.blockchain_platform.network.BrowserHttpTextClient
import pl.dawidszczesniak.blockchain_platform.network.HttpTextClient

fun appModules() = module {
    single { AppConfigProvider.config }
    single { AppLanguageStore() }
    single { ParticipationSyncStore() }
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
    single<LoginRemoteDataSource> {
        LoginRemoteDataSourceImpl(
            apiBaseUrl = get<AppConfig>().apiBaseUrl,
            httpTextClient = get(),
        )
    }
    single<LoginRepository> {
        LoginRepositoryImpl(remoteDataSource = get())
    }
    single<WalletProvider> { InjectedWalletProvider() }
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
    factory<JoinProblemUseCase> { JoinProblemUseCaseImpl(get()) }
    factory<RunProblemCodeUseCase> { RunProblemCodeUseCaseImpl(get()) }
    factory<SubmitProblemCodeUseCase> { SubmitProblemCodeUseCaseImpl(get()) }
    factory<CreateProblemUseCase> { CreateProblemUseCaseImpl(get()) }
    factory<ValidateCreateProblemUseCase> { ValidateCreateProblemUseCaseImpl(get()) }
    factory<GetDashboardMetricsHistoryUseCase> { GetDashboardMetricsHistoryUseCaseImpl(get()) }
    factory<GetDashboardUpdatesUseCase> { GetDashboardUpdatesUseCaseImpl(get()) }
    factory<LoginUseCase> { LoginUseCaseImpl(get(), get()) }
    factory { AppViewModel(get()) }
    factory { HomeViewModel(get(), get(), get()) }
    factory { LoginViewModel(get()) }
    factory { SettingsViewModel(get(), get()) }
    factory { BackendHealthViewModel(get()) }
    factory { BackendMaintenanceViewModel() }
    factory { CreateProblemViewModel(get(), get()) }
    factory { ProblemDetailsViewModel(get(), get(), get(), get(), get()) }
    factory { ProblemsListViewModel(get()) }
    factory { MyProblemsViewModel(get()) }
    factory { MyParticipationViewModel(get(), get()) }
}
