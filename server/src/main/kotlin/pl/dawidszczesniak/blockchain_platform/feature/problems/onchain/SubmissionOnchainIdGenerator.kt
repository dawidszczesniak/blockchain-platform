package pl.dawidszczesniak.blockchain_platform.feature.problems.onchain

import java.util.concurrent.atomic.AtomicLong

internal fun generateOnchainSubmissionId(nowMillis: Long = System.currentTimeMillis()): Long {
    val base = nowMillis.coerceAtLeast(1L) * ONCHAIN_SUBMISSION_ID_TIME_MULTIPLIER
    while (true) {
        val previous = lastGeneratedOnchainSubmissionId.get()
        val candidate = maxOf(base, previous + 1L)
        check(candidate > 0L) { "Generated on-chain submission id must stay positive." }
        if (lastGeneratedOnchainSubmissionId.compareAndSet(previous, candidate)) {
            return candidate
        }
    }
}

internal const val ONCHAIN_SUBMISSION_ID_TIME_MULTIPLIER = 1_048_576L

private val lastGeneratedOnchainSubmissionId = AtomicLong(0L)
