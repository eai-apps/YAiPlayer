// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption of source secrets ON THE DEVICE, byte-for-byte the same as the site (yai-cloud `site/src/keys.js`,
 * docs/SYNC_API.md §13). The server never sees the data key, source passwords, tokens, MACs or the M3U address.
 *
 *  - dataKey: 32 random bytes (AES-256-GCM)
 *  - wrappedByPassword = iv12 | GCM(PBKDF2-SHA256(password, keySalt, 310 000), dataKey)
 *  - wrappedByRecovery = salt16 | iv12 | GCM(PBKDF2-SHA256(CODE, salt16, 310 000), dataKey)
 *  - record secret     = iv12 | GCM(dataKey, UTF-8 JSON)
 *  - wrappedForDevice  = ephPub65 | iv12 | GCM(HKDF-SHA256(ECDH(eph, devicePub), salt ∅, "yai-key-transfer-v1"), dataKey)
 * All values are base64url without padding.
 */
object AccountCrypto {
    const val PBKDF2_ITERATIONS = 310_000
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()
    private val INFO = "yai-key-transfer-v1".toByteArray()

    fun b64u(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun unb64u(s: String): ByteArray = Base64.getUrlDecoder().decode(s.trim().replace('+', '-').replace('/', '_').trimEnd('='))

    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    fun newRecoveryCode(): String {
        val b = randomBytes(24)
        val s = buildString { for (x in b) append(ALPHABET[(x.toInt() and 0xFF) % ALPHABET.length]) }
        return s.chunked(4).joinToString("-")
    }

    fun normalizeRecovery(code: String) = code.replace(Regex("[\\s-]"), "").uppercase()

    private fun passwordKey(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    private fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, 12))
        return c.doFinal(sealed, 12, sealed.size - 12)
    }

    data class PasswordWrap(val keySalt: String, val wrappedByPassword: String)

    data class NewKeys(
        val dataKey: ByteArray,
        val keySalt: String,
        val wrappedByPassword: String,
        val wrappedByRecovery: String,
        val publicKey: String,
        val wrappedPrivateKey: String,
    )

    fun wrapByPassword(dataKey: ByteArray, password: String): PasswordWrap {
        val salt = randomBytes(16)
        return PasswordWrap(b64u(salt), b64u(seal(passwordKey(password, salt), dataKey)))
    }

    fun wrapByRecovery(dataKey: ByteArray, code: String): String {
        val salt = randomBytes(16)
        return b64u(salt + seal(passwordKey(normalizeRecovery(code), salt), dataKey))
    }

    /** Keys for a new account (registration on a TV box or phone). */
    fun newAccountKeys(password: String, recoveryCode: String): NewKeys {
        val dataKey = randomBytes(32)
        val pw = wrapByPassword(dataKey, password)
        val pair = newDeviceKeyPair()
        return NewKeys(
            dataKey = dataKey,
            keySalt = pw.keySalt,
            wrappedByPassword = pw.wrappedByPassword,
            wrappedByRecovery = wrapByRecovery(dataKey, recoveryCode),
            publicKey = publicRaw(pair.public),
            wrappedPrivateKey = b64u(seal(dataKey, pair.private.encoded)),
        )
    }

    fun unlockWithPassword(keySalt: String, wrappedByPassword: String, password: String): ByteArray =
        open(passwordKey(password, unb64u(keySalt)), unb64u(wrappedByPassword))

    fun unlockWithRecovery(wrappedByRecovery: String, code: String): ByteArray {
        val blob = unb64u(wrappedByRecovery)
        return open(passwordKey(normalizeRecovery(code), blob.copyOfRange(0, 16)), blob.copyOfRange(16, blob.size))
    }

    fun encryptSecret(dataKey: ByteArray, json: String): String = b64u(seal(dataKey, json.toByteArray(Charsets.UTF_8)))
    fun decryptSecret(dataKey: ByteArray, secret: String): String = String(open(dataKey, unb64u(secret)), Charsets.UTF_8)

    // ---- P-256 key transfer (TV by code/QR, a new phone) ----
    private val p256: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
    }

    fun newDeviceKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()

    private fun fixed32(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }

    /** Raw uncompressed point, 65 bytes, base64url — what the server and the site expect as `devicePub`. */
    fun publicRaw(key: PublicKey): String {
        val w = (key as ECPublicKey).w
        return b64u(byteArrayOf(4) + fixed32(w.affineX) + fixed32(w.affineY))
    }

    fun publicFromRaw(raw: ByteArray): PublicKey {
        require(raw.size == 65 && raw[0] == 4.toByte()) { "bad P-256 point" }
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, p256))
    }

    fun privateFromPkcs8(pkcs8: ByteArray): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    private fun hkdf(ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256")) // empty salt = HashLen zeros (RFC 5869)
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        return mac.doFinal(INFO + byteArrayOf(1))
    }

    private fun shared(priv: PrivateKey, pub: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").apply { init(priv); doPhase(pub, true) }.generateSecret()

    fun wrapForDevice(dataKey: ByteArray, devicePub: String): String {
        val eph = newDeviceKeyPair()
        val key = hkdf(shared(eph.private, publicFromRaw(unb64u(devicePub))))
        return b64u(unb64u(publicRaw(eph.public)) + seal(key, dataKey))
    }

    fun unwrapOnDevice(devicePrivate: PrivateKey, wrapped: String): ByteArray {
        val blob = unb64u(wrapped)
        val key = hkdf(shared(devicePrivate, publicFromRaw(blob.copyOfRange(0, 65))))
        return open(key, blob.copyOfRange(65, blob.size))
    }
}
