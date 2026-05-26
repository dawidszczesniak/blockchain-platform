package pl.dawidszczesniak.blockchain_platform.feature.problems.onchain

import java.math.BigInteger
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.NumericType
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint64
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.Log
import org.web3j.protocol.core.methods.response.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import org.web3j.protocol.core.methods.request.Transaction as EthCallTransaction
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetKind
import pl.dawidszczesniak.blockchain_platform.feature.problems.atomicAmountToBigInteger

internal data class BlockchainPlatformContractConfig(
    val proxyAddress: String,
    val operatorPrivateKey: String,
    val gasLimit: Long,
    val gasPriceWei: Long?,
    val explorerTxBaseUrl: String?,
    val prepareIntentTtlSeconds: Int,
) {
    val operatorWalletAddress: String
        get() = normalizeAddress(Credentials.create(operatorPrivateKey.removePrefix("0x")).address)

    fun explorerTxUrl(txHash: String?): String? {
        if (txHash.isNullOrBlank()) return null
        val base = explorerTxBaseUrl?.trim().orEmpty().trimEnd('/')
        if (base.isBlank()) return null
        return "$base/$txHash"
    }

    companion object {
        fun fromEnvironment(
            env: Map<String, String>,
            blockchainConfig: BlockchainConfig,
        ): BlockchainPlatformContractConfig {
            val proxyAddress = env["ETH_PLATFORM_PROXY_ADDRESS"]?.trim()?.ifBlank { null }
            val operatorPrivateKey = env["ETH_PLATFORM_OPERATOR_PRIVATE_KEY"]?.trim()?.ifBlank { null }

            require(!blockchainConfig.ethRpcUrl.isNullOrBlank()) {
                "ETH_RPC_URL must be configured because BlockchainTestContract is always used on-chain."
            }
            require(!proxyAddress.isNullOrBlank()) {
                "ETH_PLATFORM_PROXY_ADDRESS must be configured because BlockchainTestContract is always used on-chain."
            }
            require(!operatorPrivateKey.isNullOrBlank()) {
                "ETH_PLATFORM_OPERATOR_PRIVATE_KEY must be configured because submission result payloads are signed by platform operator."
            }

            return BlockchainPlatformContractConfig(
                proxyAddress = proxyAddress,
                operatorPrivateKey = operatorPrivateKey,
                gasLimit = env["ETH_PLATFORM_GAS_LIMIT"]
                    ?.toLongOrNull()
                    ?.coerceIn(120_000L, 5_000_000L)
                    ?: DEFAULT_GAS_LIMIT,
                gasPriceWei = env["ETH_PLATFORM_GAS_PRICE_WEI"]
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L },
                explorerTxBaseUrl = blockchainConfig.explorerBaseUrl?.let { "$it/tx" },
                prepareIntentTtlSeconds = env["ETH_PLATFORM_PREPARE_INTENT_TTL_SECONDS"]
                    ?.toIntOrNull()
                    ?.coerceIn(60, 3_600)
                    ?: DEFAULT_PREPARE_INTENT_TTL_SECONDS,
            )
        }
    }
}

internal data class PreparedCompetitionTransaction(
    val to: String,
    val data: String,
    val valueHex: String,
)

internal data class PreparedCompetitionTransactions(
    val approvalTransaction: PreparedCompetitionTransaction? = null,
    val transaction: PreparedCompetitionTransaction,
)

internal data class VerifiedCompetitionCreation(
    val competitionId: Long,
    val txHash: String,
)

internal data class VerifiedCompetitionJoin(
    val txHash: String,
)

internal data class SubmissionResultRecord(
    val competitionId: Long,
    val onchainSubmissionId: Long,
    val participantWalletAddress: String,
    val submissionHash: String,
    val codeHash: String,
    val challengeHash: String,
    val resultHash: String,
    val sandboxImageHash: String,
    val runtimeMs: Int,
    val memoryUsedKb: Int,
    val consensusNodes: Int,
)

internal data class PreparedSignedSubmissionResult(
    val signatureHex: String,
    val signerWalletAddress: String,
    val transaction: PreparedCompetitionTransaction,
    val simulationErrorMessage: String? = null,
)

internal data class VerifiedSubmissionRecording(
    val txHash: String,
)

internal data class VerifiedCompetitionSettlement(
    val txHash: String,
    val winnerWalletAddress: String,
)

internal data class VerifiedCompetitionCancellation(
    val txHash: String,
)

internal interface BlockchainPlatformContractClient {
    fun prepareCreateCompetition(
        paymentAsset: PaymentAssetConfig,
        competitionKey: String,
        joinDeadlineEpochSeconds: Long,
        submitDeadlineEpochSeconds: Long,
        entryFeeAmountAtomic: String,
        requiredParticipants: Int,
        prizeAmountAtomic: String,
    ): PreparedCompetitionTransactions

