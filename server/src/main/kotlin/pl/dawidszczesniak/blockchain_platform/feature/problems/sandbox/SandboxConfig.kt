package pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox

internal data class SandboxConfig(
    val nodes: List<String>,
    val requestTimeoutMs: Long,
    val connectTimeoutMs: Long,
    val expectedImageHash: String?,
    val requiredConsensus: Int,
    val nodeAttestationSecrets: Map<String, String>,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): SandboxConfig {
            val appEnv = env["APP_ENV"]?.trim()?.lowercase().orEmpty().ifBlank { "local" }
            val isProductionLike = appEnv == "staging" || appEnv == "prod"
            val nodes = env["SANDBOX_NODES"]
                ?.split(',')
                ?.mapNotNull { raw ->
                    raw.trim().takeIf { it.isNotBlank() }?.removeSuffix("/")
                }
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_LOCAL_NODES
            val requestTimeoutMs = env["SANDBOX_REQUEST_TIMEOUT_MS"]
                ?.toLongOrNull()
                ?.coerceIn(1_000L, 120_000L)
                ?: DEFAULT_REQUEST_TIMEOUT_MS
            val connectTimeoutMs = env["SANDBOX_CONNECT_TIMEOUT_MS"]
                ?.toLongOrNull()
                ?.coerceIn(100L, 30_000L)
                ?: DEFAULT_CONNECT_TIMEOUT_MS
            val expectedImageHash = env["SANDBOX_IMAGE_HASH"]
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val requiredConsensus = env["SANDBOX_CONSENSUS_THRESHOLD"]
                ?.toIntOrNull()
                ?.coerceIn(1, nodes.size)
                ?: DEFAULT_REQUIRED_CONSENSUS.coerceAtMost(nodes.size)
            val nodeAttestationSecrets = parseNodeSecrets(env["SANDBOX_NODE_SECRETS"]).ifEmpty {
                if (appEnv == "local") {
                    DEFAULT_LOCAL_NODE_ATTESTATION_SECRETS
                } else {
                    emptyMap()
                }
            }

            if (isProductionLike && nodeAttestationSecrets.isEmpty()) {
                error("SANDBOX_NODE_SECRETS must be configured in staging/prod.")
            }
            if (nodeAttestationSecrets.size < requiredConsensus) {
                error(
                    "SANDBOX_NODE_SECRETS must define at least $requiredConsensus node secrets " +
                        "to satisfy SANDBOX_CONSENSUS_THRESHOLD."
                )
            }

            return SandboxConfig(
                nodes = nodes,
                requestTimeoutMs = requestTimeoutMs,
                connectTimeoutMs = connectTimeoutMs,
                expectedImageHash = expectedImageHash,
                requiredConsensus = requiredConsensus,
                nodeAttestationSecrets = nodeAttestationSecrets,
            )
        }
    }
}

private val DEFAULT_LOCAL_NODES = listOf(
    "http://127.0.0.1:8091",
    "http://127.0.0.1:8092",
    "http://127.0.0.1:8093",
)

private const val DEFAULT_REQUEST_TIMEOUT_MS = 20_000L
private const val DEFAULT_CONNECT_TIMEOUT_MS = 2_500L
private const val DEFAULT_REQUIRED_CONSENSUS = 3
private val DEFAULT_LOCAL_NODE_ATTESTATION_SECRETS = mapOf(
    "sandbox-node-1" to "local-dev-sandbox-secret-1",
    "sandbox-node-2" to "local-dev-sandbox-secret-2",
    "sandbox-node-3" to "local-dev-sandbox-secret-3",
)

private fun parseNodeSecrets(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) {
        return emptyMap()
    }
    return raw.split(',').map { entry ->
        val normalizedEntry = entry.trim()
        if (normalizedEntry.isBlank()) {
            error("SANDBOX_NODE_SECRETS contains an empty entry.")
        }
        val parts = entry.split('=', limit = 2)
        if (parts.size != 2) {
            error("SANDBOX_NODE_SECRETS entry '$normalizedEntry' must use nodeId=secret format.")
        }
        val nodeId = parts[0].trim()
        val secret = parts[1].trim()
        if (nodeId.isBlank() || secret.isBlank()) {
            error("SANDBOX_NODE_SECRETS entry '$normalizedEntry' must have non-empty nodeId and secret.")
        }
        nodeId to secret
    }.also { entries ->
        val duplicateNodeIds = entries
            .groupingBy { it.first }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateNodeIds.isNotEmpty()) {
            error("SANDBOX_NODE_SECRETS contains duplicate node ids: ${duplicateNodeIds.joinToString(", ")}.")
        }
    }.toMap()
}
