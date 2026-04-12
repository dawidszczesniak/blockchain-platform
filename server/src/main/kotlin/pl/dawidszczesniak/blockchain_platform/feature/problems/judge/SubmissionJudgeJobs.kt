package pl.dawidszczesniak.blockchain_platform.feature.problems.judge

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import redis.clients.jedis.JedisPooled
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.db.SubmissionJudgeJobStatus
import pl.dawidszczesniak.blockchain_platform.db.tables.ProblemSubmissionJudgeJobsTable
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmissionJudgeOutcome
import pl.dawidszczesniak.blockchain_platform.feature.problems.usecase.SubmissionJudgeService

internal data class SubmissionJudgeJobRecord(
    val jobId: Long,
    val problemId: Int,
    val userId: Long,
    val sourceCode: String,
    val language: String,
    val status: SubmissionJudgeJobStatus,
    val statusMessage: String?,
    val resultPayloadJson: String?,
    val previewPayloadJson: String?,
    val submissionId: Long?,
    val requestedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
)

internal interface SubmissionJudgeJobRepository {
    fun create(problemId: Int, userId: Long, sourceCode: String, language: String): SubmissionJudgeJobRecord
    fun get(jobId: Long): SubmissionJudgeJobRecord?
    fun getForUser(jobId: Long, userId: Long): SubmissionJudgeJobRecord?
    fun markRunning(jobId: Long): Boolean
    fun completeAccepted(jobId: Long, submissionId: Long, payloadJson: String)
    fun completeRejected(jobId: Long, message: String, previewJson: String)
    fun completeError(jobId: Long, message: String)
    fun recoverQueuedJobIds(): List<Long>
}

