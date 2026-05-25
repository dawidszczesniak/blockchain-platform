package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import pl.dawidszczesniak.blockchain_platform.db.CompetitionSettlementJobStatus
import pl.dawidszczesniak.blockchain_platform.db.CompetitionSettlementJobType
import pl.dawidszczesniak.blockchain_platform.db.CompetitionSettlementStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttemptStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttestationStatus
import pl.dawidszczesniak.blockchain_platform.db.SubmissionTestResultStatus
import pl.dawidszczesniak.blockchain_platform.db.ProblemLifecycleStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.CompetitionSettlementSnapshot
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.JoinProblemResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.NewProblemDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.OnchainJoinContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.PersistedSubmissionRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemExecutionContext
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemSettlementCandidate
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionNodeAttestationDraft
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionPersistedTestResult
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionRecordDraft

class CompetitionSettlementWorkerTest {
    @Test
    fun `registration deadline job completes outstanding jobs and waits for user-triggered cancellation`() {
        val now = Instant.parse("2026-05-03T00:00:10Z")
        val repository = FakeSettlementProblemWriteRepository(
            snapshot = CompetitionSettlementSnapshot(
                problemId = 1,
                competitionId = 11,
                prizeAmountAtomic = "1000",
                requiredParticipants = 2,
                registeredParticipants = 1,
                problemStatus = ProblemLifecycleStatus.Open.dbValue,
                settlementStatus = CompetitionSettlementStatus.Pending.dbValue,
            ),
        )
        val jobRepository = FakeCompetitionSettlementJobRepository(
            dueJobs = ArrayDeque(
                listOf(
                    CompetitionSettlementJobRecord(
                        jobId = 100,
                        problemId = 1,
                        competitionId = 11,
                        jobType = CompetitionSettlementJobType.RegistrationDeadline,
                        status = CompetitionSettlementJobStatus.Running,
                        attempts = 1,
                        runAt = now,
                        availableAt = now,
                        lockedAt = now,
                        statusMessage = null,
                        completedAt = null,
                        createdAt = now,
                    ),
                )
            ),
        )
        val worker = CompetitionSettlementWorker(
            repository = repository,
            jobRepository = jobRepository,
            wakeupSignal = CompetitionSettlementWakeupSignal(),
            nowProvider = { now },
        )

        worker.runOnce()

        assertEquals(1, jobRepository.completedOutstandingProblemId)
        assertNull(repository.pendingError)
        assertNull(repository.failedError)
        assertNull(jobRepository.rescheduledJobId)
    }

    @Test
    fun `failed registration deadline no longer reschedules worker because settlement is user-triggered`() {
        val now = Instant.parse("2026-05-03T00:00:10Z")
        val repository = FakeSettlementProblemWriteRepository(
            snapshot = CompetitionSettlementSnapshot(
                problemId = 1,
                competitionId = 11,
                prizeAmountAtomic = "1000",
                requiredParticipants = 2,
                registeredParticipants = 1,
                problemStatus = ProblemLifecycleStatus.Open.dbValue,
                settlementStatus = CompetitionSettlementStatus.Pending.dbValue,
            ),
        )
        val jobRepository = FakeCompetitionSettlementJobRepository(
            dueJobs = ArrayDeque(
                listOf(
                    CompetitionSettlementJobRecord(
                        jobId = 100,
                        problemId = 1,
                        competitionId = 11,
                        jobType = CompetitionSettlementJobType.RegistrationDeadline,
                        status = CompetitionSettlementJobStatus.Running,
                        attempts = 1,
                        runAt = now,
                        availableAt = now,
                        lockedAt = now,
                        statusMessage = null,
                        completedAt = null,
                        createdAt = now,
                    ),
                )
            ),
        )
        val worker = CompetitionSettlementWorker(
            repository = repository,
            jobRepository = jobRepository,
            wakeupSignal = CompetitionSettlementWakeupSignal(),
            nowProvider = { now },
        )

        worker.runOnce()

        assertNull(repository.pendingError)
        assertNull(repository.failedError)
        assertEquals(1, jobRepository.completedOutstandingProblemId)
        assertNull(jobRepository.rescheduledJobId)
        assertNull(jobRepository.rescheduledError)
        assertNull(jobRepository.rescheduledAt)
    }
}

