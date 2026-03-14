package pl.dawidszczesniak.blockchain_platform.feature.auth.repository

import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.feature.auth.dao.UserDao

internal interface AuthRepository {
    fun loginByWallet(walletAddress: String): Long
    fun isUserPresent(userId: Long): Boolean
}

internal class AuthRepositoryImpl(
    private val userDao: UserDao,
    private val transactionRunner: DbTransactionRunner,
) : AuthRepository {
    override fun loginByWallet(walletAddress: String): Long {
        val normalized = walletAddress.trim().lowercase()
        return transactionRunner.inTransaction {
            val existing = userDao.fetchUserIdByWalletAddress(normalized)
            val userId = existing ?: createUserHandlingConcurrentInsert(normalized)
            userDao.touchUserLogin(userId)
            userId
        }
    }

    override fun isUserPresent(userId: Long): Boolean {
        return transactionRunner.inTransaction {
            userDao.fetchUserIdById(userId) != null
        }
    }

    private fun createUserHandlingConcurrentInsert(walletAddress: String): Long {
        return runCatching { userDao.insertUser(walletAddress) }.getOrElse {
            userDao.fetchUserIdByWalletAddress(walletAddress) ?: throw it
        }
    }
}
