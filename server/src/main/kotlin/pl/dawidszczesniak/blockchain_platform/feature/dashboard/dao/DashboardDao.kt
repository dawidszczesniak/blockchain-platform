package pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.tables.DashboardDailyMetricsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable

internal interface DashboardDao {
    fun fetchMetricsHistory(limit: Int): List<DashboardMetric>
    fun fetchLatestUpdates(limit: Int): List<DashboardUpdate>
}

internal class DashboardDaoImpl(
    private val database: Database,
    private val metricsRefresher: DashboardMetricsRefresher,
) : DashboardDao {
    override fun fetchMetricsHistory(limit: Int): List<DashboardMetric> {
        metricsRefresher.refreshTodayMetrics()
        val safeLimit = limit.coerceIn(1, 365)

        return transaction(database) {
            DashboardDailyMetricsTable
                .selectAll()
                .orderBy(DashboardDailyMetricsTable.metricDate to SortOrder.DESC)
                .limit(safeLimit)
                .map { row ->
                    DashboardMetric(
                        metricDate = row[DashboardDailyMetricsTable.metricDate].toString(),
                        activeChallenges = row[DashboardDailyMetricsTable.activeChallenges],
                        prizePoolAmount = row[DashboardDailyMetricsTable.prizePoolAmount],
                        submissionsCount = row[DashboardDailyMetricsTable.submissionsCount],
                    )
                }
        }
    }

    override fun fetchLatestUpdates(limit: Int): List<DashboardUpdate> {
        val safeLimit = limit.coerceIn(1, 20)

        return transaction(database) {
            ProblemsTable
                .selectAll()
                .orderBy(
                    ProblemsTable.createdAt to SortOrder.DESC,
                    ProblemsTable.problemId to SortOrder.DESC,
                )
                .limit(safeLimit)
                .map { row ->
                    DashboardUpdate(
                        id = row[ProblemsTable.problemId],
                        title = row[ProblemsTable.title],
                        body = problemUpdateBody(
                            description = row[ProblemsTable.description],
                            prizeAmount = row[ProblemsTable.prizeAmount],
                        ),
                        createdAt = row[ProblemsTable.createdAt].toString(),
                    )
                }
        }
    }

    private fun problemUpdateBody(description: String, prizeAmount: Long): String {
        return "$description Prize: $prizeAmount USDC."
    }
}
