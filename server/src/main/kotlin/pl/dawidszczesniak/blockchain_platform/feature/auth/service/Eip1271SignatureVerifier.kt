package pl.dawidszczesniak.blockchain_platform.feature.auth.service

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Bytes4
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig

internal class Eip1271SignatureVerifier(
    private val blockchainConfig: BlockchainConfig,
) {
    private val web3j: Web3j? = blockchainConfig.ethRpcUrl?.let { rpcUrl ->
        Web3j.build(HttpService(rpcUrl))
    }

    fun verifyPersonalSign(
        contractAddress: String,
        message: String,
        signatureHex: String,
    ): Boolean {
        val client = web3j ?: return false
        val signatureBytes = runCatching {
            Numeric.hexStringToByteArray(signatureHex.trim())
        }.getOrNull() ?: return false
        if (signatureBytes.size != 65) return false

        return runCatching {
            val deployedCode = client.ethGetCode(contractAddress, DefaultBlockParameterName.LATEST).send().code
            if (deployedCode.isNullOrBlank() || deployedCode == "0x" || deployedCode == "0x0") {
                return false
            }

            val messageHash = Sign.getEthereumMessageHash(message.toByteArray(Charsets.UTF_8))
            val function = Function(
                "isValidSignature",
                listOf(Bytes32(messageHash), DynamicBytes(signatureBytes)),
                listOf(object : TypeReference<Bytes4>() {}),
            )
            val encodedFunction = FunctionEncoder.encode(function)
            val transaction = Transaction.createEthCallTransaction(
                null,
                contractAddress,
                encodedFunction,
            )
            val response = client.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
            if (response.hasError()) {
                return false
            }

            val output = response.value?.trim()?.lowercase().orEmpty()
            output.startsWith(EIP1271_MAGIC_VALUE)
        }.getOrDefault(false)
    }

    fun close() {
        web3j?.shutdown()
    }
}

private const val EIP1271_MAGIC_VALUE = "0x1626ba7e"
