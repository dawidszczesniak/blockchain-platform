package pl.dawidszczesniak.blockchain_platform.feature.platform

import kotlin.math.min
import pl.dawidszczesniak.blockchain_platform.feature.problems.normalizeAtomicAmount

internal fun formatAtomicAmountDisplay(
    paymentAsset: PaymentAssetConfig,
    atomicAmount: String,
    maxFractionDigits: Int = min(paymentAsset.decimals, 6),
): String {
    val normalized = normalizeAtomicAmount(atomicAmount, "Atomic amount")
    val decimals = paymentAsset.decimals
    if (decimals == 0) {
        return normalized
    }

    val padded = normalized.padStart(decimals + 1, '0')
    val splitIndex = padded.length - decimals
    val wholePart = padded.substring(0, splitIndex)
    val rawFraction = padded.substring(splitIndex)
    val trimmedFraction = rawFraction
        .take(maxFractionDigits.coerceAtLeast(0).coerceAtMost(rawFraction.length))
        .trimEnd('0')

    return if (trimmedFraction.isEmpty()) {
        wholePart
    } else {
        "$wholePart.$trimmedFraction"
    }
}
