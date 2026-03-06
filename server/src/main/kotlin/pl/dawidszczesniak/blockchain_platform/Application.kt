package pl.dawidszczesniak.blockchain_platform

import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.json
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DatabaseFactory
import pl.dawidszczesniak.blockchain_platform.db.DbSchemaRunner
import pl.dawidszczesniak.blockchain_platform.db.DbSeeder
import pl.dawidszczesniak.blockchain_platform.db.PostgresConfig
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.controller.DashboardController
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao.DashboardDaoImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dbservice.DashboardDbServiceImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.endpoint.dashboardRoutes
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository.DashboardReadRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardMetricsHistoryUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.usecase.GetDashboardUpdatesUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.controller.ProblemController
import pl.dawidszczesniak.blockchain_platform.feature.problems.dao.ProblemDaoImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.dbservice.ProblemDbServiceImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.endpoint.problemRoutes
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemReadRepositoryImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetCreatedProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetParticipationProblemsUseCaseImpl
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCase
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.GetProblemSummariesUseCaseImpl

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = LOCAL_HOST, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val envId = System.getenv("APP_ENV") ?: AppEnvironment.Local.id
    val appEnv = parseAppEnvironment(AppEnvironment.fromId(envId))
    val allowedHosts = resolveAllowedCorsHosts(appEnv)
    val postgresConfig = PostgresConfig.fromEnvironment()
    val database = DatabaseFactory.connect(postgresConfig)
    DbSchemaRunner(postgresConfig).applySchema()
    DbSeeder(database).seedIfEmpty()
    val dashboardMetricsRefresher = DashboardMetricsRefresher(database)
    dashboardMetricsRefresher.refreshTodayMetrics()

    val problemDao = ProblemDaoImpl(database)
    val problemDbService = ProblemDbServiceImpl(problemDao)
    val problemRepository = ProblemReadRepositoryImpl(problemDbService)
    val getProblemSummariesUseCase: GetProblemSummariesUseCase = GetProblemSummariesUseCaseImpl(problemRepository)
    val getCreatedProblemsUseCase: GetCreatedProblemsUseCase = GetCreatedProblemsUseCaseImpl(problemRepository)
    val getParticipationProblemsUseCase: GetParticipationProblemsUseCase =
        GetParticipationProblemsUseCaseImpl(problemRepository)
    val problemController = ProblemController(
        getProblemSummariesUseCase = getProblemSummariesUseCase,
        getCreatedProblemsUseCase = getCreatedProblemsUseCase,
        getParticipationProblemsUseCase = getParticipationProblemsUseCase,
    )

    val dashboardDao = DashboardDaoImpl(
        database = database,
        metricsRefresher = dashboardMetricsRefresher,
    )
    val dashboardDbService = DashboardDbServiceImpl(dashboardDao)
    val dashboardRepository = DashboardReadRepositoryImpl(dashboardDbService)
    val getDashboardMetricsHistoryUseCase: GetDashboardMetricsHistoryUseCase =
        GetDashboardMetricsHistoryUseCaseImpl(dashboardRepository)
    val getDashboardUpdatesUseCase: GetDashboardUpdatesUseCase =
        GetDashboardUpdatesUseCaseImpl(dashboardRepository)
    val dashboardController = DashboardController(
        getDashboardMetricsHistoryUseCase = getDashboardMetricsHistoryUseCase,
        getDashboardUpdatesUseCase = getDashboardUpdatesUseCase,
    )

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowedHosts.forEach { host ->
            allowHost(host)
        }
    }
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Ktor OK")
        }
        get("/health") {
            call.respondText("OK")
        }
        problemRoutes(problemController)
        dashboardRoutes(dashboardController)
    }
}

private fun resolveAllowedCorsHosts(env: AppEnvironment): List<String> {
    return when (env) {
        AppEnvironment.Local -> listOf("$LOCAL_HOST:$FRONTEND_PORT")
        AppEnvironment.Staging,
        AppEnvironment.Prod,
        -> emptyList()
    }
}
