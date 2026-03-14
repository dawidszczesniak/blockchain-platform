package pl.dawidszczesniak.blockchain_platform.di

import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DatabaseBootstrapper
import pl.dawidszczesniak.blockchain_platform.db.DatabaseFactory
import pl.dawidszczesniak.blockchain_platform.db.DbSchemaRunner
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.ExposedDbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.PostgresConfig
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.controller.DashboardController
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao.DashboardDao
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao.DashboardDaoImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardReadRepository
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardReadRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemController
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemDao
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemDaoImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.LoginDefaultUserUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.LoginDefaultUserUseCaseImpl

internal fun serverModules() = module {
    single { PostgresConfig.fromEnvironment() }
    single<Database> { DatabaseFactory.connect(get()) }
    single<DbTransactionRunner> { ExposedDbTransactionRunner(get()) }

    single { DbSchemaRunner(get()) }
    single { DashboardMetricsRefresher() }
    single { DatabaseBootstrapper(get(), get(), get()) }

    single<ProblemDao> { ProblemDaoImpl() }
    single { ProblemReadRepositoryImpl(get(), get()) }
    single<ProblemReadRepository> { get<ProblemReadRepositoryImpl>() }
    single<ProblemWriteRepository> { get<ProblemReadRepositoryImpl>() }
    factory<CreateProblemUseCase> { CreateProblemUseCaseImpl(get(), get(), get()) }
    factory<GetProblemSummariesUseCase> { GetProblemSummariesUseCaseImpl(get()) }
    factory<GetCreatedProblemsUseCase> { GetCreatedProblemsUseCaseImpl(get()) }
    factory<GetParticipationProblemsUseCase> { GetParticipationProblemsUseCaseImpl(get()) }
    factory<LoginDefaultUserUseCase> { LoginDefaultUserUseCaseImpl(get()) }
    factory { ProblemController(get(), get(), get(), get(), get()) }

    single<DashboardDao> { DashboardDaoImpl(get()) }
    single<DashboardReadRepository> { DashboardReadRepositoryImpl(get(), get()) }
    factory<GetDashboardMetricsHistoryUseCase> { GetDashboardMetricsHistoryUseCaseImpl(get()) }
    factory<GetDashboardUpdatesUseCase> { GetDashboardUpdatesUseCaseImpl(get()) }
    factory { DashboardController(get(), get()) }
}
