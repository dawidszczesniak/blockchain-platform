package pl.dawidszczesniak.blockchain_platform.db

import java.time.LocalDate
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import pl.dawidszczesniak.blockchain_platform.db.tables.DashboardDailyMetricsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable

internal class DashboardMetricsRefresher(
    private val database: Database,
) {
    fun refreshTodayMetrics() {
        transaction(database) {
            val today = LocalDate.now()
            val snapshot = calculateDashboardMetrics(metricDate = today)
            val existingRow = DashboardDailyMetricsTable
                .selectAll()
                .where { DashboardDailyMetricsTable.metricDate eq today }
                .limit(1)
                .firstOrNull()

            if (existingRow == null) {
                DashboardDailyMetricsTable.insert {
                    it[metricDate] = snapshot.metricDate
                    it[activeChallenges] = snapshot.activeChallenges
                    it[prizePoolAmount] = snapshot.prizePoolAmount
                    it[submissionsCount] = snapshot.submissionsCount
                }
            } else {
                DashboardDailyMetricsTable.update({ DashboardDailyMetricsTable.metricDate eq today }) {
                    it[activeChallenges] = snapshot.activeChallenges
                    it[prizePoolAmount] = snapshot.prizePoolAmount
                    it[submissionsCount] = snapshot.submissionsCount
                }
            }
        }
    }

    private fun calculateDashboardMetrics(metricDate: LocalDate): DashboardDailyMetricSnapshot {
        val activeChallenges = ProblemsTable
            .selectAll()
            .where { ProblemsTable.problemStatus eq ProblemLifecycleStatus.Open.dbValue }
            .count()
            .toInt()

        val prizePoolAmount = ProblemsTable
            .selectAll()
            .where { ProblemsTable.problemStatus eq ProblemLifecycleStatus.Open.dbValue }
            .sumOf { row -> row[ProblemsTable.prizeAmount] }

        val submissionsCount = ProblemSubmissionsTable
            .selectAll()
            .count { row ->
                row[ProblemSubmissionsTable.submittedAt].toLocalDate() == metricDate
            }

        return DashboardDailyMetricSnapshot(
            metricDate = metricDate,
            activeChallenges = activeChallenges,
            prizePoolAmount = prizePoolAmount,
            submissionsCount = submissionsCount,
        )
    }
}

private data class DashboardDailyMetricSnapshot(
    val metricDate: LocalDate,
    val activeChallenges: Int,
    val prizePoolAmount: Long,
    val submissionsCount: Int,
)
