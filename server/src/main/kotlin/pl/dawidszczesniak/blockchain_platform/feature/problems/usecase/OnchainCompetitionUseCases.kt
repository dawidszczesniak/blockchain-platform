package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.time.Instant
import java.time.LocalDate
import pl.dawidszczesniak.blockchain_platform.db.CompetitionSettlementStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.dawidszczesniak.blockchain_platform.db.DashboardMetricsRefresher
import pl.dawidszczesniak.blockchain_platform.db.DbTransactionRunner
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetCatalog
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmSubmissionResultRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CompetitionLifecycleActionResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.toContractDeadlineEpochSeconds
import pl.dawidszczesniak.blockchain_platform.feature.problems.competition.CompetitionIntentStore
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCompetitionLifecycleActionRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmCreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.ConfirmJoinProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemRequestDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.CreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.JoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCreateProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareCompetitionLifecycleActionResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PreparedWalletTransactionDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PrepareJoinProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.CompetitionCreationContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.CompetitionJoinContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.CompetitionLifecycleContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.CompetitionSettlementRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemCreationRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.ProblemParticipationRepository
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionResultRepository

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

internal interface PrepareSettleCompetitionOnChainUseCase {
    operator fun invoke(userId: Long, walletAddress: String, problemId: Int): PrepareCompetitionLifecycleActionResponseDto
}

internal interface ConfirmSettleCompetitionOnChainUseCase {
    operator fun invoke(
        userId: Long,
        walletAddress: String,
        problemId: Int,
        request: ConfirmCompetitionLifecycleActionRequestDto,
    ): CompetitionLifecycleActionResponseDto
}

internal interface PrepareCancelCompetitionOnChainUseCase {
    operator fun invoke(userId: Long, walletAddress: String, problemId: Int): PrepareCompetitionLifecycleActionResponseDto
}

internal interface ConfirmCancelCompetitionOnChainUseCase {
    operator fun invoke(
        userId: Long,
        walletAddress: String,
        problemId: Int,
        request: ConfirmCompetitionLifecycleActionRequestDto,
    ): CompetitionLifecycleActionResponseDto
}

internal interface ConfirmSubmissionResultOnChainUseCase {
    operator fun invoke(
        userId: Long,
        walletAddress: String,
        submissionId: Long,
        request: ConfirmSubmissionResultRequestDto,
    ): SubmitProblemResponseDto
}

