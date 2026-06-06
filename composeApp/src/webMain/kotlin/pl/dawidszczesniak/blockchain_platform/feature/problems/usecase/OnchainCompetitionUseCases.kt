package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCompetitionLifecycleActionRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CompetitionLifecycleActionResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCompetitionLifecycleActionResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareJoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemRepository

interface PrepareCreateProblemOnChainUseCase {
    suspend operator fun invoke(request: CreateProblemRequestDto): PrepareCreateProblemResponseDto
}

interface ConfirmCreateProblemOnChainUseCase {
    suspend operator fun invoke(intentId: String, txHash: String): Int
}

interface PrepareJoinProblemOnChainUseCase {
    suspend operator fun invoke(problemId: Int): PrepareJoinProblemResponseDto
}

interface ConfirmJoinProblemOnChainUseCase {
    suspend operator fun invoke(problemId: Int, intentId: String, txHash: String): JoinProblemResponseDto
}

interface PrepareSettleCompetitionOnChainUseCase {
    suspend operator fun invoke(problemId: Int): PrepareCompetitionLifecycleActionResponseDto
}

interface ConfirmSettleCompetitionOnChainUseCase {
    suspend operator fun invoke(problemId: Int, txHash: String): CompetitionLifecycleActionResponseDto
}

interface PrepareCancelCompetitionOnChainUseCase {
    suspend operator fun invoke(problemId: Int): PrepareCompetitionLifecycleActionResponseDto
}

interface ConfirmCancelCompetitionOnChainUseCase {
    suspend operator fun invoke(problemId: Int, txHash: String): CompetitionLifecycleActionResponseDto
}

class PrepareCreateProblemOnChainUseCaseImpl(
    private val repository: ProblemRepository,
) : PrepareCreateProblemOnChainUseCase {
    override suspend fun invoke(request: CreateProblemRequestDto): PrepareCreateProblemResponseDto {
        return repository.prepareCreateProblemOnChain(request)
    }
}

class ConfirmCreateProblemOnChainUseCaseImpl(
    private val repository: ProblemRepository,
) : ConfirmCreateProblemOnChainUseCase {
    override suspend fun invoke(intentId: String, txHash: String): Int {
        return repository.confirmCreateProblemOnChain(
            ConfirmCreateProblemRequestDto(
                intentId = intentId,
                txHash = txHash,
            )
        )
    }
}

class PrepareJoinProblemOnChainUseCaseImpl(
    private val repository: ProblemRepository,
) : PrepareJoinProblemOnChainUseCase {
    override suspend fun invoke(problemId: Int): PrepareJoinProblemResponseDto {
        return repository.prepareJoinProblemOnChain(problemId)
    }
}

class ConfirmJoinProblemOnChainUseCaseImpl(
    private val repository: ProblemRepository,
) : ConfirmJoinProblemOnChainUseCase {
    override suspend fun invoke(problemId: Int, intentId: String, txHash: String): JoinProblemResponseDto {
        return repository.confirmJoinProblemOnChain(
            problemId = problemId,
            request = ConfirmJoinProblemRequestDto(
                intentId = intentId,
                txHash = txHash,
            ),
        )
    }
}

class PrepareSettleCompetitionOnChainUseCaseImpl(
    private val repository: ProblemRepository,
) : PrepareSettleCompetitionOnChainUseCase {
    override suspend fun invoke(problemId: Int): PrepareCompetitionLifecycleActionResponseDto {
        return repository.prepareSettleCompetitionOnChain(problemId)
    }
}

class ConfirmSettleCompetitionOnChainUseCaseImpl(
    private val repository: ProblemRepository,
) : ConfirmSettleCompetitionOnChainUseCase {
    override suspend fun invoke(problemId: Int, txHash: String): CompetitionLifecycleActionResponseDto {
        return repository.confirmSettleCompetitionOnChain(
            problemId = problemId,
            request = ConfirmCompetitionLifecycleActionRequestDto(txHash = txHash),
        )
    }
}

class PrepareCancelCompetitionOnChainUseCaseImpl(
    private val repository: ProblemRepository,
) : PrepareCancelCompetitionOnChainUseCase {
    override suspend fun invoke(problemId: Int): PrepareCompetitionLifecycleActionResponseDto {
        return repository.prepareCancelCompetitionOnChain(problemId)
    }
}

class ConfirmCancelCompetitionOnChainUseCaseImpl(
    private val repository: ProblemRepository,
) : ConfirmCancelCompetitionOnChainUseCase {
    override suspend fun invoke(problemId: Int, txHash: String): CompetitionLifecycleActionResponseDto {
        return repository.confirmCancelCompetitionOnChain(
            problemId = problemId,
            request = ConfirmCompetitionLifecycleActionRequestDto(txHash = txHash),
        )
    }
}