internal class SubmissionJudgeJobRepositoryImpl(
    private val transactionRunner: DbTransactionRunner,
) : SubmissionJudgeJobRepository {
    override fun create(problemId: Int, userId: Long, sourceCode: String, language: String): SubmissionJudgeJobRecord {
        val now = Instant.now()
        return transactionRunner.inTransaction {
            val inserted = ProblemSubmissionJudgeJobsTable.insert {
                it[ProblemSubmissionJudgeJobsTable.problemId] = problemId.toLong()
                it[ProblemSubmissionJudgeJobsTable.userId] = userId
                it[ProblemSubmissionJudgeJobsTable.sourceCode] = sourceCode
                it[ProblemSubmissionJudgeJobsTable.language] = language
                it[status] = SubmissionJudgeJobStatus.Queued.dbValue
                it[requestedAt] = now.toDbDateTime()
            }
            SubmissionJudgeJobRecord(
                jobId = inserted[ProblemSubmissionJudgeJobsTable.jobId],
                problemId = problemId,
                userId = userId,
                sourceCode = sourceCode,
                language = language,
                status = SubmissionJudgeJobStatus.Queued,
                statusMessage = null,
                resultPayloadJson = null,
                previewPayloadJson = null,
                submissionId = null,
                requestedAt = now,
                startedAt = null,
                completedAt = null,
            )
        }
    }

    override fun get(jobId: Long): SubmissionJudgeJobRecord? {
        return transactionRunner.inTransaction {
            ProblemSubmissionJudgeJobsTable
                .selectAll()
                .where { ProblemSubmissionJudgeJobsTable.jobId eq jobId }
                .singleOrNull()
                ?.toRecord()
        }
    }

    override fun getForUser(jobId: Long, userId: Long): SubmissionJudgeJobRecord? {
        return transactionRunner.inTransaction {
            ProblemSubmissionJudgeJobsTable
                .selectAll()
                .where {
                    (ProblemSubmissionJudgeJobsTable.jobId eq jobId) and
                        (ProblemSubmissionJudgeJobsTable.userId eq userId)
                }
                .singleOrNull()
                ?.toRecord()
        }
    }

    override fun markRunning(jobId: Long): Boolean {
        val now = Instant.now().toDbDateTime()
        return transactionRunner.inTransaction {
            ProblemSubmissionJudgeJobsTable.update(
                where = {
                    (ProblemSubmissionJudgeJobsTable.jobId eq jobId) and
                        (ProblemSubmissionJudgeJobsTable.status eq SubmissionJudgeJobStatus.Queued.dbValue)
                }
            ) {
                it[status] = SubmissionJudgeJobStatus.Running.dbValue
                it[startedAt] = now
                it[statusMessage] = null
            } > 0
        }
    }

    override fun completeAccepted(jobId: Long, submissionId: Long, payloadJson: String) {
        val completedAt = Instant.now().toDbDateTime()
        transactionRunner.inTransaction {
            ProblemSubmissionJudgeJobsTable.update(
                where = { ProblemSubmissionJudgeJobsTable.jobId eq jobId }
            ) {
                it[status] = SubmissionJudgeJobStatus.Accepted.dbValue
                it[ProblemSubmissionJudgeJobsTable.submissionId] = submissionId
                it[resultPayloadJson] = payloadJson
                it[previewPayloadJson] = null
                it[statusMessage] = null
                it[ProblemSubmissionJudgeJobsTable.completedAt] = completedAt
            }
        }
    }

    override fun completeRejected(jobId: Long, message: String, previewJson: String) {
        val completedAt = Instant.now().toDbDateTime()
        transactionRunner.inTransaction {
            ProblemSubmissionJudgeJobsTable.update(
                where = { ProblemSubmissionJudgeJobsTable.jobId eq jobId }
            ) {
                it[status] = SubmissionJudgeJobStatus.Rejected.dbValue
                it[statusMessage] = message
                it[previewPayloadJson] = previewJson
                it[resultPayloadJson] = null
                it[ProblemSubmissionJudgeJobsTable.completedAt] = completedAt
            }
        }
    }

    override fun completeError(jobId: Long, message: String) {
        val completedAt = Instant.now().toDbDateTime()
        transactionRunner.inTransaction {
            ProblemSubmissionJudgeJobsTable.update(
                where = { ProblemSubmissionJudgeJobsTable.jobId eq jobId }
            ) {
                it[status] = SubmissionJudgeJobStatus.Error.dbValue
                it[statusMessage] = message
                it[previewPayloadJson] = null
                it[resultPayloadJson] = null
                it[ProblemSubmissionJudgeJobsTable.completedAt] = completedAt
            }
        }
    }

    override fun recoverQueuedJobIds(): List<Long> {
        return transactionRunner.inTransaction {
            ProblemSubmissionJudgeJobsTable.update(
                where = { ProblemSubmissionJudgeJobsTable.status eq SubmissionJudgeJobStatus.Running.dbValue }
            ) {
                it[status] = SubmissionJudgeJobStatus.Queued.dbValue
                it[startedAt] = null
                it[statusMessage] = "Job recovered after worker restart."
            }
            ProblemSubmissionJudgeJobsTable
                .selectAll()
                .where { ProblemSubmissionJudgeJobsTable.status eq SubmissionJudgeJobStatus.Queued.dbValue }
                .orderBy(ProblemSubmissionJudgeJobsTable.requestedAt to SortOrder.ASC)
                .map { row -> row[ProblemSubmissionJudgeJobsTable.jobId] }
        }
    }
}

internal interface SubmissionJudgeQueue {
    fun enqueue(jobId: Long)
    fun enqueueAll(jobIds: List<Long>)
    fun reserve(timeoutSeconds: Int = 2): Long?
    fun position(jobId: Long): Int?
}

internal class RedisSubmissionJudgeQueue(
    private val redis: JedisPooled,
) : SubmissionJudgeQueue {
    override fun enqueue(jobId: Long) {
        redis.rpush(QUEUE_KEY, jobId.toString())
    }

    override fun enqueueAll(jobIds: List<Long>) {
        if (jobIds.isEmpty()) {
            return
        }
        redis.rpush(QUEUE_KEY, *jobIds.map(Long::toString).toTypedArray())
    }

    override fun reserve(timeoutSeconds: Int): Long? {
        val result = redis.blpop(timeoutSeconds, QUEUE_KEY) ?: return null
        return result.getOrNull(1)?.toLongOrNull()
    }

    override fun position(jobId: Long): Int? {
        val items = redis.lrange(QUEUE_KEY, 0, -1)
        val index = items.indexOf(jobId.toString())
        return if (index >= 0) index + 1 else null
    }

    private companion object {
        const val QUEUE_KEY = "judge:submission:queue"
    }
}