internal class PrepareCreateProblemOnChainUseCaseImpl(
    private val draftFactory: CreateProblemDraftFactory,
    private val intentStore: CompetitionIntentStore,
    private val contractClient: CompetitionCreationContractClient,
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
    private val repository: ProblemCreationRepository,
    private val intentStore: CompetitionIntentStore,
    private val contractClient: CompetitionCreationContractClient,
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
    private val repository: ProblemParticipationRepository,
    private val intentStore: CompetitionIntentStore,
    private val contractClient: CompetitionJoinContractClient,
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
    private val repository: ProblemParticipationRepository,
    private val intentStore: CompetitionIntentStore,
    private val contractClient: CompetitionJoinContractClient,
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

internal class CompetitionLifecycleValidationException(message: String) : RuntimeException(message)

internal class PrepareSettleCompetitionOnChainUseCaseImpl(
    private val repository: CompetitionSettlementRepository,
    private val contractClient: CompetitionLifecycleContractClient,
    private val blockchainConfig: BlockchainConfig,
) : PrepareSettleCompetitionOnChainUseCase {
    override fun invoke(
        userId: Long,
        walletAddress: String,
        problemId: Int,
    ): PrepareCompetitionLifecycleActionResponseDto {
        if (userId <= 0L || walletAddress.isBlank()) {
            throw CompetitionLifecycleValidationException("Authenticated wallet session is required.")
        }
        val context = repository.fetchCompetitionLifecycleContext(problemId)
            ?: throw CompetitionLifecycleValidationException("Problem is not linked to an on-chain competition.")
        requireSettlementReady(context)
        val transaction = contractClient.prepareSettleCompetition(context.competitionId)
        return PrepareCompetitionLifecycleActionResponseDto(
            chainId = blockchainConfig.chainId,
            proxyAddress = transaction.to,
            explorerBaseUrl = blockchainConfig.explorerBaseUrl,
            transaction = transaction.toDto(),
        )
    }
}

internal class ConfirmSettleCompetitionOnChainUseCaseImpl(
    private val repository: CompetitionSettlementRepository,
    private val contractClient: CompetitionLifecycleContractClient,
    private val contractConfig: BlockchainPlatformContractConfig,
) : ConfirmSettleCompetitionOnChainUseCase {
    override fun invoke(
        userId: Long,
        walletAddress: String,
        problemId: Int,
        request: ConfirmCompetitionLifecycleActionRequestDto,
    ): CompetitionLifecycleActionResponseDto {
        if (userId <= 0L || walletAddress.isBlank()) {
            throw CompetitionLifecycleValidationException("Authenticated wallet session is required.")
        }
        val context = repository.fetchCompetitionLifecycleContext(problemId)
            ?: throw CompetitionLifecycleValidationException("Problem is not linked to an on-chain competition.")
        requireSettlementReady(context)
        val normalizedWallet = normalizeWallet(walletAddress)
        val verifiedTx = contractClient.verifySettleCompetitionTransaction(
            txHash = normalizeTxHash(request.txHash),
            expectedSenderWallet = normalizedWallet,
            expectedCompetitionId = context.competitionId,
        )
        val winnerUserId = repository.findUserIdByWalletAddress(verifiedTx.winnerWalletAddress)
            ?: throw CompetitionLifecycleValidationException("Winner wallet from on-chain settlement is not mapped to a platform user.")
        repository.recordSettledWinner(
            problemId = problemId,
            winnerUserId = winnerUserId,
            payoutAmountAtomic = context.prizeAmountAtomic,
            txHash = verifiedTx.txHash,
            settledAt = Instant.now(),
            fromWallet = normalizedWallet,
        )
        return CompetitionLifecycleActionResponseDto(
            competitionId = context.competitionId,
            settlementStatus = CompetitionSettlementStatus.Settled.dbValue,
            txHash = verifiedTx.txHash,
            explorerUrl = contractConfig.explorerTxUrl(verifiedTx.txHash),
            winnerWalletAddress = verifiedTx.winnerWalletAddress,
        )
    }
}

internal class PrepareCancelCompetitionOnChainUseCaseImpl(
    private val repository: CompetitionSettlementRepository,
    private val contractClient: CompetitionLifecycleContractClient,
    private val blockchainConfig: BlockchainConfig,
    private val contractConfig: BlockchainPlatformContractConfig,
) : PrepareCancelCompetitionOnChainUseCase {
    override fun invoke(
        userId: Long,
        walletAddress: String,
        problemId: Int,
    ): PrepareCompetitionLifecycleActionResponseDto {
        if (userId <= 0L || walletAddress.isBlank()) {
            throw CompetitionLifecycleValidationException("Authenticated wallet session is required.")
        }
        val context = repository.fetchCompetitionLifecycleContext(problemId)
            ?: throw CompetitionLifecycleValidationException("Problem is not linked to an on-chain competition.")
        requireCancellationAuthorization(
            context = context,
            callerWalletAddress = walletAddress,
            operatorWalletAddress = contractConfig.operatorWalletAddress,
        )
        requireCancellationReady(context)
        val transaction = contractClient.prepareCancelCompetition(context.competitionId)
        return PrepareCompetitionLifecycleActionResponseDto(
            chainId = blockchainConfig.chainId,
            proxyAddress = transaction.to,
            explorerBaseUrl = blockchainConfig.explorerBaseUrl,
            transaction = transaction.toDto(),
        )
    }
}

internal class ConfirmCancelCompetitionOnChainUseCaseImpl(
    private val repository: CompetitionSettlementRepository,
    private val contractClient: CompetitionLifecycleContractClient,
    private val contractConfig: BlockchainPlatformContractConfig,
) : ConfirmCancelCompetitionOnChainUseCase {
    override fun invoke(
        userId: Long,
        walletAddress: String,
        problemId: Int,
        request: ConfirmCompetitionLifecycleActionRequestDto,
    ): CompetitionLifecycleActionResponseDto {
        if (userId <= 0L || walletAddress.isBlank()) {
            throw CompetitionLifecycleValidationException("Authenticated wallet session is required.")
        }
        val context = repository.fetchCompetitionLifecycleContext(problemId)
            ?: throw CompetitionLifecycleValidationException("Problem is not linked to an on-chain competition.")
        requireCancellationAuthorization(
            context = context,
            callerWalletAddress = walletAddress,
            operatorWalletAddress = contractConfig.operatorWalletAddress,
        )
        requireCancellationReady(context)
        val normalizedWallet = normalizeWallet(walletAddress)
        val verifiedTx = contractClient.verifyCancelCompetitionTransaction(
            txHash = normalizeTxHash(request.txHash),
            expectedSenderWallet = normalizedWallet,
            expectedCompetitionId = context.competitionId,
        )
        repository.markCompetitionSettlementCancelled(
            problemId = problemId,
            txHash = verifiedTx.txHash,
            settledAt = Instant.now(),
            fromWallet = normalizedWallet,
        )
        return CompetitionLifecycleActionResponseDto(
            competitionId = context.competitionId,
            settlementStatus = CompetitionSettlementStatus.Cancelled.dbValue,
            txHash = verifiedTx.txHash,
            explorerUrl = contractConfig.explorerTxUrl(verifiedTx.txHash),
            winnerWalletAddress = null,
        )
    }
}

internal class ConfirmSubmissionResultOnChainUseCaseImpl(
    private val repository: SubmissionResultRepository,
    private val contractClient: SubmissionResultContractClient,
    private val contractConfig: BlockchainPlatformContractConfig,
) : ConfirmSubmissionResultOnChainUseCase {
    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(
        userId: Long,
        walletAddress: String,
        submissionId: Long,
        request: ConfirmSubmissionResultRequestDto,
    ): SubmitProblemResponseDto {
        val context = repository.fetchSubmissionOnchainConfirmationContext(userId, submissionId)
            ?: throw SubmissionJudgeJobValidationException("Submission was not found for the current wallet session.")
        val normalizedWallet = normalizeWallet(walletAddress)
        if (normalizedWallet != normalizeWallet(context.participantWalletAddress)) {
            throw SubmissionJudgeJobValidationException("Submission does not belong to the current wallet session.")
        }
        val payloadJson = context.resultPayloadJson?.takeIf { it.isNotBlank() }
            ?: throw SubmissionJudgeJobValidationException("Submission result is not ready for on-chain confirmation.")
        val existingPayload = json.decodeFromString<SubmitProblemResponseDto>(payloadJson)
        val normalizedTxHash = normalizeTxHash(request.txHash)

        if (context.onchainRecordedAt != null) {
            val recordedTxHash = context.onchainRecordTxHash?.let(::normalizeTxHash).orEmpty()
            if (recordedTxHash.isNotBlank() && recordedTxHash != normalizedTxHash) {
                throw SubmissionJudgeJobValidationException(
                    "Submission was already confirmed on-chain under transaction $recordedTxHash."
                )
            }
            val confirmedPayload = existingPayload.copy(
                onchainRecorded = true,
                txHash = recordedTxHash.ifBlank { normalizedTxHash },
                explorerUrl = contractConfig.explorerTxUrl(recordedTxHash.ifBlank { normalizedTxHash }),
            )
            repository.updateSubmissionAcceptedResultPayload(
                submissionId = submissionId,
                payloadJson = json.encodeToString(confirmedPayload),
            )
            return confirmedPayload
        }

        val signature = existingPayload.signature?.takeIf { it.isNotBlank() }
            ?: throw SubmissionJudgeJobValidationException("Submission signature is missing.")
        val verifiedTx = contractClient.verifySubmissionResultTransaction(
            txHash = normalizedTxHash,
            expectedParticipantWallet = context.participantWalletAddress,
            record = SubmissionResultRecord(
                competitionId = context.competitionId,
                onchainSubmissionId = context.onchainSubmissionId,
                participantWalletAddress = context.participantWalletAddress,
                submissionHash = context.commitmentHash,
                codeHash = context.codeHash,
                challengeHash = context.challengeHash,
                resultHash = context.resultHash,
                sandboxImageHash = context.consensusImageHash,
                runtimeMs = context.runtimeMs,
                memoryUsedKb = context.memoryUsedKb,
                consensusNodes = context.consensusNodes,
            ),
            signatureHex = signature,
        )
        repository.markSubmissionResultRecorded(
            submissionId = submissionId,
            proxyAddress = contractConfig.proxyAddress,
            txHash = verifiedTx.txHash,
            recordedAt = Instant.now(),
            fromWallet = normalizedWallet,
        )
        val confirmedPayload = existingPayload.copy(
            onchainRecorded = true,
            txHash = verifiedTx.txHash,
            explorerUrl = contractConfig.explorerTxUrl(verifiedTx.txHash),
        )
        repository.updateSubmissionAcceptedResultPayload(
            submissionId = submissionId,
            payloadJson = json.encodeToString(confirmedPayload),
        )
        return confirmedPayload
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

private fun requireSettlementReady(context: pl.dawidszczesniak.blockchain_platform.feature.problems.repository.CompetitionLifecycleContext) {
    if (context.settlementStatus != CompetitionSettlementStatus.Pending.dbValue) {
        throw CompetitionLifecycleValidationException("Competition is already finalized on-chain.")
    }
    if (context.registeredParticipants < context.requiredParticipants) {
        throw CompetitionLifecycleValidationException("Competition cannot be settled before the participant threshold is reached.")
    }
    val submitDeadlineEpochSeconds = context.submitUntilDate.toContractDeadlineEpochSeconds()
    if (Instant.now().epochSecond <= submitDeadlineEpochSeconds) {
        throw CompetitionLifecycleValidationException("Competition settlement unlocks after the submission deadline.")
    }
}

private fun requireCancellationReady(context: pl.dawidszczesniak.blockchain_platform.feature.problems.repository.CompetitionLifecycleContext) {
    if (context.settlementStatus != CompetitionSettlementStatus.Pending.dbValue) {
        throw CompetitionLifecycleValidationException("Competition is already finalized on-chain.")
    }
    val nowEpochSeconds = Instant.now().epochSecond
    val joinDeadlineEpochSeconds = context.joinUntilDate.toContractDeadlineEpochSeconds()
    val submitDeadlineEpochSeconds = context.submitUntilDate.toContractDeadlineEpochSeconds()
    val registrationFailed = nowEpochSeconds > joinDeadlineEpochSeconds &&
        context.registeredParticipants < context.requiredParticipants
    val submissionsFinished = nowEpochSeconds > submitDeadlineEpochSeconds
    if (!registrationFailed && !submissionsFinished) {
        throw CompetitionLifecycleValidationException("Competition cancellation is still time-locked.")
    }
}

private fun requireCancellationAuthorization(
    context: pl.dawidszczesniak.blockchain_platform.feature.problems.repository.CompetitionLifecycleContext,
    callerWalletAddress: String,
    operatorWalletAddress: String,
) {
    val normalizedCaller = normalizeWallet(callerWalletAddress)
    val normalizedCreator = normalizeWallet(context.creatorWalletAddress)
    val normalizedOperator = normalizeWallet(operatorWalletAddress)
    if (normalizedCaller != normalizedCreator && normalizedCaller != normalizedOperator) {
        throw CompetitionLifecycleValidationException("Only the competition creator or platform admin can cancel this competition from the UI.")
    }
}

private fun normalizeWallet(walletAddress: String): String {
    return "0x${walletAddress.trim().removePrefix("0x").lowercase()}"
}

private fun normalizeTxHash(txHash: String): String {
    return "0x${txHash.trim().removePrefix("0x").lowercase()}"
}