private class FakeSettlementProblemWriteRepository(
    private val snapshot: CompetitionSettlementSnapshot?,
) : ProblemWriteRepository {
    var pendingError: String? = null
    var failedError: String? = null
    var cancelledProblemId: Int? = null
    var cancelledTxHash: String? = null

    override fun findProblemIdByOnchainCreationTxHash(txHash: String): Int? = error("Not used in this test.")
    override fun createProblemForUser(userId: Long, draft: NewProblemDraft): Int = error("Not used in this test.")
    override fun registerUserForProblem(userId: Long, problemId: Int): JoinProblemResult = error("Not used in this test.")
    override fun registerUserForProblemOnChain(
        userId: Long,
        problemId: Int,
        txHash: String,
        joinedAt: Instant,
        fromWallet: String,
    ): JoinProblemResult = error("Not used in this test.")
    override fun fetchOnchainJoinContext(problemId: Int): OnchainJoinContext = error("Not used in this test.")
    override fun fetchExecutionContextForUser(userId: Long, problemId: Int): ProblemExecutionContext = error("Not used in this test.")
    override fun createSubmissionRecord(draft: SubmissionRecordDraft): PersistedSubmissionRecord = error("Not used in this test.")
    override fun markSubmissionResultRecorded(
        submissionId: Long,
        proxyAddress: String,
        txHash: String,
        recordedAt: Instant,
        fromWallet: String,
    ) = error("Not used in this test.")
    override fun markSubmissionResultPendingConfirmation(
        submissionId: Long,
        proxyAddress: String,
        txHash: String,
        fromWallet: String,
    ) = error("Not used in this test.")
    override fun markSubmissionResultPendingError(
        submissionId: Long,
        error: String,
        txHash: String?,
    ) = error("Not used in this test.")
    override fun markSubmissionResultFailed(submissionId: Long, error: String) = error("Not used in this test.")
    override fun fetchSubmissionReceiptRetryContext(submissionId: Long) = error("Not used in this test.")
    override fun fetchSubmissionOnchainConfirmationContext(
        userId: Long,
        submissionId: Long,
    ) = error("Not used in this test.")

    override fun updateSubmissionAcceptedResultPayload(submissionId: Long, payloadJson: String) =
        error("Not used in this test.")

    override fun fetchCompetitionSettlementSnapshot(problemId: Int): CompetitionSettlementSnapshot? = snapshot
    override fun fetchCompetitionLifecycleContext(problemId: Int) = error("Not used in this test.")
    override fun fetchBestSettlementCandidate(problemId: Int): ProblemSettlementCandidate? = null
    override fun findUserIdByWalletAddress(walletAddress: String): Long? = error("Not used in this test.")
    override fun recordSettledWinner(
        problemId: Int,
        winnerUserId: Long,
        payoutAmountAtomic: String,
        txHash: String,
        settledAt: Instant,
        fromWallet: String,
    ) = error("Not used in this test.")

    override fun markCompetitionSettlementCancelled(problemId: Int, txHash: String, settledAt: Instant, fromWallet: String) {
        cancelledProblemId = problemId
        cancelledTxHash = txHash
    }

    override fun markCompetitionSettlementPendingError(problemId: Int, error: String) {
        pendingError = error
    }

    override fun markCompetitionSettlementFailed(problemId: Int, error: String) {
        failedError = error
    }
}

private class FakeCompetitionSettlementJobRepository(
    private val dueJobs: ArrayDeque<CompetitionSettlementJobRecord>,
) : CompetitionSettlementJobRepository {
    var completedOutstandingProblemId: Int? = null
    var rescheduledJobId: Long? = null
    var rescheduledError: String? = null
    var rescheduledAt: Instant? = null

    override fun scheduleForCompetition(problemId: Int, competitionId: Long, joinUntilDate: LocalDate, submitUntilDate: LocalDate, createdAt: Instant) =
        error("Not used in this test.")

    override fun seedMissingPendingJobs() = Unit

    override fun recoverStaleRunningJobs(staleBefore: Instant): Int = 0

    override fun reserveDueJob(now: Instant): CompetitionSettlementJobRecord? {
        return dueJobs.removeFirstOrNull()
    }

    override fun complete(jobId: Long, message: String?) = Unit

    override fun completeOutstandingJobs(problemId: Int, message: String?) {
        completedOutstandingProblemId = problemId
    }

    override fun reschedule(jobId: Long, error: String, nextAvailableAt: Instant) {
        rescheduledJobId = jobId
        rescheduledError = error
        rescheduledAt = nextAvailableAt
    }

    override fun markDead(jobId: Long, error: String) = Unit

    override fun nextAvailableAt(): Instant? = null

    override fun nextStaleRecoveryAt(staleLockThresholdMs: Long): Instant? = null
}
