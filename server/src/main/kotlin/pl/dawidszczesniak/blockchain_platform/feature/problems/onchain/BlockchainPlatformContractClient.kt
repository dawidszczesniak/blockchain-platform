package pl.dawidszczesniak.blockchain_platform.feature.problems.onchain

import java.math.BigInteger
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint64
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.Log
import org.web3j.protocol.core.methods.response.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetConfig
import pl.dawidszczesniak.blockchain_platform.feature.platform.PaymentAssetKind
import pl.dawidszczesniak.blockchain_platform.feature.problems.atomicAmountToBigInteger

internal data class BlockchainPlatformContractConfig(
    val proxyAddress: String,
    val operatorPrivateKey: String,
    val gasLimit: Long,
    val gasPriceWei: Long?,
    val receiptTimeoutMs: Long,
    val receiptPollIntervalMs: Long,
    val explorerTxBaseUrl: String?,
    val prepareIntentTtlSeconds: Int,
    val autoSettlePollIntervalMs: Long,
) {
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
                "ETH_PLATFORM_OPERATOR_PRIVATE_KEY must be configured because settlement and accepted result recording are always on-chain."
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
                receiptTimeoutMs = env["ETH_PLATFORM_RECEIPT_TIMEOUT_MS"]
                    ?.toLongOrNull()
                    ?.coerceIn(5_000L, 300_000L)
                    ?: DEFAULT_RECEIPT_TIMEOUT_MS,
                receiptPollIntervalMs = env["ETH_PLATFORM_RECEIPT_POLL_INTERVAL_MS"]
                    ?.toLongOrNull()
                    ?.coerceIn(500L, 30_000L)
                    ?: DEFAULT_RECEIPT_POLL_INTERVAL_MS,
                explorerTxBaseUrl = blockchainConfig.explorerBaseUrl?.let { "$it/tx" },
                prepareIntentTtlSeconds = env["ETH_PLATFORM_PREPARE_INTENT_TTL_SECONDS"]
                    ?.toIntOrNull()
                    ?.coerceIn(60, 3_600)
                    ?: DEFAULT_PREPARE_INTENT_TTL_SECONDS,
                autoSettlePollIntervalMs = env["ETH_PLATFORM_AUTO_SETTLE_POLL_INTERVAL_MS"]
                    ?.toLongOrNull()
                    ?.coerceIn(5_000L, 300_000L)
                    ?: DEFAULT_AUTO_SETTLE_POLL_INTERVAL_MS,
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
    val submissionId: Long,
    val participantWalletAddress: String,
    val submissionHash: String,
    val codeHash: String,
    val testsHash: String,
    val resultHash: String,
    val sandboxImageHash: String,
    val runtimeMs: Int,
    val memoryUsedKb: Int,
    val consensusNodes: Int,
)

