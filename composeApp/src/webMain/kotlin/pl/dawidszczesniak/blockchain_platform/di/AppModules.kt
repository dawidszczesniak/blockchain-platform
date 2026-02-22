package pl.dawidszczesniak.blockchain_platform.di

import org.koin.dsl.module
import pl.dawidszczesniak.blockchain_platform.AppConfigProvider
import pl.dawidszczesniak.blockchain_platform.data.MockDataConfig
import pl.dawidszczesniak.blockchain_platform.data.MockProblemRepository
import pl.dawidszczesniak.blockchain_platform.domain.repository.ProblemRepository
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetCreatedProblems
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetParticipationProblems
import pl.dawidszczesniak.blockchain_platform.domain.usecase.GetProblemSummaries
import pl.dawidszczesniak.blockchain_platform.presentation.app.AppViewModel
import pl.dawidszczesniak.blockchain_platform.presentation.app.BackendHealthViewModel
import pl.dawidszczesniak.blockchain_platform.presentation.created.CreatedProblemsViewModel
import pl.dawidszczesniak.blockchain_platform.presentation.create.CreateProblemViewModel
import pl.dawidszczesniak.blockchain_platform.presentation.participation.ParticipationViewModel
import pl.dawidszczesniak.blockchain_platform.presentation.problems.ProblemsListViewModel

fun appModules() = module {
    single { AppConfigProvider.config }
    single { MockDataConfig() }
    single<ProblemRepository> { MockProblemRepository(get()) }
    factory { GetProblemSummaries(get()) }
    factory { GetCreatedProblems(get()) }
    factory { GetParticipationProblems(get()) }
    factory { AppViewModel() }
    factory { BackendHealthViewModel(get()) }
    factory { CreateProblemViewModel() }
    factory { ProblemsListViewModel(get()) }
    factory { CreatedProblemsViewModel(get()) }
    factory { ParticipationViewModel(get()) }
}
