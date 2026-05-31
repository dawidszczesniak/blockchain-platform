package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import kotlinx.serialization.json.Json
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.PreparedWalletTransactionDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.dto.SubmitProblemResponseDto
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.BlockchainPlatformContractConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.PreparedCompetitionTransaction
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultContractClient
import pl.dawidszczesniak.blockchain_platform.feature.problems.onchain.SubmissionResultRecord
import pl.dawidszczesniak.blockchain_platform.feature.problems.repository.SubmissionResultRepository

internal interface SubmissionOnchainPayloadRefresher {
    fun refresh(
        userId: Long,
        submissionId: Long,
        existingPayload: SubmitProblemResponseDto,
    ): SubmitProblemResponseDto
}

internal class SubmissionOnchainPayloadRefresherImpl(
    private val repository: SubmissionResultRepository,
    private val contractClient: SubmissionResultContractClient,
    private val contractConfig: BlockchainPlatformContractConfig,
    private val blockchainConfig: BlockchainConfig,
) : SubmissionOnchainPayloadRefresher {
    private val json = Json { ignoreUnknownKeys = true }

    override fun refresh(
        userId: Long,
        submissionId: Long,
        existingPayload: SubmitProblemResponseDto,
    ): SubmitProblemResponseDto {
        val context = repository.fetchSubmissionOnchainConfirmationContext(userId, submissionId) ?: return existingPayload
        val refreshed = when {
            context.onchainRecordedAt != null -> existingPayload.copy(
                chainId = blockchainConfig.chainId,
                proxyAddress = contractConfig.proxyAddress,
                onchainSimulationError = null,
                onchainRecorded = true,
                txHash = context.onchainRecordTxHash.orEmpty(),
                explorerUrl = contractConfig.explorerTxUrl(context.onchainRecordTxHash),
            )

            !context.onchainRecordTxHash.isNullOrBlank() -> existingPayload.copy(
                chainId = blockchainConfig.chainId,
                proxyAddress = contractConfig.proxyAddress,
                onchainSimulationError = null,
                onchainRecorded = false,
                txHash = context.onchainRecordTxHash,
                explorerUrl = contractConfig.explorerTxUrl(context.onchainRecordTxHash),
            )

            else -> {
                val prepared = contractClient.prepareSignedSubmissionResult(
                    SubmissionResultRecord(
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
                    )
                )
                prepared.simulationErrorMessage?.let { message ->
                    repository.markSubmissionResultPendingError(
                        submissionId = submissionId,
                        error = message,
                    )
                }
                existingPayload.copy(
                    chainId = blockchainConfig.chainId,
                    proxyAddress = contractConfig.proxyAddress,
                    walletTransaction = prepared.simulationErrorMessage?.let { null }
                        ?: prepared.transaction.toDto(),
                    signature = prepared.signatureHex,
                    signerWalletAddress = prepared.signerWalletAddress,
                    onchainSimulationError = prepared.simulationErrorMessage,
                    onchainRecorded = false,
                    txHash = "",
                    explorerUrl = null,
                )
            }
        }
        if (refreshed != existingPayload) {
            repository.updateSubmissionAcceptedResultPayload(
                submissionId = submissionId,
                payloadJson = json.encodeToString(refreshed),
            )
        }
        return refreshed
    }
}

private fun PreparedCompetitionTransaction.toDto(): PreparedWalletTransactionDto {
    return PreparedWalletTransactionDto(
        to = to,
        data = data,
        valueHex = valueHex,
    )
}