internal data class BlockchainPlatformWriteResult(
    val success: Boolean,
    val txHash: String? = null,
    val error: String? = null,
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

    fun settleCompetition(competitionId: Long): BlockchainPlatformWriteResult

    fun cancelCompetition(competitionId: Long): BlockchainPlatformWriteResult

    fun recordSubmissionResult(record: SubmissionResultRecord): BlockchainPlatformWriteResult

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

    override fun settleCompetition(competitionId: Long): BlockchainPlatformWriteResult {
        val function = Function(
            METHOD_SETTLE_COMPETITION,
            listOf(Uint256(BigInteger.valueOf(competitionId))),
            emptyList(),
        )
        return submitBackendWriteTransaction(function, "Competition settlement failed.")
    }

    override fun cancelCompetition(competitionId: Long): BlockchainPlatformWriteResult {
        val function = Function(
            METHOD_CANCEL_COMPETITION,
            listOf(Uint256(BigInteger.valueOf(competitionId))),
            emptyList(),
        )
        return submitBackendWriteTransaction(function, "Competition cancellation failed.")
    }

    override fun recordSubmissionResult(record: SubmissionResultRecord): BlockchainPlatformWriteResult {
        val function = Function(
            METHOD_RECORD_SUBMISSION_RESULT,
            listOf(
                Uint256(BigInteger.valueOf(record.competitionId)),
                Uint256(BigInteger.valueOf(record.submissionId)),
                Address(normalizeAddress(record.participantWalletAddress)),
                Bytes32(bytes32(record.submissionHash)),
                Bytes32(bytes32(record.codeHash)),
                Bytes32(bytes32(record.testsHash)),
                Bytes32(bytes32(record.resultHash)),
                Bytes32(bytes32(record.sandboxImageHash)),
                Uint32(BigInteger.valueOf(record.runtimeMs.coerceAtLeast(0).toLong())),
                Uint32(BigInteger.valueOf(record.memoryUsedKb.coerceAtLeast(0).toLong())),
                Uint16(BigInteger.valueOf(record.consensusNodes.coerceAtLeast(0).toLong())),
            ),
            emptyList(),
        )
        return submitBackendWriteTransaction(function, "On-chain submission result write failed.")
    }

    override fun close() {
        web3j.shutdown()
    }

    private fun submitBackendWriteTransaction(
        function: Function,
        fallbackError: String,
    ): BlockchainPlatformWriteResult {
        return runCatching {
            val data = FunctionEncoder.encode(function)
            val nonce = web3j.ethGetTransactionCount(
                operatorCredentials.address,
                DefaultBlockParameterName.PENDING,
            ).send().transactionCount
            val gasPrice = contractConfig.gasPriceWei?.toBigInteger()
                ?: web3j.ethGasPrice().send().gasPrice
            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                BigInteger.valueOf(contractConfig.gasLimit),
                contractConfig.proxyAddress,
                data,
            )
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, blockchainConfig.chainId, operatorCredentials)
            val sendResponse = web3j.ethSendRawTransaction(Numeric.toHexString(signedMessage)).send()
            if (sendResponse.hasError()) {
                val reason = sendResponse.error?.message?.ifBlank { null } ?: "eth_sendRawTransaction failed."
                return@runCatching BlockchainPlatformWriteResult(success = false, error = reason)
            }
            val txHash = sendResponse.transactionHash
            val receipt = waitForReceipt(txHash)
                ?: return@runCatching BlockchainPlatformWriteResult(
                    success = false,
                    txHash = txHash,
                    error = "Transaction sent but receipt was not confirmed in time.",
                )
            val status = receipt.status.orEmpty().lowercase()
            if (status == "0x1" || status == "1") {
                BlockchainPlatformWriteResult(success = true, txHash = txHash)
            } else {
                BlockchainPlatformWriteResult(
                    success = false,
                    txHash = txHash,
                    error = "Transaction reverted with status ${receipt.status}.",
                )
            }
        }.getOrElse { error ->
            BlockchainPlatformWriteResult(
                success = false,
                error = error.message?.ifBlank { null } ?: fallbackError,
            )
        }
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

    private fun waitForReceipt(txHash: String): TransactionReceipt? {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt <= contractConfig.receiptTimeoutMs) {
            val receipt = runCatching {
                web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt.orElse(null)
            }.getOrNull()
            if (receipt != null) {
                return receipt
            }
            Thread.sleep(contractConfig.receiptPollIntervalMs)
        }
        return null
    }
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
    return (get(index) as Uint256).value
}

private fun List<Type<*>>.addressValue(index: Int): String {
    return (get(index) as Address).value
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

private val HEX_32_BYTES_REGEX = Regex("^(0x)?[0-9a-fA-F]{64}$")
private const val METHOD_CREATE_COMPETITION = "createCompetition"
private const val METHOD_JOIN_COMPETITION = "joinCompetition"
private const val METHOD_APPROVE = "approve"
private const val METHOD_SETTLE_COMPETITION = "settleCompetition"
private const val METHOD_CANCEL_COMPETITION = "cancelCompetition"
private const val METHOD_RECORD_SUBMISSION_RESULT = "recordSubmissionResult"
private const val DEFAULT_GAS_LIMIT = 700_000L
private const val DEFAULT_RECEIPT_TIMEOUT_MS = 120_000L
private const val DEFAULT_RECEIPT_POLL_INTERVAL_MS = 2_000L
private const val DEFAULT_PREPARE_INTENT_TTL_SECONDS = 900
private const val DEFAULT_AUTO_SETTLE_POLL_INTERVAL_MS = 30_000L
private const val ZERO_HEX = "0x0"
private const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
