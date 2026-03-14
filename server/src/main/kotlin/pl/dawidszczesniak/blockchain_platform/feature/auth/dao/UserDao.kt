package pl.dawidszczesniak.blockchain_platform.feature.auth.dao

import java.time.LocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import pl.dawidszczesniak.blockchain_platform.db.tables.UsersTable

internal interface UserDao {
    fun fetchUserIdByWalletAddress(walletAddress: String): Long?
    fun insertUser(walletAddress: String): Long
    fun touchUserLogin(userId: Long)
    fun fetchUserIdById(userId: Long): Long?
}

internal class UserDaoImpl : UserDao {
    override fun fetchUserIdByWalletAddress(walletAddress: String): Long? {
        return UsersTable
            .selectAll()
            .where { UsersTable.walletAddress eq walletAddress }
            .orderBy(UsersTable.userId to SortOrder.ASC)
            .limit(1)
            .firstOrNull()
            ?.get(UsersTable.userId)
    }

    override fun insertUser(walletAddress: String): Long {
        val inserted = UsersTable.insert {
            it[UsersTable.walletAddress] = walletAddress
        }
        return inserted[UsersTable.userId]
    }

    override fun touchUserLogin(userId: Long) {
        UsersTable.update({ UsersTable.userId eq userId }) {
            it[UsersTable.lastLoginAt] = LocalDateTime.now()
        }
    }

    override fun fetchUserIdById(userId: Long): Long? {
        return UsersTable
            .selectAll()
            .where { UsersTable.userId eq userId }
            .limit(1)
            .firstOrNull()
            ?.get(UsersTable.userId)
    }
}
