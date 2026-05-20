package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.Instant
import java.time.LocalDate
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.toContractDeadlineEpochSeconds
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.CompetitionIntentStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PreparedWalletTransactionDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareJoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemWriteRepository

internal interface PrepareCreateProblemOnChainUseCase {
    operator fun invoke(userId: Long, walletAddress: String, request: CreateProblemRequestDto): PrepareCreateProblemResponseDto
}

internal interface ConfirmCreateProblemOnChainUseCase {
    operator fun invoke(userId: Long, walletAddress: String, request: ConfirmCreateProblemRequestDto): CreateProblemResponseDto
}

internal interface PrepareJoinProblemOnChainUseCase {
    operator fun invoke(userId: Long, walletAddress: String, problemId: Int): PrepareJoinProblemResponseDto
}

internal interface ConfirmJoinProblemOnChainUseCase {
    operator fun invoke(userId: Long, walletAddress: String, problemId: Int, request: ConfirmJoinProblemRequestDto): JoinProblemResponseDto
}

internal class PrepareCreateProblemOnChainUseCaseImpl(
    private val draftFactory: CreateProblemDraftFactory,
    private val intentStore: CompetitionIntentStore,
    private val contractClient: BlockchainPlatformContractClient,
    private val blockchainConfig: BlockchainConfig,
    private val paymentAssetCatalog: PaymentAssetCatalog,
) : PrepareCreateProblemOnChainUseCase {
    override fun invoke(
        userId: Long,
        walletAddress: String,
        request: CreateProblemRequestDto,
    ): PrepareCreateProblemResponseDto {
        val validatedDraft = draftFactory.build(request, walletAddress)
        val joinDeadlineEpochSeconds = validatedDraft.joinUntilDate.toContractDeadlineEpochSeconds()
        val submitDeadlineEpochSeconds = validatedDraft.submitUntilDate.toContractDeadlineEpochSeconds()
        val preparedTx = contractClient.prepareCreateCompetition(
            paymentAsset = validatedDraft.paymentAsset,
            competitionKey = validatedDraft.competitionKey,
            joinDeadlineEpochSeconds = joinDeadlineEpochSeconds,
            submitDeadlineEpochSeconds = submitDeadlineEpochSeconds,
            entryFeeAmountAtomic = validatedDraft.entryFeeAmountAtomic,
            requiredParticipants = validatedDraft.requiredParticipants,
            prizeAmountAtomic = validatedDraft.prizeAmountAtomic,
        )
        val storedIntent = intentStore.createCreateProblemIntent(
            validatedDraft.toPreparedIntent(
                userId = userId,
                walletAddress = walletAddress,
            )
        )
        return PrepareCreateProblemResponseDto(
            intentId = storedIntent.intentId,
            chainId = blockchainConfig.chainId,
            proxyAddress = preparedTx.transaction.to,
            explorerBaseUrl = blockchainConfig.explorerBaseUrl,
            expiresAt = storedIntent.expiresAt,
            paymentAsset = validatedDraft.paymentAsset.toDto(),
            approvalTransaction = preparedTx.approvalTransaction?.toDto(),
            transaction = PreparedWalletTransactionDto(
                to = preparedTx.transaction.to,
                data = preparedTx.transaction.data,
                valueHex = preparedTx.transaction.valueHex,
            ),
        )
    }
}

internal class ConfirmCreateProblemOnChainUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val intentStore: CompetitionIntentStore,
    private val contractClient: BlockchainPlatformContractClient,
    private val dashboardMetricsRefresher: DashboardMetricsRefresher,
    private val transactionRunner: DbTransactionRunner,
    private val contractConfig: BlockchainPlatformContractConfig,
    private val paymentAssetCatalog: PaymentAssetCatalog,
) : ConfirmCreateProblemOnChainUseCase {
    override fun invoke(
        userId: Long,
        walletAddress: String,
        request: ConfirmCreateProblemRequestDto,
    ): CreateProblemResponseDto {
        val intent = intentStore.getCreateProblemIntent(request.intentId)
            ?: throw CreateProblemValidationException("Create problem intent expired. Prepare the transaction again.")
        if (intent.userId != userId || normalizeWallet(walletAddress) != normalizeWallet(intent.walletAddress)) {
            throw CreateProblemValidationException("Create problem intent does not belong to the current wallet session.")
        }
        repository.findProblemIdByOnchainCreationTxHash(normalizeTxHash(request.txHash))?.let { existingId ->
            return CreateProblemResponseDto(id = existingId)
        }
        val joinUntilDate = LocalDate.parse(intent.joinUntilDate)
        val submitUntilDate = LocalDate.parse(intent.submitUntilDate)
        val verifiedTx = contractClient.verifyCreateCompetitionTransaction(
            txHash = request.txHash,
            expectedCreatorWallet = intent.walletAddress,
            expectedPaymentAsset = paymentAssetCatalog.requireByCode(intent.paymentAssetCode),
            expectedCompetitionKey = intent.competitionKey,
            expectedJoinDeadlineEpochSeconds = joinUntilDate.toContractDeadlineEpochSeconds(),
            expectedSubmitDeadlineEpochSeconds = submitUntilDate.toContractDeadlineEpochSeconds(),
            expectedEntryFeeAmountAtomic = intent.entryFeeAmountAtomic,
            expectedRequiredParticipants = intent.requiredParticipants,
            expectedPrizeAmountAtomic = intent.prizeAmountAtomic,
        )
        val createdProblemId = repository.createProblemForUser(
            userId = userId,
            draft = intent.toValidatedDraft(paymentAssetCatalog).toNewProblemDraft(
                onchainCompetitionId = verifiedTx.competitionId,
                onchainContractAddress = contractConfig.proxyAddress,
                onchainCreationKey = intent.competitionKey,
                onchainCreationTxHash = verifiedTx.txHash,
                onchainCreationFromWallet = normalizeWallet(walletAddress),
                onchainCreationConfirmedAt = Instant.now(),
            ),
        )
        intentStore.deleteCreateProblemIntent(request.intentId)
        transactionRunner.inTransaction {
            dashboardMetricsRefresher.refreshTodayMetrics()
        }
        return CreateProblemResponseDto(id = createdProblemId)
    }
}

internal class PrepareJoinProblemOnChainUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val intentStore: CompetitionIntentStore,
    private val contractClient: BlockchainPlatformContractClient,
    private val blockchainConfig: BlockchainConfig,
    private val paymentAssetCatalog: PaymentAssetCatalog,
) : PrepareJoinProblemOnChainUseCase {
    override fun invoke(userId: Long, walletAddress: String, problemId: Int): PrepareJoinProblemResponseDto {
        if (problemId <= 0) {
            throw JoinProblemValidationException("Invalid problem identifier.")
        }
        val context = try {
            repository.fetchOnchainJoinContext(problemId)
        } catch (error: IllegalArgumentException) {
            throw JoinProblemValidationException(error.message ?: "Cannot join this competition.")
        }
        val paymentAsset = paymentAssetCatalog.requireByCode(context.paymentAsset.code)
        val preparedTx = contractClient.prepareJoinCompetition(
            paymentAsset = paymentAsset,
            competitionId = context.competitionId,
            entryFeeAmountAtomic = context.entryFeeAmountAtomic,
        )
        val storedIntent = intentStore.createJoinProblemIntent(
            pl.dawidszczesniak.blockchain_platform.feature.problems.competition.PreparedJoinProblemIntent(
                intentId = "",
                userId = userId,
                walletAddress = walletAddress,
                problemId = context.problemId,
                competitionId = context.competitionId,
                paymentAssetCode = paymentAsset.code,
                entryFeeAmountAtomic = context.entryFeeAmountAtomic,
                expiresAt = "",
            )
        )
        return PrepareJoinProblemResponseDto(
            intentId = storedIntent.intentId,
            chainId = blockchainConfig.chainId,
            proxyAddress = preparedTx.transaction.to,
            explorerBaseUrl = blockchainConfig.explorerBaseUrl,
            expiresAt = storedIntent.expiresAt,
            paymentAsset = paymentAsset.toDto(),
            approvalTransaction = preparedTx.approvalTransaction?.toDto(),
            transaction = PreparedWalletTransactionDto(
                to = preparedTx.transaction.to,
                data = preparedTx.transaction.data,
                valueHex = preparedTx.transaction.valueHex,
            ),
        )
    }
}

