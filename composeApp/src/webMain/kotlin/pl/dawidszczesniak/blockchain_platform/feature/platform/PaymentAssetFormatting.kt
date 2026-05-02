package pl.dawidszczesniak.blockchain_platform.feature.platform

import kotlin.math.min
import pl.dawidszczesniak.blockchain_platform.feature.platform.dto.PaymentAssetDto

fun sanitizeHumanAmountInput(value: String): String {
    var separatorUsed = false
    val builder = StringBuilder()
    value.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            (char == '.' || char == ',') && !separatorUsed -> {
                builder.append('.')
                separatorUsed = true
            }
        }
    }
    return builder.toString()
}

fun parseHumanAmountToAtomic(value: String, paymentAsset: PaymentAssetDto): String? {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isEmpty()) {
        return null
    }
    if (normalized.count { it == '.' } > 1) {
        return null
    }
    val parts = normalized.split('.', limit = 2)
    val wholePart = parts[0].ifEmpty { "0" }
    val fractionPart = parts.getOrNull(1).orEmpty()
    if (!wholePart.all { it.isDigit() } || !fractionPart.all { it.isDigit() }) {
        return null
    }
    if (fractionPart.length > paymentAsset.decimals) {
        return null
    }
    val atomic = buildString {
        append(wholePart.trimStart('0').ifEmpty { "0" })
        append(fractionPart.padEnd(paymentAsset.decimals, '0'))
    }.trimStart('0').ifEmpty { "0" }
    return atomic
}

fun formatAtomicAmount(
    paymentAsset: PaymentAssetDto,
    atomicAmount: String,
    maxFractionDigits: Int = min(paymentAsset.decimals, 6),
): String {
    val normalized = atomicAmount.trim().ifEmpty { "0" }
    if (!normalized.all { it.isDigit() }) {
        return atomicAmount
    }
    if (paymentAsset.decimals == 0) {
        return normalized
    }
    val padded = normalized.padStart(paymentAsset.decimals + 1, '0')
    val splitIndex = padded.length - paymentAsset.decimals
    val wholePart = padded.substring(0, splitIndex)
    val fraction = padded.substring(splitIndex)
        .take(maxFractionDigits.coerceAtLeast(0).coerceAtMost(paymentAsset.decimals))
        .trimEnd('0')
    return if (fraction.isEmpty()) {
        wholePart
    } else {
        "$wholePart.$fraction"
    }
}

fun formatAmountWithSymbol(paymentAsset: PaymentAssetDto, atomicAmount: String): String {
    return "${formatAtomicAmount(paymentAsset, atomicAmount)} ${paymentAsset.symbol}"
}

fun compareAtomicAmounts(left: String, right: String): Int {
    val normalizedLeft = left.trim().trimStart('0').ifEmpty { "0" }
    val normalizedRight = right.trim().trimStart('0').ifEmpty { "0" }
    return when {
        normalizedLeft.length != normalizedRight.length -> normalizedLeft.length.compareTo(normalizedRight.length)
        else -> normalizedLeft.compareTo(normalizedRight)
    }
}
