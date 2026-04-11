package pl.dawidszczesniak.blockchain_platform.feature.problems.anchor

import java.math.BigInteger
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig

internal data class AnchorTransactionResult(
    val anchored: Boolean,
    val txHash: String? = null,
    val error: String? = null,
)

internal interface BlockchainAnchorClient {
    fun anchorSubmission(commitmentHash: String, submissionId: Long): AnchorTransactionResult

    fun close()
}

internal class EthereumBlockchainAnchorClient(
    private val blockchainConfig: BlockchainConfig,
    private val anchorConfig: AnchorConfig,
) : BlockchainAnchorClient {
    private val web3j: Web3j? = blockchainConfig.ethRpcUrl?.let { rpcUrl ->
        Web3j.build(HttpService(rpcUrl))
    }
    private val credentials: Credentials? = anchorConfig.signerPrivateKey
        ?.takeIf { it.isNotBlank() }
        ?.let { Credentials.create(it.removePrefix("0x")) }

    override fun anchorSubmission(commitmentHash: String, submissionId: Long): AnchorTransactionResult {
        if (!anchorConfig.enabled) {
            return AnchorTransactionResult(anchored = false, error = "Ethereum anchoring is disabled.")
        }
        val client = web3j ?: return AnchorTransactionResult(
            anchored = false,
            error = "ETH RPC client is unavailable.",
        )
        val signer = credentials ?: return AnchorTransactionResult(
            anchored = false,
            error = "Anchor signer credentials are unavailable.",
        )
        val chainId = anchorConfig.chainId ?: return AnchorTransactionResult(
            anchored = false,
            error = "ETH chain id is not configured.",
        )
        val contractAddress = anchorConfig.contractAddress ?: return AnchorTransactionResult(
            anchored = false,
            error = "Anchor contract address is not configured.",
        )

        return runCatching {
            val function = Function(
                anchorConfig.contractMethodName,
                listOf(
                    Bytes32(hexToBytes32(commitmentHash)),
                    Uint256(BigInteger.valueOf(submissionId)),
                ),
                emptyList(),
            )
            val data = FunctionEncoder.encode(function)
            val nonce = client.ethGetTransactionCount(
                signer.address,
                DefaultBlockParameterName.PENDING,
            ).send().transactionCount
            val gasPrice = anchorConfig.gasPriceWei?.toBigInteger()
                ?: client.ethGasPrice().send().gasPrice
            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                BigInteger.valueOf(anchorConfig.gasLimit),
                contractAddress,
                data,
            )
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, signer)
            val signedHex = Numeric.toHexString(signedMessage)
            val sendResponse = client.ethSendRawTransaction(signedHex).send()
            if (sendResponse.hasError()) {
                val reason = sendResponse.error?.message?.ifBlank { null }
                    ?: "eth_sendRawTransaction failed."
                return@runCatching AnchorTransactionResult(
                    anchored = false,
                    error = reason,
                )
            }
            val txHash = sendResponse.transactionHash
            val receipt = waitForReceipt(client, txHash)
            if (receipt == null) {
                return@runCatching AnchorTransactionResult(
                    anchored = false,
                    txHash = txHash,
                    error = "Transaction sent but receipt was not confirmed in time.",
                )
            }
            val status = receipt.status.orEmpty().lowercase()
            if (status == "0x1" || status == "1") {
                AnchorTransactionResult(
                    anchored = true,
                    txHash = txHash,
                )
            } else {
                AnchorTransactionResult(
                    anchored = false,
                    txHash = txHash,
                    error = "Transaction reverted with status ${receipt.status}.",
                )
            }
        }.getOrElse { error ->
            AnchorTransactionResult(
                anchored = false,
                error = error.message?.ifBlank { null } ?: "Ethereum anchoring failed.",
            )
        }
    }

    override fun close() {
        web3j?.shutdown()
    }

    private fun waitForReceipt(client: Web3j, txHash: String): TransactionReceipt? {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt <= anchorConfig.receiptTimeoutMs) {
            val receipt = runCatching {
                client.ethGetTransactionReceipt(txHash).send().transactionReceipt.orElse(null)
            }.getOrNull()
            if (receipt != null) {
                return receipt
            }
            Thread.sleep(anchorConfig.receiptPollIntervalMs)
        }
        return null
    }
}

private fun hexToBytes32(hexValue: String): ByteArray {
    val bytes = Numeric.hexStringToByteArray(hexValue)
    require(bytes.size == 32) { "Expected 32-byte hex value, got ${bytes.size} bytes." }
    return bytes
}