internal class ConfirmJoinProblemOnChainUseCaseImpl(
    private val repository: ProblemWriteRepository,
    private val intentStore: CompetitionIntentStore,
    private val contractClient: BlockchainPlatformContractClient,
    private val paymentAssetCatalog: PaymentAssetCatalog,
) : ConfirmJoinProblemOnChainUseCase {
    override fun invoke(
        userId: Long,
        walletAddress: String,
        problemId: Int,
        request: ConfirmJoinProblemRequestDto,
    ): JoinProblemResponseDto {
        val intent = intentStore.getJoinProblemIntent(request.intentId)
            ?: throw JoinProblemValidationException("Join competition intent expired. Prepare the transaction again.")
        if (intent.userId != userId || intent.problemId != problemId) {
            throw JoinProblemValidationException("Join competition intent does not match the current request.")
        }
        if (normalizeWallet(walletAddress) != normalizeWallet(intent.walletAddress)) {
            throw JoinProblemValidationException("Join competition intent does not belong to the current wallet session.")
        }
        contractClient.verifyJoinCompetitionTransaction(
            txHash = request.txHash,
            expectedParticipantWallet = intent.walletAddress,
            expectedPaymentAsset = paymentAssetCatalog.requireByCode(intent.paymentAssetCode),
            expectedCompetitionId = intent.competitionId,
            expectedEntryFeeAmountAtomic = intent.entryFeeAmountAtomic,
        )
        val result = try {
            repository.registerUserForProblemOnChain(
                userId = userId,
                problemId = problemId,
                txHash = normalizeTxHash(request.txHash),
                joinedAt = Instant.now(),
                fromWallet = normalizeWallet(walletAddress),
            )
        } catch (error: IllegalArgumentException) {
            throw JoinProblemValidationException(error.message ?: "Cannot register for this problem.")
        }
        intentStore.deleteJoinProblemIntent(request.intentId)
        return JoinProblemResponseDto(
            joined = result.joined,
            registeredParticipants = result.registeredParticipants,
            requiredParticipants = result.requiredParticipants,
        )
    }
}

private fun pl.dawidszczesniak.blockchain_platform.feature.problems.competition.PreparedCreateProblemIntent.toValidatedDraft(
    paymentAssetCatalog: PaymentAssetCatalog,
): ValidatedCreateProblemDraft {
    return ValidatedCreateProblemDraft(
        title = title,
        description = description,
        constraints = constraints,
        examples = examples,
        referenceSolutionCode = referenceSolutionCode,
        referenceSolutionHash = referenceSolutionHash,
        referenceRuntimeMs = referenceRuntimeMs,
        referenceMemoryUsedKb = referenceMemoryUsedKb,
        referenceConsensusNodes = referenceConsensusNodes,
        validationNodeId = validationNodeId,
        validationRunHash = validationRunHash,
        validationResultHash = validationResultHash,
        validationImageHash = validationImageHash,
        validatedAt = Instant.parse(validatedAt),
        paymentAsset = paymentAssetCatalog.requireByCode(paymentAssetCode),
        prizeAmountAtomic = prizeAmountAtomic,
        entryFeeAmountAtomic = entryFeeAmountAtomic,
        requiredParticipants = requiredParticipants,
        joinUntilDate = LocalDate.parse(joinUntilDate),
        submitUntilDate = LocalDate.parse(submitUntilDate),
        tests = tests,
        competitionKey = competitionKey,
    )
}

private fun pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.PreparedCompetitionTransaction.toDto():
    PreparedWalletTransactionDto {
    return PreparedWalletTransactionDto(
        to = to,
        data = data,
        valueHex = valueHex,
    )
}

private fun normalizeWallet(walletAddress: String): String {
    return "0x${walletAddress.trim().removePrefix("0x").lowercase()}"
}

private fun normalizeTxHash(txHash: String): String {
    return "0x${txHash.trim().removePrefix("0x").lowercase()}"
}
