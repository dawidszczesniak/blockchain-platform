package pl.dawidszczesniak.blockchain_platform.feature.problems.usecase

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.db.SubmissionAttestationStatus
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxConfig
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxNodeRunOutput
import pl.dawidszczesniak.blockchain_platform.feature.problems.sandbox.SandboxRunTestOutput

internal data class EvaluatedNodeRun(
    val nodeId: String,
    val nodeUrl: String,
    val imageHash: String?,
    val runHash: String?,
    val resultHash: String?,
    val suiteExecutionTimeMs: Int? = null,
    val attestationPayloadHash: String?,
    val attestationSignature: String?,
    val attestationScheme: String?,
    val results: List<SandboxRunTestOutput>,
    val status: SubmissionAttestationStatus,
    val isValid: Boolean,
    val message: String?,
)

internal data class SandboxConsensusDecision(
    val evaluatedNodes: List<EvaluatedNodeRun>,
    val representativeNode: EvaluatedNodeRun,
    val consensusReached: Int,
    val consensusNodeIds: Set<String>,
    val resultHash: String,
    val imageHash: String?,
    val runtimeMs: Int,
    val memoryUsedKb: Int?,
)

internal class SandboxConsensusEvaluator(
    private val sandboxConfig: SandboxConfig,
) {
    fun evaluate(nodeRuns: List<SandboxNodeRunOutput>): SandboxConsensusDecision {
        if (nodeRuns.isEmpty()) {
            throw IllegalStateException("No sandbox nodes are configured.")
        }

        val evaluatedNodes = nodeRuns.map(::evaluateNodeRun)
        val validNodeGroups = evaluatedNodes
            .filter { it.isValid && !it.resultHash.isNullOrBlank() }
            .groupBy { it.resultHash!! to (it.imageHash ?: "") }
        val consensusEntry = validNodeGroups.entries.maxByOrNull { it.value.size }
            ?: throw IllegalStateException("No valid node attestation was returned by the sandbox cluster.")
        val consensusReached = consensusEntry.value.size
        if (consensusReached < sandboxConfig.requiredConsensus) {
            throw IllegalStateException(
                "Sandbox consensus not reached: got $consensusReached/${sandboxConfig.requiredConsensus} valid matching nodes."
            )
        }

        val consensusNodes = consensusEntry.value
        val aggregatedResults = aggregateConsensusResults(consensusNodes)
        val runtimeMs = aggregatedResults.sumOf { it.executionTimeMs }
        val memoryUsedKb = aggregatedResults.mapNotNull { it.memoryUsedKb }.maxOrNull()
        val representativeNode = consensusNodes.first().copy(
            suiteExecutionTimeMs = runtimeMs,
            results = aggregatedResults,
        )

        return SandboxConsensusDecision(
            evaluatedNodes = evaluatedNodes,
            representativeNode = representativeNode,
            consensusReached = consensusReached,
            consensusNodeIds = consensusNodes.map { it.nodeId }.toSet(),
            resultHash = consensusEntry.key.first,
            imageHash = consensusEntry.key.second.takeIf { it.isNotBlank() },
            runtimeMs = runtimeMs,
            memoryUsedKb = memoryUsedKb,
        )
    }

    private fun evaluateNodeRun(node: SandboxNodeRunOutput): EvaluatedNodeRun {
        val nodeId = node.nodeId?.trim().orEmpty().ifBlank { "unknown-node" }
        if (!node.errorMessage.isNullOrBlank()) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = node.resultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = emptyList(),
                status = SubmissionAttestationStatus.Error,
                isValid = false,
                message = node.errorMessage,
            )
        }
        val computedResultHash = computeSandboxResultHash(node.results)
        if (computedResultHash != node.resultHash) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = node.resultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Node result hash does not match recomputed value.",
            )
        }

        val imageHash = node.imageHash.orEmpty()
        val runHash = node.runHash.orEmpty()
        val executedAt = node.executedAt.orEmpty()
        if (imageHash.isBlank() || runHash.isBlank() || executedAt.isBlank()) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Node attestation metadata is incomplete.",
            )
        }

        val scheme = node.attestationScheme?.trim()?.lowercase().orEmpty()
        if (scheme != SANDBOX_DEFAULT_ATTESTATION_SCHEME) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Unsupported attestation scheme '$scheme'.",
            )
        }

        val sharedSecret = sandboxConfig.nodeAttestationSecrets[nodeId].orEmpty()
        if (sharedSecret.isBlank()) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "No shared attestation secret configured for node '$nodeId'.",
            )
        }

        val expectedPayloadHash = computeAttestationPayloadHash(
            nodeId = nodeId,
            imageHash = imageHash,
            runHash = runHash,
            resultHash = computedResultHash,
            executedAt = executedAt,
        )
        if (expectedPayloadHash != node.attestationPayloadHash) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Attestation payload hash mismatch.",
            )
        }

        val expectedSignature = hmacSha256Hex(
            secret = sharedSecret,
            payloadHash = expectedPayloadHash,
        )
        if (!constantTimeEquals(expectedSignature, node.attestationSignature.orEmpty())) {
            return EvaluatedNodeRun(
                nodeId = nodeId,
                nodeUrl = node.nodeUrl,
                imageHash = node.imageHash,
                runHash = node.runHash,
                resultHash = computedResultHash,
                suiteExecutionTimeMs = node.suiteExecutionTimeMs,
                attestationPayloadHash = node.attestationPayloadHash,
                attestationSignature = node.attestationSignature,
                attestationScheme = node.attestationScheme,
                results = node.results,
                status = SubmissionAttestationStatus.Invalid,
                isValid = false,
                message = "Attestation signature mismatch.",
            )
        }

        return EvaluatedNodeRun(
            nodeId = nodeId,
            nodeUrl = node.nodeUrl,
            imageHash = node.imageHash,
            runHash = node.runHash,
            resultHash = computedResultHash,
            suiteExecutionTimeMs = node.suiteExecutionTimeMs,
            attestationPayloadHash = node.attestationPayloadHash,
            attestationSignature = node.attestationSignature,
            attestationScheme = node.attestationScheme,
            results = node.results,
            status = SubmissionAttestationStatus.Ok,
            isValid = true,
            message = null,
        )
    }
}

