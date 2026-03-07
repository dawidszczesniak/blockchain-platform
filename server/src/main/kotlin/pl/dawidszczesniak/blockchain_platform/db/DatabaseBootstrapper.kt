package pl.dawidszczesniak.blockchain_platform.db

internal class DatabaseBootstrapper(
    private val schemaRunner: DbSchemaRunner,
    private val seeder: DbSeeder,
    private val dashboardMetricsRefresher: DashboardMetricsRefresher,
) {
    fun bootstrap() {
        schemaRunner.applySchema()
        seeder.seedIfEmpty()
        dashboardMetricsRefresher.refreshTodayMetrics()
    }
}