    fun prepareJoinCompetition(
        paymentAsset: PaymentAssetConfig,
        competitionId: Long,
        entryFeeAmountAtomic: String,
    ): PreparedCompetitionTransactions

    fun verifyCreateCompetitionTransaction(
        txHash: String,
        expectedCreatorWallet: String,
        expectedPaymentAsset: PaymentAssetConfig,
        expectedCompetitionKey: String,
        expectedJoinDeadlineEpochSeconds: Long,
        expectedSubmitDeadlineEpochSeconds: Long,
        expectedEntryFeeAmountAtomic: String,
        expectedRequiredParticipants: Int,
        expectedPrizeAmountAtomic: String,
    ): VerifiedCompetitionCreation

    fun verifyJoinCompetitionTransaction(
        txHash: String,
        expectedParticipantWallet: String,
        expectedPaymentAsset: PaymentAssetConfig,
        expectedCompetitionId: Long,
        expectedEntryFeeAmountAtomic: String,
    ): VerifiedCompetitionJoin

    fun prepareSignedSubmissionResult(
        record: SubmissionResultRecord,
    ): PreparedSignedSubmissionResult

    fun verifySubmissionResultTransaction(
        txHash: String,
        expectedParticipantWallet: String,
        record: SubmissionResultRecord,
        signatureHex: String,
    ): VerifiedSubmissionRecording

    fun prepareSettleCompetition(competitionId: Long): PreparedCompetitionTransaction

    fun verifySettleCompetitionTransaction(
        txHash: String,
        expectedSenderWallet: String,
        expectedCompetitionId: Long,
    ): VerifiedCompetitionSettlement

    fun prepareCancelCompetition(competitionId: Long): PreparedCompetitionTransaction

    fun verifyCancelCompetitionTransaction(
        txHash: String,
        expectedSenderWallet: String,
        expectedCompetitionId: Long,
    ): VerifiedCompetitionCancellation

    fun close()
}

