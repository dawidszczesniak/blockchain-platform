package pl.dawidszczesniak.blockchain_platform.feature.problems.anchor

import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

internal data class MerkleTreeResult(
    val rootHash: String,
    val proofsByIndex: Map<Int, List<String>>,
)

internal object MerkleTreeBuilder {
    fun buildFromLeaves(leafHashes: List<String>): MerkleTreeResult {
        require(leafHashes.isNotEmpty()) { "Merkle tree requires at least one leaf." }
        val leaves = leafHashes.map(::hexToBytes32)
        val proofs = leaves.indices.associateWith { mutableListOf<String>() }.toMutableMap()

        leaves.indices.forEach { index ->
            var currentIndex = index
            var layer = leaves
            while (layer.size > 1) {
                val expanded = if (layer.size % 2 == 1) {
                    layer + listOf(layer.last())
                } else {
                    layer
                }
                val siblingIndex = if (currentIndex % 2 == 0) currentIndex + 1 else currentIndex - 1
                proofs.getValue(index).add(toHex(expanded[siblingIndex]))
                currentIndex /= 2
                layer = nextLayer(expanded)
            }
        }

        var rootLayer = leaves
        while (rootLayer.size > 1) {
            val expanded = if (rootLayer.size % 2 == 1) {
                rootLayer + listOf(rootLayer.last())
            } else {
                rootLayer
            }
            rootLayer = nextLayer(expanded)
        }

        return MerkleTreeResult(
            rootHash = toHex(rootLayer.single()),
            proofsByIndex = proofs.mapValues { (_, proof) -> proof.toList() },
        )
    }

    private fun nextLayer(layer: List<ByteArray>): List<ByteArray> {
        return layer.chunked(2).map { pair ->
            val left = pair[0]
            val right = pair[1]
            Hash.sha3(left + right)
        }
    }
}

private fun hexToBytes32(hexValue: String): ByteArray {
    val bytes = Numeric.hexStringToByteArray(hexValue)
    require(bytes.size == 32) { "Expected 32-byte hash leaf, got ${bytes.size} bytes." }
    return bytes
}

private fun toHex(bytes: ByteArray): String = Numeric.toHexString(bytes).lowercase()
