package pl.dawidszczesniak.blockchain_platform.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

internal interface DbTransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}

internal class ExposedDbTransactionRunner(
    private val database: Database,
) : DbTransactionRunner {
    override fun <T> inTransaction(block: () -> T): T {
        return transaction(database) {
            block()
        }
    }
}