private fun aggregateConsensusResults(
    consensusNodes: List<EvaluatedNodeRun>,
): List<SandboxRunTestOutput> {
    val referenceResults = consensusNodes.firstOrNull()?.results.orEmpty()
    if (referenceResults.isEmpty()) {
        return emptyList()
    }

    return referenceResults.map { referenceResult ->
        val matchingResults = consensusNodes.map { node ->
            node.results.firstOrNull { candidate ->
                candidate.id == referenceResult.id && candidate.order == referenceResult.order
            } ?: throw IllegalStateException(
                "Consensus node '${node.nodeId}' is missing test result ${referenceResult.id}/${referenceResult.order}."
            )
        }
        val executionTimeMs = medianInt(matchingResults.map { it.executionTimeMs }) ?: 0
        val memoryValues = matchingResults.map { it.memoryUsedKb }
        val memoryUsedKb = if (memoryValues.all { it != null }) {
            medianInt(memoryValues.filterNotNull())
        } else {
            null
        }
        referenceResult.copy(
            executionTimeMs = executionTimeMs,
            memoryUsedKb = memoryUsedKb,
        )
    }
}

private fun computeAttestationPayloadHash(
    nodeId: String,
    imageHash: String,
    runHash: String,
    resultHash: String,
    executedAt: String,
): String {
    val payload = "$nodeId|$imageHash|$runHash|$resultHash|$executedAt"
    val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun hmacSha256Hex(secret: String, payloadHash: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val digest = mac.doFinal(payloadHash.toByteArray(Charsets.UTF_8))
    return "0x${Numeric.toHexStringNoPrefix(digest).lowercase()}"
}

private fun constantTimeEquals(expectedHex: String, actualHex: String): Boolean {
    val expected = normalizeHashHex(expectedHex).removePrefix("0x").toByteArray(Charsets.UTF_8)
    val actual = normalizeHashHex(actualHex).removePrefix("0x").toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(expected, actual)
}

private fun normalizeHashHex(raw: String): String {
    val trimmed = raw.trim().lowercase()
    return if (trimmed.startsWith("0x")) trimmed else "0x$trimmed"
}

private fun medianInt(values: List<Int>): Int? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        ((sorted[middle - 1].toLong() + sorted[middle].toLong()) / 2L).toInt()
    }
}

private const val SANDBOX_DEFAULT_ATTESTATION_SCHEME = "hmac-sha256"
