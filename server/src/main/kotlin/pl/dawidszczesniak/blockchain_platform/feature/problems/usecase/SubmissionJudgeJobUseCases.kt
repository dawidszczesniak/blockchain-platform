package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.RunProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.JudgeLanguages
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.SubmissionJudgeJobMapper
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.SubmissionJudgeJobRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.judge.SubmissionJudgeQueue
import pl.dawidszczesniak.blockchain_platform.db.SubmissionJudgeJobStatus

internal class SubmissionJudgeJobValidationException(
    message: String,
) : IllegalArgumentException(message)

internal interface EnqueueProblemSubmissionUseCase {
    operator fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmissionJudgeJobDto
}

internal interface GetSubmissionJudgeJobUseCase {
    operator fun invoke(userId: Long, jobId: Long): SubmissionJudgeJobDto
}

internal class EnqueueProblemSubmissionUseCaseImpl(
    private val repository: SubmissionJudgeJobRepository,
    private val queue: SubmissionJudgeQueue,
    private val mapper: SubmissionJudgeJobMapper,
) : EnqueueProblemSubmissionUseCase {
    override fun invoke(userId: Long, problemId: Int, request: RunProblemRequestDto): SubmissionJudgeJobDto {
        val language = runCatching { JudgeLanguages.requireSupported(request.language).id }
            .getOrElse { error ->
                throw SubmissionJudgeJobValidationException(error.message ?: "Unsupported language.")
            }
        val sourceCode = request.sourceCode.trim()
        if (sourceCode.isBlank()) {
            throw SubmissionJudgeJobValidationException("Source code cannot be empty.")
        }
        if (sourceCode.length > MAX_SOURCE_CODE_CHARS) {
            throw SubmissionJudgeJobValidationException(
                "Source code is too long. Max length is $MAX_SOURCE_CODE_CHARS characters."
            )
        }
        val job = repository.create(
            problemId = problemId,
            userId = userId,
            sourceCode = sourceCode,
            language = language,
        )
        queue.enqueue(job.jobId)
        return mapper.toDto(
            record = job,
            queuePosition = queue.position(job.jobId),
        )
    }
}

internal class GetSubmissionJudgeJobUseCaseImpl(
    private val repository: SubmissionJudgeJobRepository,
    private val queue: SubmissionJudgeQueue,
    private val mapper: SubmissionJudgeJobMapper,
) : GetSubmissionJudgeJobUseCase {
    override fun invoke(userId: Long, jobId: Long): SubmissionJudgeJobDto {
        val record = repository.getForUser(jobId, userId)
            ?: throw SubmissionJudgeJobValidationException("Submission judge job not found.")
        return mapper.toDto(
            record = record,
            queuePosition = if (record.status == SubmissionJudgeJobStatus.Queued) {
                queue.position(jobId)
            } else {
                null
            },
        )
    }
}

private const val MAX_SOURCE_CODE_CHARS = 120_000
