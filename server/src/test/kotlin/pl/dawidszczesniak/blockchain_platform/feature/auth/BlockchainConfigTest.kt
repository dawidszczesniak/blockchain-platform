package pl.dawidszczesniak.blockchain_platform.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlockchainConfigTest {
    @Test
    fun `local defaults to Sepolia`() {
        val config = BlockchainConfig.fromEnvironment(
            mapOf(
                "APP_ENV" to "local",
            )
        )

        assertEquals(11155111L, config.chainId)
        assertEquals("Ethereum Sepolia", config.networkName)
        assertEquals("ETH", config.nativeCurrencySymbol)
        assertEquals("https://sepolia.etherscan.io", config.explorerBaseUrl)
    }

    @Test
    fun `staging requires backend rpc url`() {
        assertFailsWith<IllegalStateException> {
            BlockchainConfig.fromEnvironment(
                mapOf(
                    "APP_ENV" to "staging",
                )
            )
        }
    }

    @Test
    fun `wallet chain must match configured chain`() {
        val config = BlockchainConfig.fromEnvironment(
            mapOf(
                "APP_ENV" to "staging",
                "ETH_RPC_URL" to "https://rpc.example.com",
            )
        )

        assertFailsWith<IllegalArgumentException> {
            config.validateChainIdForEnvironment(84532L)
        }
    }
}
