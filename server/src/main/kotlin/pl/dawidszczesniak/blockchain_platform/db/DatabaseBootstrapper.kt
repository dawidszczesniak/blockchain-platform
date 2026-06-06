package pl.dawidszczesniak.blockchain_platform.db

internal class DatabaseBootstrapper(
    private val schemaRunner: DbSchemaRunner,
    private val dashboardMetricsRefresher: DashboardMetricsRefresher,
    private val transactionRunner: DbTransactionRunner,
) {
    fun bootstrap() {
        schemaRunner.applySchema()
        transactionRunner.inTransaction {
            dashboardMetricsRefresher.refreshTodayMetrics()
        }
    }
}