internal class EthereumBlockchainPlatformContractClient(
    private val blockchainConfig: BlockchainConfig,
    private val contractConfig: BlockchainPlatformContractConfig,
) : BlockchainPlatformContractClient {
    private val web3j: Web3j = Web3j.build(HttpService(requireNotNull(blockchainConfig.ethRpcUrl)))
    private val operatorCredentials: Credentials = Credentials.create(
        contractConfig.operatorPrivateKey.removePrefix("0x")
    )

    override fun prepareCreateCompetition(
        paymentAsset: PaymentAssetConfig,
        competitionKey: String,
        joinDeadlineEpochSeconds: Long,
        submitDeadlineEpochSeconds: Long,
        entryFeeAmountAtomic: String,
        requiredParticipants: Int,
        prizeAmountAtomic: String,
    ): PreparedCompetitionTransactions {
        val prizeAmount = atomicAmountToBigInteger(prizeAmountAtomic, "Prize amount")
        require(prizeAmount > BigInteger.ZERO) { "Prize must be greater than 0." }
        val entryFeeAmount = atomicAmountToBigInteger(entryFeeAmountAtomic, "Entry fee amount")

        val function = Function(
            METHOD_CREATE_COMPETITION,
            listOf(
                Bytes32(hexToBytes32(competitionKey)),
                Address(paymentTokenAddress(paymentAsset)),
                Uint256(prizeAmount),
                Uint256(entryFeeAmount),
                Uint64(BigInteger.valueOf(joinDeadlineEpochSeconds)),
                Uint64(BigInteger.valueOf(submitDeadlineEpochSeconds)),
                Uint32(BigInteger.valueOf(requiredParticipants.toLong())),
            ),
            emptyList(),
        )
        val mainTransaction = PreparedCompetitionTransaction(
            to = contractConfig.proxyAddress,
            data = FunctionEncoder.encode(function),
            valueHex = toPrefixedHex(expectedTransactionValue(paymentAsset, prizeAmount)),
        )
        val approvalTransaction = paymentAsset.tokenAddress?.let { tokenAddress ->
            prepareApprovalTransaction(tokenAddress = tokenAddress, amount = prizeAmount)
        }
        return PreparedCompetitionTransactions(
            approvalTransaction = approvalTransaction,
            transaction = mainTransaction,
        )
    }

    override fun prepareJoinCompetition(
        paymentAsset: PaymentAssetConfig,
        competitionId: Long,
        entryFeeAmountAtomic: String,
    ): PreparedCompetitionTransactions {
        val entryFeeAmount = atomicAmountToBigInteger(entryFeeAmountAtomic, "Entry fee amount")
        val function = Function(
            METHOD_JOIN_COMPETITION,
            listOf(Uint256(BigInteger.valueOf(competitionId))),
            emptyList(),
        )
        val mainTransaction = PreparedCompetitionTransaction(
            to = contractConfig.proxyAddress,
            data = FunctionEncoder.encode(function),
            valueHex = toPrefixedHex(expectedTransactionValue(paymentAsset, entryFeeAmount)),
        )
        val approvalTransaction = paymentAsset.tokenAddress?.let { tokenAddress ->
            prepareApprovalTransaction(tokenAddress = tokenAddress, amount = entryFeeAmount)
        }
        return PreparedCompetitionTransactions(
            approvalTransaction = approvalTransaction,
            transaction = mainTransaction,
        )
    }

    override fun verifyCreateCompetitionTransaction(
        txHash: String,
        expectedCreatorWallet: String,
        expectedPaymentAsset: PaymentAssetConfig,
        expectedCompetitionKey: String,
        expectedJoinDeadlineEpochSeconds: Long,
        expectedSubmitDeadlineEpochSeconds: Long,
        expectedEntryFeeAmountAtomic: String,
        expectedRequiredParticipants: Int,
        expectedPrizeAmountAtomic: String,
    ): VerifiedCompetitionCreation {
        val transaction = loadTransaction(txHash)
        val receipt = loadSuccessfulReceipt(txHash)
        val expectedPrepared = prepareCreateCompetition(
            paymentAsset = expectedPaymentAsset,
            competitionKey = expectedCompetitionKey,
            joinDeadlineEpochSeconds = expectedJoinDeadlineEpochSeconds,
            submitDeadlineEpochSeconds = expectedSubmitDeadlineEpochSeconds,
            entryFeeAmountAtomic = expectedEntryFeeAmountAtomic,
            requiredParticipants = expectedRequiredParticipants,
            prizeAmountAtomic = expectedPrizeAmountAtomic,
        )
        validateUserSubmittedTransaction(
            transaction = transaction,
            expectedFrom = expectedCreatorWallet,
            expectedTo = expectedPrepared.transaction.to,
            expectedData = expectedPrepared.transaction.data,
            expectedValue = hexToBigInteger(expectedPrepared.transaction.valueHex),
        )
        val log = findEventLog(receipt, CREATED_EVENT_SIGNATURE)
        val competitionId = Numeric.toBigInt(log.topics.getOrNull(1)).longValueExact()
        val competitionKey = normalizeHex(log.topics.getOrNull(2))
        val creatorWallet = topicAddress(log.topics.getOrNull(3))
        if (competitionKey != normalizeHex(expectedCompetitionKey)) {
            error("CompetitionCreated event key does not match prepared intent.")
        }
        if (creatorWallet != normalizeAddress(expectedCreatorWallet)) {
            error("CompetitionCreated event creator does not match current wallet.")
        }
        val values = FunctionReturnDecoder.decode(log.data, CREATED_EVENT_PARAMETERS)
        val paymentToken = normalizeAddress(values.addressValue(0))
        val prizeAmount = values.uintValue(1)
        val entryFeeAmount = values.uintValue(2)
        val joinDeadline = values.uintValue(3)
        val submitDeadline = values.uintValue(4)
        val requiredParticipants = values.uintValue(5)
        if (
            paymentToken != paymentTokenAddress(expectedPaymentAsset) ||
            prizeAmount != atomicAmountToBigInteger(expectedPrizeAmountAtomic, "Prize amount") ||
            entryFeeAmount != atomicAmountToBigInteger(expectedEntryFeeAmountAtomic, "Entry fee amount") ||
            joinDeadline != BigInteger.valueOf(expectedJoinDeadlineEpochSeconds) ||
            submitDeadline != BigInteger.valueOf(expectedSubmitDeadlineEpochSeconds) ||
            requiredParticipants != BigInteger.valueOf(expectedRequiredParticipants.toLong())
        ) {
            error("CompetitionCreated event payload does not match prepared intent.")
        }
        return VerifiedCompetitionCreation(
            competitionId = competitionId,
            txHash = normalizeHex(txHash),
        )
    }

    override fun verifyJoinCompetitionTransaction(
        txHash: String,
        expectedParticipantWallet: String,
        expectedPaymentAsset: PaymentAssetConfig,
        expectedCompetitionId: Long,
        expectedEntryFeeAmountAtomic: String,
    ): VerifiedCompetitionJoin {
        val transaction = loadTransaction(txHash)
        val receipt = loadSuccessfulReceipt(txHash)
        val expectedPrepared = prepareJoinCompetition(
            paymentAsset = expectedPaymentAsset,
            competitionId = expectedCompetitionId,
            entryFeeAmountAtomic = expectedEntryFeeAmountAtomic,
        )
        validateUserSubmittedTransaction(
            transaction = transaction,
            expectedFrom = expectedParticipantWallet,
            expectedTo = expectedPrepared.transaction.to,
            expectedData = expectedPrepared.transaction.data,
            expectedValue = hexToBigInteger(expectedPrepared.transaction.valueHex),
        )
        val log = findEventLog(receipt, JOINED_EVENT_SIGNATURE)
        val competitionId = Numeric.toBigInt(log.topics.getOrNull(1)).longValueExact()
        val participant = topicAddress(log.topics.getOrNull(2))
        if (competitionId != expectedCompetitionId) {
            error("CompetitionJoined event competition id does not match prepared intent.")
        }
        if (participant != normalizeAddress(expectedParticipantWallet)) {
            error("CompetitionJoined event participant does not match current wallet.")
        }
        return VerifiedCompetitionJoin(txHash = normalizeHex(txHash))
    }

    override fun prepareSignedSubmissionResult(
        record: SubmissionResultRecord,
    ): PreparedSignedSubmissionResult {
        val signatureHex = signSubmissionResult(record)
        val transaction = prepareSubmissionResultTransaction(record, signatureHex)
        return PreparedSignedSubmissionResult(
            signatureHex = signatureHex,
            signerWalletAddress = contractConfig.operatorWalletAddress,
            transaction = transaction,
            simulationErrorMessage = simulateSubmissionResultTransaction(
                record = record,
                transaction = transaction,
            ),
        )
    }

    override fun verifySubmissionResultTransaction(
        txHash: String,
        expectedParticipantWallet: String,
        record: SubmissionResultRecord,
        signatureHex: String,
    ): VerifiedSubmissionRecording {
        val transaction = loadTransaction(txHash)
        val receipt = loadSuccessfulReceipt(txHash)
        val expectedPrepared = prepareSubmissionResultTransaction(record, signatureHex)
        validateUserSubmittedTransaction(
            transaction = transaction,
            expectedFrom = expectedParticipantWallet,
            expectedTo = expectedPrepared.to,
            expectedData = expectedPrepared.data,
            expectedValue = hexToBigInteger(expectedPrepared.valueHex),
        )
        val log = findEventLog(receipt, SUBMISSION_RECORDED_EVENT_SIGNATURE)
        val competitionId = Numeric.toBigInt(log.topics.getOrNull(1)).longValueExact()
        val submissionId = Numeric.toBigInt(log.topics.getOrNull(2)).longValueExact()
        val participant = topicAddress(log.topics.getOrNull(3))
        if (competitionId != record.competitionId) {
            error("SubmissionResultRecorded event competition id does not match prepared submission.")
        }
        if (submissionId != record.onchainSubmissionId) {
            error("SubmissionResultRecorded event submission id does not match prepared submission.")
        }
        if (participant != normalizeAddress(expectedParticipantWallet)) {
            error("SubmissionResultRecorded event participant does not match current wallet.")
        }
        val values = FunctionReturnDecoder.decode(log.data, SUBMISSION_RECORDED_EVENT_PARAMETERS)
        val submissionHash = values.bytes32Value(0)
        val codeHash = values.bytes32Value(1)
        val challengeHash = values.bytes32Value(2)
        val resultHash = values.bytes32Value(3)
        val sandboxImageHash = values.bytes32Value(4)
        val runtimeMs = values.uintValue(5)
        val memoryUsedKb = values.uintValue(6)
        val consensusNodes = values.uint16Value(7)
        if (
            submissionHash != normalizeHex(record.submissionHash) ||
            codeHash != normalizeHex(record.codeHash) ||
            challengeHash != normalizeHex(record.challengeHash) ||
            resultHash != normalizeHex(record.resultHash) ||
            sandboxImageHash != normalizeHex(record.sandboxImageHash) ||
            runtimeMs != BigInteger.valueOf(record.runtimeMs.toLong()) ||
            memoryUsedKb != BigInteger.valueOf(record.memoryUsedKb.toLong()) ||
            consensusNodes != BigInteger.valueOf(record.consensusNodes.toLong())
        ) {
            error("SubmissionResultRecorded event payload does not match prepared submission.")
        }
        return VerifiedSubmissionRecording(txHash = normalizeHex(txHash))
    }

    override fun prepareSettleCompetition(competitionId: Long): PreparedCompetitionTransaction {
        val function = Function(
            METHOD_SETTLE_COMPETITION,
            listOf(Uint256(BigInteger.valueOf(competitionId))),
            emptyList(),
        )
        return PreparedCompetitionTransaction(
            to = contractConfig.proxyAddress,
            data = FunctionEncoder.encode(function),
            valueHex = ZERO_HEX,
        )
    }

    override fun verifySettleCompetitionTransaction(
        txHash: String,
        expectedSenderWallet: String,
        expectedCompetitionId: Long,
    ): VerifiedCompetitionSettlement {
        val transaction = loadTransaction(txHash)
        val receipt = loadSuccessfulReceipt(txHash)
        val expectedPrepared = prepareSettleCompetition(expectedCompetitionId)
        validateUserSubmittedTransaction(
            transaction = transaction,
            expectedFrom = expectedSenderWallet,
            expectedTo = expectedPrepared.to,
            expectedData = expectedPrepared.data,
            expectedValue = hexToBigInteger(expectedPrepared.valueHex),
        )
        val log = findEventLog(receipt, SETTLED_EVENT_SIGNATURE)
        val competitionId = Numeric.toBigInt(log.topics.getOrNull(1)).longValueExact()
        val winnerWalletAddress = topicAddress(log.topics.getOrNull(2))
        if (competitionId != expectedCompetitionId) {
            error("CompetitionSettled event competition id does not match prepared settlement.")
        }
        return VerifiedCompetitionSettlement(
            txHash = normalizeHex(txHash),
            winnerWalletAddress = winnerWalletAddress,
        )
    }

    override fun prepareCancelCompetition(competitionId: Long): PreparedCompetitionTransaction {
        val function = Function(
            METHOD_CANCEL_COMPETITION,
            listOf(Uint256(BigInteger.valueOf(competitionId))),
            emptyList(),
        )
        return PreparedCompetitionTransaction(
            to = contractConfig.proxyAddress,
            data = FunctionEncoder.encode(function),
            valueHex = ZERO_HEX,
        )
    }

    override fun verifyCancelCompetitionTransaction(
        txHash: String,
        expectedSenderWallet: String,
        expectedCompetitionId: Long,
    ): VerifiedCompetitionCancellation {
        val transaction = loadTransaction(txHash)
        val receipt = loadSuccessfulReceipt(txHash)
        val expectedPrepared = prepareCancelCompetition(expectedCompetitionId)
        validateUserSubmittedTransaction(
            transaction = transaction,
            expectedFrom = expectedSenderWallet,
            expectedTo = expectedPrepared.to,
            expectedData = expectedPrepared.data,
            expectedValue = hexToBigInteger(expectedPrepared.valueHex),
        )
        val log = findEventLog(receipt, CANCELLED_EVENT_SIGNATURE)
        val competitionId = Numeric.toBigInt(log.topics.getOrNull(1)).longValueExact()
        if (competitionId != expectedCompetitionId) {
            error("CompetitionCancelled event competition id does not match prepared cancellation.")
        }
        return VerifiedCompetitionCancellation(txHash = normalizeHex(txHash))
    }

    override fun close() {
        web3j.shutdown()
    }

    private fun prepareSubmissionResultTransaction(
        record: SubmissionResultRecord,
        signatureHex: String,
    ): PreparedCompetitionTransaction {
        val function = Function(
            METHOD_RECORD_SUBMISSION_RESULT,
            listOf(
                Uint256(BigInteger.valueOf(record.competitionId)),
                Uint256(BigInteger.valueOf(record.onchainSubmissionId)),
                Bytes32(bytes32(record.submissionHash)),
                Bytes32(bytes32(record.codeHash)),
                Bytes32(bytes32(record.challengeHash)),
                Bytes32(bytes32(record.resultHash)),
                Bytes32(bytes32(record.sandboxImageHash)),
                Uint32(BigInteger.valueOf(record.runtimeMs.coerceAtLeast(0).toLong())),
                Uint32(BigInteger.valueOf(record.memoryUsedKb.coerceAtLeast(0).toLong())),
                Uint16(BigInteger.valueOf(record.consensusNodes.coerceAtLeast(0).toLong())),
                DynamicBytes(Numeric.hexStringToByteArray(normalizeHex(signatureHex))),
            ),
            emptyList(),
        )
        return PreparedCompetitionTransaction(
            to = contractConfig.proxyAddress,
            data = FunctionEncoder.encode(function),
            valueHex = ZERO_HEX,
        )
    }

    private fun simulateSubmissionResultTransaction(
        record: SubmissionResultRecord,
        transaction: PreparedCompetitionTransaction,
    ): String? {
        val request = EthCallTransaction.createEthCallTransaction(
            normalizeAddress(record.participantWalletAddress),
            normalizeAddress(transaction.to),
            normalizeHex(transaction.data),
        )
        val response = web3j.ethCall(request, DefaultBlockParameterName.LATEST).send()
        if (!response.isReverted) {
            return null
        }
        val revertData = extractRevertData(response)
        val decodedReason = decodeSubmissionRevertReason(revertData)
        val fallbackReason = response.revertReason?.trim().orEmpty()
        return listOfNotNull(
            decodedReason,
            fallbackReason.takeIf { it.isNotBlank() && it != decodedReason },
        ).firstOrNull()
            ?: "On-chain simulation reverted without a readable reason."
    }

    private fun signSubmissionResult(record: SubmissionResultRecord): String {
        val digest = submissionResultDigest(
            contractAddress = contractConfig.proxyAddress,
            chainId = blockchainConfig.chainId,
            record = record,
        )
        val signatureData = Sign.signMessage(
            Numeric.hexStringToByteArray(digest),
            operatorCredentials.ecKeyPair,
            false,
        )
        return signatureHex(signatureData)
    }

    private fun prepareApprovalTransaction(
        tokenAddress: String,
        amount: BigInteger,
    ): PreparedCompetitionTransaction {
        val function = Function(
            METHOD_APPROVE,
            listOf(
                Address(normalizeAddress(contractConfig.proxyAddress)),
                Uint256(amount),
            ),
            emptyList(),
        )
        return PreparedCompetitionTransaction(
            to = normalizeAddress(tokenAddress),
            data = FunctionEncoder.encode(function),
            valueHex = ZERO_HEX,
        )
    }

    private fun validateUserSubmittedTransaction(
        transaction: Transaction,
        expectedFrom: String,
        expectedTo: String,
        expectedData: String,
        expectedValue: BigInteger,
    ) {
        if (normalizeAddress(transaction.from) != normalizeAddress(expectedFrom)) {
            error("Transaction sender does not match the authenticated wallet.")
        }
        if (normalizeAddress(transaction.to) != normalizeAddress(expectedTo)) {
            error("Transaction target contract does not match prepared competition intent.")
        }
        if (normalizeHex(transaction.input) != normalizeHex(expectedData)) {
            error("Transaction calldata does not match prepared competition intent.")
        }
        if ((transaction.value ?: BigInteger.ZERO) != expectedValue) {
            error("Transaction value does not match prepared competition intent.")
        }
    }

    private fun loadTransaction(txHash: String): Transaction {
        return web3j.ethGetTransactionByHash(normalizeHex(txHash)).send().transaction.orElse(null)
            ?: error("Ethereum transaction was not found.")
    }

    private fun loadSuccessfulReceipt(txHash: String): TransactionReceipt {
        val receipt = web3j.ethGetTransactionReceipt(normalizeHex(txHash)).send().transactionReceipt.orElse(null)
            ?: error("Transaction receipt is not available yet.")
        val status = receipt.status.orEmpty().lowercase()
        require(status == "0x1" || status == "1") {
            "Transaction reverted with status ${receipt.status}."
        }
        return receipt
    }

    private fun findEventLog(receipt: TransactionReceipt, signature: String): Log {
        return receipt.logs.firstOrNull { log ->
            normalizeAddress(log.address) == normalizeAddress(contractConfig.proxyAddress) &&
                normalizeHex(log.topics.firstOrNull()) == normalizeHex(signature)
        } ?: error("Expected BlockchainTestContract event was not found in transaction receipt.")
    }

}

