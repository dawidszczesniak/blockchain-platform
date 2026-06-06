package pl.dawidszczesniak.blockchain_platform.feature.problems

import java.math.BigInteger

internal fun normalizeAtomicAmount(
    raw: String,
    fieldName: String,
    allowZero: Boolean = true,
): String {
    val trimmed = raw.trim()
    require(trimmed.isNotEmpty()) { "$fieldName is required." }
    require(trimmed.all { it.isDigit() }) { "$fieldName must be an unsigned integer amount in atomic units." }
    val normalized = trimmed.trimStart('0').ifEmpty { "0" }
    require(allowZero || normalized != "0") { "$fieldName must be greater than 0." }
    return normalized
}

internal fun atomicAmountToBigInteger(raw: String, fieldName: String): BigInteger {
    return BigInteger(normalizeAtomicAmount(raw, fieldName))
}
