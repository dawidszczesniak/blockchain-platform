package pl.dawidszczesniak.blockchain_platform.di

import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module
import redis.clients.jedis.JedisPooled
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DatabaseBootstrapper
import pl.dawidszczesniak.blockchain_platform.db.DatabaseFactory
import pl.dawidszczesniak.blockchain_platform.db.DbSchemaRunner
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.ExposedDbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.PostgresConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.auth.controller.AuthController
import pl.dawidszczesniak.blockchain_platform.feature.auth.dao.UserDao
import pl.dawidszczesniak.blockchain_platform.feature.auth.dao.UserDaoImpl
import pl.dawidszczesniak.blockchain_platform.feature.auth.repository.AuthRepository
import pl.dawidszczesniak.blockchain_platform.feature.auth.repository.AuthRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.AuthRateLimiter
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.Eip1271SignatureVerifier
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.EthereumSignatureVerifier
import pl.dawidszczesniak.blockchain_platform.feature.auth.service.WalletChallengeService
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.AuthSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.RedisAuthSessionStore
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.RedisWalletChallengeStore
import pl.dawidszczesniak.blockchain_platform.feature.auth.store.WalletChallengeStore
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.CreateWalletChallengeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.CreateWalletChallengeUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.GetAuthenticatedWalletUseCase
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.GetAuthenticatedWalletUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.VerifyWalletChallengeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.auth.usecase.VerifyWalletChallengeUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.controller.DashboardController
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao.DashboardDao
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao.DashboardDaoImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardReadRepository
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardReadRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.platform.controller.PlatformController
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemController
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.CompetitionIntentStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.RedisCompetitionIntentStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemDao
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemDaoImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.RedisSubmissionJudgeQueue
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.SubmissionJudgeJobMapper
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.SubmissionJudgeJobRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.SubmissionJudgeJobRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.SubmissionJudgeQueue
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.SubmissionJudgeWorker
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.EthereumBlockchainPlatformContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxHttpClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemDraftFactory
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.EnqueueProblemSubmissionUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.EnqueueProblemSubmissionUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetSubmissionJudgeJobUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetSubmissionJudgeJobUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.JoinProblemUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareCreateProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareCreateProblemOnChainUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmCreateProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmCreateProblemOnChainUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareJoinProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.PrepareJoinProblemOnChainUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmJoinProblemOnChainUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ConfirmJoinProblemOnChainUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CompetitionSettlementJobRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CompetitionSettlementJobRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CompetitionSettlementWakeupSignal
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CompetitionSettlementWorker
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.RunProblemCodeUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmissionJudgeService
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmitProblemCodeUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmitProblemCodeUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ValidateCreateProblemUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.ValidateCreateProblemUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemReferenceValidationService
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.CreateProblemReferenceValidationServiceImpl
import pl.dawidszczesniak.blockchain_platform.redis.RedisConfig
import pl.dawidszczesniak.blockchain_platform.redis.RedisFactory

