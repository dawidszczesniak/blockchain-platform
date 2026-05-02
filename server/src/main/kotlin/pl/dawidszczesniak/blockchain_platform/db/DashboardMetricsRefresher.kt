package pl.dawidszczesniak.blockchain_platform.db

import java.math.BigInteger
import java.time.LocalDate
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import pl.dawidszczesniak.blockchain_platform.db.tables.DashboardDailyMetricsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.platform.formatAtomicAmountDisplay
import pl.dawidszczesniak.blockchain_platform.feature.problems.atomicAmountToBigInteger

internal class DashboardMetricsRefresher(
    private val paymentAssetCatalog: PaymentAssetCatalog,
) {
    fun refreshTodayMetrics() {
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
                it[prizePoolLabel] = snapshot.prizePoolLabel
                it[submissionsCount] = snapshot.submissionsCount
            }
        } else {
            DashboardDailyMetricsTable.update({ DashboardDailyMetricsTable.metricDate eq today }) {
                it[activeChallenges] = snapshot.activeChallenges
                it[prizePoolLabel] = snapshot.prizePoolLabel
                it[submissionsCount] = snapshot.submissionsCount
            }
        }
    }

    private fun calculateDashboardMetrics(metricDate: LocalDate): DashboardDailyMetricSnapshot {
        val openProblemRows = ProblemsTable
            .selectAll()
            .where { ProblemsTable.problemStatus eq ProblemLifecycleStatus.Open.dbValue }
            .toList()
        val activeChallenges = openProblemRows.size
        val prizePoolLabel = buildPrizePoolLabel(openProblemRows)

        val submissionsCount = ProblemSubmissionsTable
            .selectAll()
            .count { row ->
                row[ProblemSubmissionsTable.submittedAt].toLocalDate() == metricDate
            }

        return DashboardDailyMetricSnapshot(
            metricDate = metricDate,
            activeChallenges = activeChallenges,
            prizePoolLabel = prizePoolLabel,
            submissionsCount = submissionsCount,
        )
    }

    private fun buildPrizePoolLabel(openProblemRows: List<ResultRow>): String {
        if (openProblemRows.isEmpty()) {
            return "0"
        }
        val assetCodes = openProblemRows.map { row -> row[ProblemsTable.paymentAssetCode] }.distinct()
        if (assetCodes.size != 1) {
            return "Mixed assets"
        }
        val paymentAsset = runCatching {
            paymentAssetCatalog.requireByCode(assetCodes.single())
        }.getOrNull() ?: return "Mixed assets"
        val totalAtomicAmount = openProblemRows.fold(BigInteger.ZERO) { sum, row ->
            sum + atomicAmountToBigInteger(row[ProblemsTable.prizeAmountAtomic], "Prize amount")
        }
        return "${formatAtomicAmountDisplay(paymentAsset, totalAtomicAmount.toString())} ${paymentAsset.symbol}"
    }
}

private data class DashboardDailyMetricSnapshot(
    val metricDate: LocalDate,
    val activeChallenges: Int,
    val prizePoolLabel: String,
    val submissionsCount: Int,
)