internal class SubmissionJudgeWorker(
    private val queue: SubmissionJudgeQueue,
    private val repository: SubmissionJudgeJobRepository,
    private val judgeService: SubmissionJudgeService,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    fun start() {
        queue.enqueueAll(repository.recoverQueuedJobIds())
        scope.launch {
            while (isActive) {
                val jobId = runCatching { queue.reserve() }
                    .getOrElse {
                        delay(1_000)
                        continue
                    }
                    ?: continue
                process(jobId)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    private fun process(jobId: Long) {
        if (!repository.markRunning(jobId)) {
            return
        }
        val job = repository.get(jobId)
        if (job == null) {
            repository.completeError(jobId, "Judge job no longer exists.")
            return
        }
        val request = RunProblemRequestDto(
            sourceCode = job.sourceCode,
            language = job.language,
        )
        runCatching {
            judgeService.judge(
                userId = job.userId,
                problemId = job.problemId,
                request = request,
            )
        }.onSuccess { outcome ->
            when (outcome) {
                is SubmissionJudgeOutcome.Accepted -> {
                    repository.completeAccepted(
                        jobId = jobId,
                        submissionId = outcome.response.submissionId,
                        payloadJson = json.encodeToString(SubmitProblemResponseDto.serializer(), outcome.response),
                    )
                }

                is SubmissionJudgeOutcome.Rejected -> {
                    repository.completeRejected(
                        jobId = jobId,
                        message = outcome.message,
                        previewJson = json.encodeToString(RunProblemResponseDto.serializer(), outcome.preview),
                    )
                }
            }
        }.onFailure { error ->
            repository.completeError(
                jobId = jobId,
                message = error.message?.ifBlank { null } ?: "Judge worker failed.",
            )
        }
    }
}

internal class SubmissionJudgeJobMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toDto(record: SubmissionJudgeJobRecord, queuePosition: Int? = null): SubmissionJudgeJobDto {
        val preview = record.previewPayloadJson?.takeIf { it.isNotBlank() }?.let { payload ->
            json.decodeFromString(RunProblemResponseDto.serializer(), payload)
        }
        val result = record.resultPayloadJson?.takeIf { it.isNotBlank() }?.let { payload ->
            json.decodeFromString(SubmitProblemResponseDto.serializer(), payload)
        }
        return SubmissionJudgeJobDto(
            jobId = record.jobId,
            status = record.status.name,
            language = record.language,
            queuePosition = queuePosition,
            message = record.statusMessage,
            submissionId = record.submissionId,
            runPreview = preview,
            submissionResult = result,
        )
    }
}

private fun ResultRow.toRecord(): SubmissionJudgeJobRecord {
    return SubmissionJudgeJobRecord(
        jobId = this[ProblemSubmissionJudgeJobsTable.jobId],
        problemId = this[ProblemSubmissionJudgeJobsTable.problemId].toInt(),
        userId = this[ProblemSubmissionJudgeJobsTable.userId],
        sourceCode = this[ProblemSubmissionJudgeJobsTable.sourceCode],
        language = this[ProblemSubmissionJudgeJobsTable.language],
        status = SubmissionJudgeJobStatus.entries.first { it.dbValue == this[ProblemSubmissionJudgeJobsTable.status] },
        statusMessage = this[ProblemSubmissionJudgeJobsTable.statusMessage],
        resultPayloadJson = this[ProblemSubmissionJudgeJobsTable.resultPayloadJson],
        previewPayloadJson = this[ProblemSubmissionJudgeJobsTable.previewPayloadJson],
        submissionId = this[ProblemSubmissionJudgeJobsTable.submissionId],
        requestedAt = this[ProblemSubmissionJudgeJobsTable.requestedAt].toInstant(ZoneOffset.UTC),
        startedAt = this[ProblemSubmissionJudgeJobsTable.startedAt]?.toInstant(ZoneOffset.UTC),
        completedAt = this[ProblemSubmissionJudgeJobsTable.completedAt]?.toInstant(ZoneOffset.UTC),
    )
}

private fun Instant.toDbDateTime(): LocalDateTime {
    return LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
