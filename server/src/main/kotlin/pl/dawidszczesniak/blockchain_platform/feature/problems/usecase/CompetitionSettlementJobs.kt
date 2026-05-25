package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import pl.dawidszczesniak.blockchain_platform.db.CompetitionSettlementJobStatus
import pl.dawidszczesniak.blockchain_platform.db.CompetitionSettlementJobType
import pl.dawidszczesniak.blockchain_platform.db.CompetitionSettlementStatus
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.ProblemLifecycleStatus
import pl.dawidszczesniak.blockchain_platform.db.tables.CompetitionSettlementJobsTable
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemsTable
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.toContractDeadlineEpochSeconds
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.CompetitionSettlementSnapshot
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository

internal data class CompetitionSettlementJobRecord(
    val jobId: Long,
    val problemId: Int,
    val competitionId: Long,
    val jobType: CompetitionSettlementJobType,
    val status: CompetitionSettlementJobStatus,
    val attempts: Int,
    val runAt: Instant,
    val availableAt: Instant,
    val lockedAt: Instant?,
    val statusMessage: String?,
    val completedAt: Instant?,
    val createdAt: Instant,
)

internal interface CompetitionSettlementJobRepository {
    fun scheduleForCompetition(problemId: Int, competitionId: Long, joinUntilDate: LocalDate, submitUntilDate: LocalDate, createdAt: Instant)
    fun seedMissingPendingJobs()
    fun recoverStaleRunningJobs(staleBefore: Instant): Int
    fun reserveDueJob(now: Instant): CompetitionSettlementJobRecord?
    fun complete(jobId: Long, message: String? = null)
    fun completeOutstandingJobs(problemId: Int, message: String? = null)
    fun reschedule(jobId: Long, error: String, nextAvailableAt: Instant)
    fun markDead(jobId: Long, error: String)
    fun nextAvailableAt(): Instant?
    fun nextStaleRecoveryAt(staleLockThresholdMs: Long): Instant?
}

