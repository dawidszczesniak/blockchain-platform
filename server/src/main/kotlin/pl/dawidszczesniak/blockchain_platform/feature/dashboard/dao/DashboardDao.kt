package pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao

import java.time.LocalDate
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.tables.DashboardDailyMetricsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable

internal interface DashboardDao {
    fun refreshTodayMetrics()
    fun fetchMetricsRows(limit: Int): List<ResultRow>
    fun fetchLatestUpdateRows(limit: Int): List<ResultRow>
}

internal class DashboardDaoImpl(
    private val metricsRefresher: DashboardMetricsRefresher,
) : DashboardDao {
    override fun refreshTodayMetrics() {
        metricsRefresher.refreshTodayMetrics()
    }

    override fun fetchMetricsRows(limit: Int): List<ResultRow> {
        val safeLimit = limit.coerceIn(1, 365)
        val today = LocalDate.now()

        return DashboardDailyMetricsTable
            .selectAll()
            .where { DashboardDailyMetricsTable.metricDate lessEq today }
            .orderBy(DashboardDailyMetricsTable.metricDate to SortOrder.DESC)
            .limit(safeLimit)
            .toList()
    }

    override fun fetchLatestUpdateRows(limit: Int): List<ResultRow> {
        val safeLimit = limit.coerceIn(1, 20)

        return ProblemsTable
            .selectAll()
            .orderBy(
                ProblemsTable.createdAt to SortOrder.DESC,
                ProblemsTable.problemId to SortOrder.DESC,
            )
            .limit(safeLimit)
            .toList()
    }
}
