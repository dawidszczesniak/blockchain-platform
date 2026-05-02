package pl.dawidszczesniak.blockchain_platform.feature.problems.onchain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import pl.dawidszczesniak.blockchain_platform.feature.auth.BlockchainConfig

class BlockchainPlatformContractConfigTest {
    @Test
    fun `requires rpc url because BlockchainTestContract is always on-chain`() {
        val error = assertFailsWith<IllegalArgumentException> {
            BlockchainPlatformContractConfig.fromEnvironment(
                env = emptyMap(),
                blockchainConfig = localBlockchainConfig(ethRpcUrl = null),
            )
        }

        assertEquals(
            "ETH_RPC_URL must be configured because BlockchainTestContract is always used on-chain.",
            error.message,
        )
    }

    @Test
    fun `requires contract address and operator key`() {
        val missingContract = assertFailsWith<IllegalArgumentException> {
            BlockchainPlatformContractConfig.fromEnvironment(
                env = emptyMap(),
                blockchainConfig = localBlockchainConfig(),
            )
        }
        assertEquals(
            "ETH_PLATFORM_PROXY_ADDRESS must be configured because BlockchainTestContract is always used on-chain.",
            missingContract.message,
        )

        val missingOperatorKey = assertFailsWith<IllegalArgumentException> {
            BlockchainPlatformContractConfig.fromEnvironment(
                env = mapOf(
                    "ETH_PLATFORM_PROXY_ADDRESS" to "0x1111111111111111111111111111111111111111",
                ),
                blockchainConfig = localBlockchainConfig(),
            )
        }
        assertEquals(
            "ETH_PLATFORM_OPERATOR_PRIVATE_KEY must be configured because settlement and accepted result recording are always on-chain.",
            missingOperatorKey.message,
        )
    }

    @Test
    fun `loads unified BlockchainTestContract configuration`() {
        val config = BlockchainPlatformContractConfig.fromEnvironment(
            env = mapOf(
                "ETH_PLATFORM_PROXY_ADDRESS" to "0x1111111111111111111111111111111111111111",
                "ETH_PLATFORM_OPERATOR_PRIVATE_KEY" to "0xabc",
            ),
            blockchainConfig = localBlockchainConfig(),
        )

        assertEquals("0x1111111111111111111111111111111111111111", config.proxyAddress)
        assertEquals("0xabc", config.operatorPrivateKey)
        assertEquals(30_000L, config.autoSettlePollIntervalMs)
        assertEquals("https://sepolia.etherscan.io/tx/0xtx", config.explorerTxUrl("0xtx"))
    }

    private fun localBlockchainConfig(
        ethRpcUrl: String? = "http://localhost:8545",
    ) = BlockchainConfig(
        appEnvironment = "local",
        chainId = 11155111L,
        networkName = "Ethereum Sepolia",
        nativeCurrencyName = "Sepolia Ether",
        nativeCurrencySymbol = "ETH",
        ethRpcUrl = ethRpcUrl,
        explorerBaseUrl = "https://sepolia.etherscan.io",
    )
}