private fun extractRevertData(response: org.web3j.protocol.core.methods.response.EthCall): String? {
    val errorData = response.error?.data?.trim().orEmpty()
    HEX_DATA_REGEX.find(errorData)?.value?.let { return normalizeHex(it) }
    val value = response.value?.trim().orEmpty()
    if (value.startsWith("0x") && value.length >= 10) {
        return normalizeHex(value)
    }
    return null
}

@Suppress("UNCHECKED_CAST")
private fun decodeSubmissionRevertReason(revertData: String?): String? {
    val normalized = revertData?.trim()?.lowercase()?.takeIf { it.startsWith("0x") && it.length >= 10 } ?: return null
    val selector = normalized.take(10)
    val encodedArguments = "0x${normalized.removePrefix("0x").drop(8)}"
    return when (selector) {
        customErrorSelector("ParticipantNotJoined()") -> "ParticipantNotJoined(): wallet is not joined to this competition."
        customErrorSelector("SubmissionWindowClosed()") -> "SubmissionWindowClosed(): the submission deadline has already passed on-chain."
        customErrorSelector("InvalidSignature()") -> "InvalidSignature(): operator signature does not match the submission payload."
        customErrorSelector("CompetitionNotOpen()") -> "CompetitionNotOpen(): the competition is no longer open for recording submissions."
        customErrorSelector("InvalidCompetition()") -> "InvalidCompetition(): competition id was rejected by the contract."
        customErrorSelector("InvalidSubmission()") -> "InvalidSubmission(): submission payload was rejected by the contract."
        customErrorSelector("InvalidHash()") -> "InvalidHash(): one of the prepared bytes32 hashes was rejected by the contract."
        customErrorSelector("SubmissionAlreadyRecorded(uint256)") -> {
            val decodedId = runCatching {
                FunctionReturnDecoder.decode(
                    encodedArguments,
                    listOf(object : TypeReference<Uint256>() {} as TypeReference<Type<*>>),
                ).uintValue(0).longValueExact()
            }.getOrNull()
            if (decodedId != null) {
                "SubmissionAlreadyRecorded($decodedId): this on-chain submission id is already used."
            } else {
                "SubmissionAlreadyRecorded(): this on-chain submission id is already used."
            }
        }

        customErrorSelector("SandboxImageNotApproved(bytes32)") -> {
            val decodedHash = runCatching {
                FunctionReturnDecoder.decode(
                    encodedArguments,
                    listOf(object : TypeReference<Bytes32>() {} as TypeReference<Type<*>>),
                ).bytes32Value(0)
            }.getOrNull()
            if (!decodedHash.isNullOrBlank()) {
                "SandboxImageNotApproved($decodedHash): sandbox image hash is not approved by the contract."
            } else {
                "SandboxImageNotApproved(): sandbox image hash is not approved by the contract."
            }
        }

        else -> null
    }
}

