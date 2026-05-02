package pl.dawidszczesniak.blockchain_platform.feature.dashboard.repository

import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.tables.DashboardDailyMetricsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.dao.DashboardDao
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardMetric
import pl.dawidszczesniak.blockchain_platform.feature.dashboard.domain.DashboardUpdate
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.platform.formatAtomicAmountDisplay

internal interface DashboardReadRepository {
    fun fetchMetricsHistory(limit: Int): List<DashboardMetric>
    fun fetchLatestUpdates(limit: Int): List<DashboardUpdate>
}

internal class DashboardReadRepositoryImpl(
    private val dashboardDao: DashboardDao,
    private val transactionRunner: DbTransactionRunner,
    private val paymentAssetCatalog: PaymentAssetCatalog,
) : DashboardReadRepository {
    override fun fetchMetricsHistory(limit: Int): List<DashboardMetric> {
        return transactionRunner.inTransaction {
            dashboardDao.refreshTodayMetrics()

            dashboardDao.fetchMetricsRows(limit = limit).map { row ->
                DashboardMetric(
                    metricDate = row[DashboardDailyMetricsTable.metricDate].toString(),
                    activeChallenges = row[DashboardDailyMetricsTable.activeChallenges],
                    prizePoolLabel = row[DashboardDailyMetricsTable.prizePoolLabel],
                    submissionsCount = row[DashboardDailyMetricsTable.submissionsCount],
                )
            }
        }
    }

    override fun fetchLatestUpdates(limit: Int): List<DashboardUpdate> {
        return transactionRunner.inTransaction {
            dashboardDao.fetchLatestUpdateRows(limit = limit).map { row ->
                DashboardUpdate(
                    id = row[ProblemsTable.problemId],
                    title = row[ProblemsTable.title],
                    body = problemUpdateBody(
                        description = row[ProblemsTable.description],
                        paymentAssetCode = row[ProblemsTable.paymentAssetCode],
                        prizeAmountAtomic = row[ProblemsTable.prizeAmountAtomic],
                    ),
                    createdAt = row[ProblemsTable.createdAt].toString(),
                )
            }
        }
    }

    private fun problemUpdateBody(
        description: String,
        paymentAssetCode: String,
        prizeAmountAtomic: String,
    ): String {
        val paymentAsset = paymentAssetCatalog.requireByCode(paymentAssetCode)
        val displayAmount = formatAtomicAmountDisplay(paymentAsset, prizeAmountAtomic)
        return "$description Prize: $displayAmount ${paymentAsset.symbol}."
    }
}
