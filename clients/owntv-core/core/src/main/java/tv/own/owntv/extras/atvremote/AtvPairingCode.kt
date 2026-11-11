// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

/**
 * The pairing secret of the Android TV Remote v2 protocol: SHA-256 over the client's RSA modulus and exponent,
 * the TV's modulus and exponent (each as unsigned big-endian bytes without leading zeros) and the last four hex
 * digits of the code on the TV screen. The first two hex digits of the code are the first byte of that hash,
 * which lets the phone reject a mistyped code before sending anything.
 */
object AtvPairingCode {
    private val HEX = Regex("[0-9A-Fa-f]{6}")

    /** Removes spaces and dashes and upper-cases; returns null unless exactly six hex digits remain. */
    fun normalize(input: String): String? {
        val code = input.filter { !it.isWhitespace() && it != '-' }.uppercase()
        return code.takeIf { HEX.matches(it) }
    }

    fun secret(client: X509Certificate, server: X509Certificate, code: String): ByteArray? {
        val c = client.publicKey as? RSAPublicKey ?: throw IllegalArgumentException("Client key must be RSA")
        val s = server.publicKey as? RSAPublicKey ?: throw IllegalArgumentException("TV key must be RSA")
        return secret(c.modulus, c.publicExponent, s.modulus, s.publicExponent, code)
    }

    /** The secret to send, or null when the code does not match these two keys (typo or wrong TV). */
    fun secret(
        clientModulus: BigInteger,
        clientExponent: BigInteger,
        serverModulus: BigInteger,
        serverExponent: BigInteger,
        code: String,
    ): ByteArray? {
        val normalized = normalize(code) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(unsigned(clientModulus))
        digest.update(unsigned(clientExponent))
        digest.update(unsigned(serverModulus))
        digest.update(unsigned(serverExponent))
        digest.update(hex(normalized.substring(2)))
        val hash = digest.digest()
        return hash.takeIf { (it[0].toInt() and 0xFF) == normalized.substring(0, 2).toInt(16) }
    }

    internal fun unsigned(value: BigInteger): ByteArray {
        require(value.signum() > 0)
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