internal fun serverModules(environment: Map<String, String>) = module {
    single { PostgresConfig.fromEnvironment(environment) }
    single { RedisConfig.fromEnvironment(environment) }
    single { AuthConfig.fromEnvironment(environment) }
    single { BlockchainConfig.fromEnvironment(environment) }
    single<Database> { DatabaseFactory.connect(get()) }
    single<JedisPooled> { RedisFactory.connect(get()) }
    single<DbTransactionRunner> { ExposedDbTransactionRunner(get()) }

    single { DbSchemaRunner(get()) }
    single { DashboardMetricsRefresher(get()) }
    single { DatabaseBootstrapper(get(), get(), get()) }

    single<UserDao> { UserDaoImpl() }
    single<AuthSessionStore> { RedisAuthSessionStore(get()) }
    single<WalletChallengeStore> { RedisWalletChallengeStore(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single { WalletChallengeService(get(), get(), get()) }
    single { AuthRateLimiter(get(), get()) }
    single { EthereumSignatureVerifier() }
    single { Eip1271SignatureVerifier(get()) }
    factory<CreateWalletChallengeUseCase> { CreateWalletChallengeUseCaseImpl(get()) }
    factory<VerifyWalletChallengeUseCase> { VerifyWalletChallengeUseCaseImpl(get(), get(), get(), get()) }
    factory<GetAuthenticatedWalletUseCase> { GetAuthenticatedWalletUseCaseImpl(get()) }
    factory { AuthController(get(), get(), get()) }

    single<ProblemDao> { ProblemDaoImpl() }
    single { CompetitionSettlementWakeupSignal() }
    single { ProblemReadRepositoryImpl(get(), get(), get(), get()) }
    single<ProblemReadRepository> { get<ProblemReadRepositoryImpl>() }
    single<ProblemWriteRepository> { get<ProblemReadRepositoryImpl>() }
    single { SandboxConfig.fromEnvironment(environment) }
    single { PaymentAssetCatalog.fromEnvironment(environment, get()) }
    single { BlockchainPlatformContractConfig.fromEnvironment(environment, get()) }
    single<SandboxClient> { SandboxHttpClient(get()) }
    single<SubmissionJudgeJobRepository> { SubmissionJudgeJobRepositoryImpl(get()) }
    single<SubmissionJudgeQueue> { RedisSubmissionJudgeQueue(get()) }
    single { SubmissionJudgeJobMapper() }
    single<CreateProblemReferenceValidationService> { CreateProblemReferenceValidationServiceImpl(get()) }
    single { CreateProblemDraftFactory(get(), get()) }
    single<CompetitionIntentStore> { RedisCompetitionIntentStore(get(), get()) }
    single<BlockchainPlatformContractClient> { EthereumBlockchainPlatformContractClient(get(), get()) }
    single<CompetitionSettlementJobRepository> { CompetitionSettlementJobRepositoryImpl(get(), get()) }
    factory<CreateProblemUseCase> { CreateProblemUseCaseImpl() }
    factory<PrepareCreateProblemOnChainUseCase> { PrepareCreateProblemOnChainUseCaseImpl(get(), get(), get(), get(), get()) }
    factory<ConfirmCreateProblemOnChainUseCase> { ConfirmCreateProblemOnChainUseCaseImpl(get(), get(), get(), get(), get(), get(), get()) }
    factory<ValidateCreateProblemUseCase> { ValidateCreateProblemUseCaseImpl(get()) }
    factory<GetProblemSummariesUseCase> { GetProblemSummariesUseCaseImpl(get()) }
    factory<GetCreatedProblemsUseCase> { GetCreatedProblemsUseCaseImpl(get()) }
    factory<GetParticipationProblemsUseCase> { GetParticipationProblemsUseCaseImpl(get()) }
    factory<JoinProblemUseCase> { JoinProblemUseCaseImpl() }
    factory<PrepareJoinProblemOnChainUseCase> { PrepareJoinProblemOnChainUseCaseImpl(get(), get(), get(), get(), get()) }
    factory<ConfirmJoinProblemOnChainUseCase> { ConfirmJoinProblemOnChainUseCaseImpl(get(), get(), get(), get()) }
    factory<RunProblemCodeUseCase> { RunProblemCodeUseCaseImpl(get(), get()) }
    single { SubmitProblemCodeUseCaseImpl(get(), get(), get(), get(), get()) }
    single<SubmitProblemCodeUseCase> { get<SubmitProblemCodeUseCaseImpl>() }
    single<SubmissionJudgeService> { get<SubmitProblemCodeUseCaseImpl>() }
    single<EnqueueProblemSubmissionUseCase> { EnqueueProblemSubmissionUseCaseImpl(get(), get(), get()) }
    single<GetSubmissionJudgeJobUseCase> { GetSubmissionJudgeJobUseCaseImpl(get(), get(), get()) }
    single { SubmissionJudgeWorker(get(), get(), get()) }
    single { CompetitionSettlementWorker(get(), get(), get(), get(), get()) }
    factory { ProblemController(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    single<DashboardDao> { DashboardDaoImpl(get()) }
    single<DashboardReadRepository> { DashboardReadRepositoryImpl(get(), get(), get()) }
    factory<GetDashboardMetricsHistoryUseCase> { GetDashboardMetricsHistoryUseCaseImpl(get()) }
    factory<GetDashboardUpdatesUseCase> { GetDashboardUpdatesUseCaseImpl(get()) }
    factory { DashboardController(get(), get()) }
    factory { PlatformController(get(), get(), get()) }
}