internal class CompetitionSettlementJobRepositoryImpl(
    private val transactionRunner: DbTransactionRunner,
    private val wakeupSignal: CompetitionSettlementWakeupSignal,
) : CompetitionSettlementJobRepository {
    override fun scheduleForCompetition(
        problemId: Int,
        competitionId: Long,
        joinUntilDate: LocalDate,
        submitUntilDate: LocalDate,
        createdAt: Instant,
    ) {
        transactionRunner.inTransaction {
            scheduleCompetitionSettlementJobsInCurrentTransaction(
                problemId = problemId.toLong(),
                competitionId = competitionId,
                joinUntilDate = joinUntilDate,
                submitUntilDate = submitUntilDate,
                createdAt = createdAt,
            )
        }
        wakeupSignal.notifyWorkScheduled()
    }

    override fun seedMissingPendingJobs() {
        transactionRunner.inTransaction {
            ProblemsTable
                .selectAll()
                .where {
                    (ProblemsTable.onchainCompetitionId.isNotNull()) and
                        (ProblemsTable.onchainSettlementStatus eq CompetitionSettlementStatus.Pending.dbValue)
                }
                .forEach { row ->
                    scheduleCompetitionSettlementJobsInCurrentTransaction(
                        problemId = row[ProblemsTable.problemId],
                        competitionId = row[ProblemsTable.onchainCompetitionId] ?: return@forEach,
                        joinUntilDate = row[ProblemsTable.joinUntilDate],
                        submitUntilDate = row[ProblemsTable.submitUntilDate],
                        createdAt = row[ProblemsTable.onchainCreationConfirmedAt]?.toInstant(ZoneOffset.UTC)
                            ?: row[ProblemsTable.createdAt].toInstant(ZoneOffset.UTC),
                    )
                }
        }
    }

    override fun recoverStaleRunningJobs(staleBefore: Instant): Int {
        return transactionRunner.inTransaction {
            CompetitionSettlementJobsTable.update(
                where = {
                    (CompetitionSettlementJobsTable.status eq CompetitionSettlementJobStatus.Running.dbValue) and
                        (CompetitionSettlementJobsTable.lockedAt lessEq staleBefore.toUtcDateTime())
                }
            ) {
                it[status] = CompetitionSettlementJobStatus.Scheduled.dbValue
                it[availableAt] = staleBefore.toUtcDateTime()
                it[lockedAt] = null
                it[statusMessage] = "Recovered after worker interruption."
            }
        }
    }

    override fun reserveDueJob(now: Instant): CompetitionSettlementJobRecord? {
        return transactionRunner.inTransaction {
            repeat(8) {
                val candidate = CompetitionSettlementJobsTable
                    .selectAll()
                    .where {
                        (CompetitionSettlementJobsTable.status eq CompetitionSettlementJobStatus.Scheduled.dbValue) and
                            (CompetitionSettlementJobsTable.availableAt lessEq now.toUtcDateTime())
                    }
                    .orderBy(
                        CompetitionSettlementJobsTable.availableAt to SortOrder.ASC,
                        CompetitionSettlementJobsTable.runAt to SortOrder.ASC,
                        CompetitionSettlementJobsTable.jobId to SortOrder.ASC,
                    )
                    .limit(1)
                    .singleOrNull()
                    ?.toCompetitionSettlementJobRecord()
                    ?: return@inTransaction null

                val runningNow = CompetitionSettlementJobsTable.update(
                    where = {
                        (CompetitionSettlementJobsTable.jobId eq candidate.jobId) and
                            (CompetitionSettlementJobsTable.status eq CompetitionSettlementJobStatus.Scheduled.dbValue)
                    }
                ) {
                    it[status] = CompetitionSettlementJobStatus.Running.dbValue
                    it[attempts] = candidate.attempts + 1
                    it[lockedAt] = now.toUtcDateTime()
                    it[statusMessage] = null
                } > 0

                if (runningNow) {
                    return@inTransaction candidate.copy(
                        status = CompetitionSettlementJobStatus.Running,
                        attempts = candidate.attempts + 1,
                        lockedAt = now,
                        statusMessage = null,
                    )
                }
            }
            null
        }
    }

    override fun complete(jobId: Long, message: String?) {
        transactionRunner.inTransaction {
            CompetitionSettlementJobsTable.update(
                where = { CompetitionSettlementJobsTable.jobId eq jobId }
            ) {
                it[status] = CompetitionSettlementJobStatus.Completed.dbValue
                it[lockedAt] = null
                it[statusMessage] = message
                it[completedAt] = Instant.now().toUtcDateTime()
            }
        }
    }

    override fun completeOutstandingJobs(problemId: Int, message: String?) {
        transactionRunner.inTransaction {
            CompetitionSettlementJobsTable.update(
                where = {
                    (CompetitionSettlementJobsTable.problemId eq problemId.toLong()) and
                        (CompetitionSettlementJobsTable.status neq CompetitionSettlementJobStatus.Completed.dbValue) and
                        (CompetitionSettlementJobsTable.status neq CompetitionSettlementJobStatus.Dead.dbValue)
                }
            ) {
                it[status] = CompetitionSettlementJobStatus.Completed.dbValue
                it[lockedAt] = null
                it[statusMessage] = message
                it[completedAt] = Instant.now().toUtcDateTime()
            }
        }
    }

    override fun reschedule(jobId: Long, error: String, nextAvailableAt: Instant) {
        transactionRunner.inTransaction {
            CompetitionSettlementJobsTable.update(
                where = { CompetitionSettlementJobsTable.jobId eq jobId }
            ) {
                it[status] = CompetitionSettlementJobStatus.Scheduled.dbValue
                it[lockedAt] = null
                it[availableAt] = nextAvailableAt.toUtcDateTime()
                it[statusMessage] = error
            }
        }
    }

    override fun markDead(jobId: Long, error: String) {
        transactionRunner.inTransaction {
            CompetitionSettlementJobsTable.update(
                where = { CompetitionSettlementJobsTable.jobId eq jobId }
            ) {
                it[status] = CompetitionSettlementJobStatus.Dead.dbValue
                it[lockedAt] = null
                it[statusMessage] = error
                it[completedAt] = Instant.now().toUtcDateTime()
            }
        }
    }

    override fun nextAvailableAt(): Instant? {
        return transactionRunner.inTransaction {
            CompetitionSettlementJobsTable
                .selectAll()
                .where { CompetitionSettlementJobsTable.status eq CompetitionSettlementJobStatus.Scheduled.dbValue }
                .orderBy(CompetitionSettlementJobsTable.availableAt to SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.get(CompetitionSettlementJobsTable.availableAt)
                ?.toInstant(ZoneOffset.UTC)
        }
    }

    override fun nextStaleRecoveryAt(staleLockThresholdMs: Long): Instant? {
        return transactionRunner.inTransaction {
            CompetitionSettlementJobsTable
                .selectAll()
                .where { CompetitionSettlementJobsTable.status eq CompetitionSettlementJobStatus.Running.dbValue }
                .orderBy(CompetitionSettlementJobsTable.lockedAt to SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.get(CompetitionSettlementJobsTable.lockedAt)
                ?.toInstant(ZoneOffset.UTC)
                ?.plusMillis(staleLockThresholdMs)
        }
    }
}

internal class CompetitionSettlementWakeupSignal {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    fun notifyWorkScheduled() {
        channel.trySend(Unit)
    }

    suspend fun awaitUntil(now: Instant, wakeAt: Instant?) {
        if (wakeAt == null) {
            channel.receive()
            return
        }
        val delayMs = Duration.between(now, wakeAt).toMillis()
        if (delayMs <= 0L) {
            return
        }
        withTimeoutOrNull(delayMs) {
            channel.receive()
        }
    }
}

internal class CompetitionSettlementWorker(
    private val repository: ProblemWriteRepository,
    private val jobRepository: CompetitionSettlementJobRepository,
    private val wakeupSignal: CompetitionSettlementWakeupSignal,
    private val nowProvider: () -> Instant = { Instant.now() },
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        jobRepository.seedMissingPendingJobs()
        jobRepository.recoverStaleRunningJobs(nowProvider().minusMillis(staleLockThresholdMs()))
        wakeupSignal.notifyWorkScheduled()
        scope.launch {
            while (isActive) {
                runCatching { processDueJobs() }
                    .onFailure { error ->
                        System.err.println("CompetitionSettlementWorker failed: ${error.message}")
                    }
                waitForNextWakeUp()
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    internal fun runOnce() {
        processDueJobs()
    }

    private fun processDueJobs() {
        jobRepository.recoverStaleRunningJobs(nowProvider().minusMillis(staleLockThresholdMs()))
        while (true) {
            val now = nowProvider()
            val job = jobRepository.reserveDueJob(now) ?: return
            process(job, now)
        }
    }

    private fun process(job: CompetitionSettlementJobRecord, now: Instant) {
        val snapshot = repository.fetchCompetitionSettlementSnapshot(job.problemId)
        if (snapshot == null) {
            jobRepository.complete(job.jobId, "Competition record no longer exists.")
            return
        }
        if (snapshot.problemStatus != ProblemLifecycleStatus.Open.dbValue ||
            snapshot.settlementStatus != CompetitionSettlementStatus.Pending.dbValue
        ) {
            jobRepository.completeOutstandingJobs(snapshot.problemId, "Competition already finalized.")
            return
        }

        when (job.jobType) {
            CompetitionSettlementJobType.RegistrationDeadline -> processRegistrationDeadline(job, snapshot)
            CompetitionSettlementJobType.SubmissionDeadline -> processSubmissionDeadline(job, snapshot)
        }
    }

    private fun processRegistrationDeadline(
        job: CompetitionSettlementJobRecord,
        snapshot: CompetitionSettlementSnapshot,
    ) {
        if (snapshot.registeredParticipants >= snapshot.requiredParticipants) {
            jobRepository.complete(job.jobId, "Registration threshold reached.")
            return
        }
        jobRepository.completeOutstandingJobs(
            snapshot.problemId,
            "Registration deadline reached. Awaiting user-triggered competition cancellation.",
        )
    }

    private fun processSubmissionDeadline(
        job: CompetitionSettlementJobRecord,
        snapshot: CompetitionSettlementSnapshot,
    ) {
        if (snapshot.registeredParticipants < snapshot.requiredParticipants) {
            jobRepository.completeOutstandingJobs(
                snapshot.problemId,
                "Submission deadline reached without required participants. Awaiting user-triggered competition cancellation.",
            )
            return
        }

        val bestCandidate = repository.fetchBestSettlementCandidate(snapshot.problemId)
        if (bestCandidate == null) {
            jobRepository.completeOutstandingJobs(
                snapshot.problemId,
                "Submission deadline reached without a valid winner. Awaiting user-triggered competition cancellation.",
            )
            return
        }

        jobRepository.completeOutstandingJobs(
            snapshot.problemId,
            "Submission deadline reached. Awaiting user-triggered competition settlement.",
        )
    }

    private suspend fun waitForNextWakeUp() {
        val now = nowProvider()
        wakeupSignal.awaitUntil(
            now = now,
            wakeAt = nextWakeUpAt(now),
        )
    }

    private fun nextWakeUpAt(now: Instant): Instant? {
        val nextScheduledJobAt = jobRepository.nextAvailableAt()
        val nextRecoveryAt = jobRepository.nextStaleRecoveryAt(staleLockThresholdMs())
        return listOfNotNull(nextScheduledJobAt, nextRecoveryAt)
            .minOrNull()
            ?.takeIf { !it.isBefore(now) }
    }

    private fun staleLockThresholdMs(): Long {
        return 180_000L
    }

}

internal fun scheduleCompetitionSettlementJobsInCurrentTransaction(
    problemId: Long,
    competitionId: Long,
    joinUntilDate: LocalDate,
    submitUntilDate: LocalDate,
    createdAt: Instant,
) {
    insertCompetitionSettlementJobIfMissing(
        problemId = problemId,
        competitionId = competitionId,
        jobType = CompetitionSettlementJobType.RegistrationDeadline,
        runAt = Instant.ofEpochSecond(joinUntilDate.toContractDeadlineEpochSeconds() + 1),
        createdAt = createdAt,
    )
    insertCompetitionSettlementJobIfMissing(
        problemId = problemId,
        competitionId = competitionId,
        jobType = CompetitionSettlementJobType.SubmissionDeadline,
        runAt = Instant.ofEpochSecond(submitUntilDate.toContractDeadlineEpochSeconds() + 1),
        createdAt = createdAt,
    )
}

private fun insertCompetitionSettlementJobIfMissing(
    problemId: Long,
    competitionId: Long,
    jobType: CompetitionSettlementJobType,
    runAt: Instant,
    createdAt: Instant,
) {
    CompetitionSettlementJobsTable.insertIgnore {
        it[CompetitionSettlementJobsTable.problemId] = problemId
        it[CompetitionSettlementJobsTable.competitionId] = competitionId
        it[CompetitionSettlementJobsTable.jobType] = jobType.dbValue
        it[CompetitionSettlementJobsTable.status] = CompetitionSettlementJobStatus.Scheduled.dbValue
        it[CompetitionSettlementJobsTable.attempts] = 0
        it[CompetitionSettlementJobsTable.runAt] = runAt.toUtcDateTime()
        it[CompetitionSettlementJobsTable.availableAt] = runAt.toUtcDateTime()
        it[CompetitionSettlementJobsTable.createdAt] = createdAt.toUtcDateTime()
    }
}

private fun ResultRow.toCompetitionSettlementJobRecord(): CompetitionSettlementJobRecord {
    return CompetitionSettlementJobRecord(
        jobId = this[CompetitionSettlementJobsTable.jobId],
        problemId = this[CompetitionSettlementJobsTable.problemId].toInt(),
        competitionId = this[CompetitionSettlementJobsTable.competitionId],
        jobType = CompetitionSettlementJobType.entries.first {
            it.dbValue == this[CompetitionSettlementJobsTable.jobType]
        },
        status = CompetitionSettlementJobStatus.entries.first {
            it.dbValue == this[CompetitionSettlementJobsTable.status]
        },
        attempts = this[CompetitionSettlementJobsTable.attempts],
        runAt = this[CompetitionSettlementJobsTable.runAt].toInstant(ZoneOffset.UTC),
        availableAt = this[CompetitionSettlementJobsTable.availableAt].toInstant(ZoneOffset.UTC),
        lockedAt = this[CompetitionSettlementJobsTable.lockedAt]?.toInstant(ZoneOffset.UTC),
        statusMessage = this[CompetitionSettlementJobsTable.statusMessage],
        completedAt = this[CompetitionSettlementJobsTable.completedAt]?.toInstant(ZoneOffset.UTC),
        createdAt = this[CompetitionSettlementJobsTable.createdAt].toInstant(ZoneOffset.UTC),
    )
}

internal fun Instant.toUtcDateTime(): LocalDateTime {
    return LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
