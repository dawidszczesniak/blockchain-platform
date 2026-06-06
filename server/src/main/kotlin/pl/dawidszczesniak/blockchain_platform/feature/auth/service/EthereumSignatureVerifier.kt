package pl.dawidszczesniak.blockchain_platform.feature.auth.service

import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthValidationException
import pl.dawidszczesniak.blockchain_platform.feature.auth.AuthVerificationException

internal class EthereumSignatureVerifier {
    fun recoverAddressFromPersonalSign(message: String, signatureHex: String): String {
        val signatureBytes = parseSignature(signatureHex)
        val r = signatureBytes.copyOfRange(0, 32)
        val s = signatureBytes.copyOfRange(32, 64)
        val v = normalizeRecoveryId(signatureBytes[64].toInt() and 0xFF)
        val signatureData = Sign.SignatureData(v.toByte(), r, s)

        return try {
            val prefixedHash = Sign.getEthereumMessageHash(message.toByteArray(Charsets.UTF_8))
            val publicKey = Sign.signedMessageHashToKey(prefixedHash, signatureData)
            "0x${Keys.getAddress(publicKey)}".lowercase()
        } catch (_: Throwable) {
            throw AuthVerificationException("Signature verification failed.")
        }
    }

    private fun parseSignature(signatureHex: String): ByteArray {
        val normalized = signatureHex.trim()
        val parsed = runCatching { Numeric.hexStringToByteArray(normalized) }.getOrNull()
            ?: throw AuthValidationException("Invalid signature format.")
        if (parsed.size != SIGNATURE_LENGTH_BYTES) {
            throw AuthValidationException("Invalid signature length.")
        }
        return parsed
    }

    private fun normalizeRecoveryId(rawV: Int): Int {
        val normalized = when (rawV) {
            0, 1 -> rawV + 27
            else -> rawV
        }
        if (normalized != 27 && normalized != 28) {
            throw AuthValidationException("Invalid signature recovery id.")
        }
        return normalized
    }
}

private const val SIGNATURE_LENGTH_BYTES = 65