private fun customErrorSelector(signature: String): String {
    return normalizeHex(Hash.sha3String(signature)).take(10)
}

internal fun bytes32HashHex(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.matches(HEX_32_BYTES_REGEX)) {
        return normalizeHex(value)
    }
    val digest = Hash.sha3(value.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun bytes32(raw: String): ByteArray = Numeric.hexStringToByteArray(bytes32HashHex(raw))

private fun normalizeHex(value: String?): String {
    val raw = value?.trim().orEmpty().removePrefix("0x").lowercase()
    require(raw.isNotBlank()) { "Hex value is required." }
    return "0x$raw"
}

private fun normalizeAddress(value: String?): String {
    val raw = value?.trim().orEmpty().removePrefix("0x").lowercase()
    require(raw.length == 40) { "Ethereum address must be 20 bytes." }
    return "0x$raw"
}

private fun hexToBytes32(hexValue: String): ByteArray {
    val bytes = Numeric.hexStringToByteArray(normalizeHex(hexValue))
    require(bytes.size == 32) { "Expected 32-byte hex value, got ${bytes.size} bytes." }
    return bytes
}

private fun topicAddress(topic: String?): String {
    val normalized = normalizeHex(topic).removePrefix("0x")
    require(normalized.length == 64) { "Invalid indexed address topic." }
    return "0x${normalized.takeLast(40)}"
}

private fun toPrefixedHex(value: BigInteger): String {
    require(value >= BigInteger.ZERO) { "Hex value must be non-negative." }
    return "0x${value.toString(16)}"
}

private fun hexToBigInteger(valueHex: String): BigInteger {
    return Numeric.toBigInt(valueHex)
}

private fun List<Type<*>>.uintValue(index: Int): BigInteger {
    return (get(index) as NumericType).value
}

private fun List<Type<*>>.uint16Value(index: Int): BigInteger {
    return (get(index) as NumericType).value
}

private fun List<Type<*>>.addressValue(index: Int): String {
    return (get(index) as Address).value
}

private fun List<Type<*>>.bytes32Value(index: Int): String {
    return Numeric.toHexString((get(index) as Bytes32).value).lowercase()
}

private fun paymentTokenAddress(paymentAsset: PaymentAssetConfig): String {
    return when (paymentAsset.kind) {
        PaymentAssetKind.Native -> ZERO_ADDRESS
        PaymentAssetKind.Erc20 -> paymentAsset.tokenAddress ?: error("ERC-20 payment asset must define tokenAddress.")
    }.let(::normalizeAddress)
}

private fun expectedTransactionValue(paymentAsset: PaymentAssetConfig, amount: BigInteger): BigInteger {
    return when (paymentAsset.kind) {
        PaymentAssetKind.Native -> amount
        PaymentAssetKind.Erc20 -> BigInteger.ZERO
    }
}

@Suppress("UNCHECKED_CAST")
private val CREATED_EVENT_PARAMETERS: List<TypeReference<Type<*>>> = listOf(
    object : TypeReference<Address>() {} as TypeReference<Type<*>>,
    object : TypeReference<Uint256>() {} as TypeReference<Type<*>>,
    object : TypeReference<Uint256>() {} as TypeReference<Type<*>>,
    object : TypeReference<Uint256>() {} as TypeReference<Type<*>>,
    object : TypeReference<Uint256>() {} as TypeReference<Type<*>>,
    object : TypeReference<Uint256>() {} as TypeReference<Type<*>>,
)

private val CREATED_EVENT_SIGNATURE = EventEncoder.encode(
    Event(
        "CompetitionCreated",
        mutableListOf<TypeReference<out Type<*>>>(
            object : TypeReference<Uint256>(true) {},
            object : TypeReference<Bytes32>(true) {},
            object : TypeReference<Address>(true) {},
        ).apply {
            addAll(CREATED_EVENT_PARAMETERS)
        },
    ),
)

private val JOINED_EVENT_SIGNATURE = EventEncoder.encode(
    Event(
        "CompetitionJoined",
        listOf(
            object : TypeReference<Uint256>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Uint256>() {},
        ),
    ),
)

private val SETTLED_EVENT_SIGNATURE = EventEncoder.encode(
    Event(
        "CompetitionSettled",
        listOf(
            object : TypeReference<Uint256>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>() {},
            object : TypeReference<Uint256>() {},
            object : TypeReference<Uint256>() {},
            object : TypeReference<Uint256>() {},
        ),
    ),
)

private val CANCELLED_EVENT_SIGNATURE = EventEncoder.encode(
    Event(
        "CompetitionCancelled",
        listOf(
            object : TypeReference<Uint256>(true) {},
            object : TypeReference<Uint256>() {},
        ),
    ),
)

@Suppress("UNCHECKED_CAST")
private val SUBMISSION_RECORDED_EVENT_PARAMETERS: List<TypeReference<Type<*>>> = listOf(
    object : TypeReference<Bytes32>() {} as TypeReference<Type<*>>,
    object : TypeReference<Bytes32>() {} as TypeReference<Type<*>>,
    object : TypeReference<Bytes32>() {} as TypeReference<Type<*>>,
    object : TypeReference<Bytes32>() {} as TypeReference<Type<*>>,
    object : TypeReference<Bytes32>() {} as TypeReference<Type<*>>,
    object : TypeReference<Uint32>() {} as TypeReference<Type<*>>,
    object : TypeReference<Uint32>() {} as TypeReference<Type<*>>,
    object : TypeReference<Uint16>() {} as TypeReference<Type<*>>,
)

private val SUBMISSION_RECORDED_EVENT_SIGNATURE = EventEncoder.encode(
    Event(
        "SubmissionResultRecorded",
        mutableListOf<TypeReference<out Type<*>>>(
            object : TypeReference<Uint256>(true) {},
            object : TypeReference<Uint256>(true) {},
            object : TypeReference<Address>(true) {},
        ).apply {
            addAll(SUBMISSION_RECORDED_EVENT_PARAMETERS)
        },
    ),
)

private fun submissionResultDigest(
    contractAddress: String,
    chainId: Long,
    record: SubmissionResultRecord,
): String {
    val encoded = FunctionEncoder.encodeConstructor(
        listOf(
            Address(normalizeAddress(contractAddress)),
            Uint256(BigInteger.valueOf(chainId)),
            Uint256(BigInteger.valueOf(record.competitionId)),
            Uint256(BigInteger.valueOf(record.onchainSubmissionId)),
            Address(normalizeAddress(record.participantWalletAddress)),
            Bytes32(bytes32(record.submissionHash)),
            Bytes32(bytes32(record.codeHash)),
            Bytes32(bytes32(record.challengeHash)),
            Bytes32(bytes32(record.resultHash)),
            Bytes32(bytes32(record.sandboxImageHash)),
            Uint32(BigInteger.valueOf(record.runtimeMs.coerceAtLeast(0).toLong())),
            Uint32(BigInteger.valueOf(record.memoryUsedKb.coerceAtLeast(0).toLong())),
            Uint16(BigInteger.valueOf(record.consensusNodes.coerceAtLeast(0).toLong())),
        ),
    )
    return normalizeHex(Hash.sha3(encoded))
}

private fun signatureHex(signature: Sign.SignatureData): String {
    val bytes = ByteArray(65)
    System.arraycopy(signature.r, 0, bytes, 0, 32)
    System.arraycopy(signature.s, 0, bytes, 32, 32)
    bytes[64] = signature.v.firstOrNull() ?: 0
    return Numeric.toHexString(bytes).lowercase()
}

private val HEX_32_BYTES_REGEX = Regex("^(0x)?[0-9a-fA-F]{64}$")
private val HEX_DATA_REGEX = Regex("0x[0-9a-fA-F]{8,}")
private const val METHOD_CREATE_COMPETITION = "createCompetition"
private const val METHOD_JOIN_COMPETITION = "joinCompetition"
private const val METHOD_APPROVE = "approve"
private const val METHOD_SETTLE_COMPETITION = "settleCompetition"
private const val METHOD_CANCEL_COMPETITION = "cancelCompetition"
private const val METHOD_RECORD_SUBMISSION_RESULT = "recordSubmissionResult"
private const val DEFAULT_GAS_LIMIT = 700_000L
private const val DEFAULT_PREPARE_INTENT_TTL_SECONDS = 900
private const val ZERO_HEX = "0x0"
private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
