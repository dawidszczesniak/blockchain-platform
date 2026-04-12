package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmissionJudgeJobDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface SubmitProblemCodeUseCase {
    suspend operator fun invoke(problemId: Int, sourceCode: String, language: String): SubmissionJudgeJobDto
}

interface GetSubmissionJudgeJobUseCase {
    suspend operator fun invoke(jobId: Long): SubmissionJudgeJobDto
}

class SubmitProblemCodeUseCaseImpl(
    private val repository: ProblemRepository,
) : SubmitProblemCodeUseCase {
    override suspend fun invoke(problemId: Int, sourceCode: String, language: String): SubmissionJudgeJobDto {
        return repository.submitProblemCode(
            problemId = problemId,
            sourceCode = sourceCode,
            language = language,
        )
    }
}

class GetSubmissionJudgeJobUseCaseImpl(
    private val repository: ProblemRepository,
) : GetSubmissionJudgeJobUseCase {
    override suspend fun invoke(jobId: Long): SubmissionJudgeJobDto {
        return repository.fetchSubmissionJudgeJob(jobId)
    }
}
