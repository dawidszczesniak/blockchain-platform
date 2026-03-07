package pl.dawidszczesniak.blockchain_platform.di

import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DatabaseBootstrapper
import pl.dawidszczesniak.blockchain_platform.db.DatabaseFactory
import pl.dawidszczesniak.blockchain_platform.db.DbSchemaRunner
import pl.dawidszczesniak.blockchain_platform.db.DbSeeder
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
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCaseImpl

internal fun serverModules() = module {
    single { PostgresConfig.fromEnvironment() }
    single<Database> { DatabaseFactory.connect(get()) }
    single { DbSchemaRunner(get()) }
    single { DbSeeder(get()) }
    single { DashboardMetricsRefresher(get()) }
    single { DatabaseBootstrapper(get(), get(), get()) }

    single<ProblemDao> { ProblemDaoImpl(get()) }
    single<ProblemReadRepository> { ProblemReadRepositoryImpl(get()) }
    factory<GetProblemSummariesUseCase> { GetProblemSummariesUseCaseImpl(get()) }
    factory<GetCreatedProblemsUseCase> { GetCreatedProblemsUseCaseImpl(get()) }
    factory<GetParticipationProblemsUseCase> { GetParticipationProblemsUseCaseImpl(get()) }
    factory { ProblemController(get(), get(), get()) }

    single<DashboardDao> { DashboardDaoImpl(get(), get()) }
    single<DashboardReadRepository> { DashboardReadRepositoryImpl(get()) }
    factory<GetDashboardMetricsHistoryUseCase> { GetDashboardMetricsHistoryUseCaseImpl(get()) }
    factory<GetDashboardUpdatesUseCase> { GetDashboardUpdatesUseCaseImpl(get()) }
    factory { DashboardController(get(), get()) }
}
