// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import okhttp3.tls.HeldCertificate

/**
 * The phone's RSA-2048 certificate and key for the Android TV Remote protocol, held in software (stored encrypted
 * by the Android side, see AtvIdentityStore).
 *
 * Why not Android Keystore (the first version, v3.0.42–3.0.46): a TV on Android 10+ negotiates TLS 1.3, where the
 * client must sign with RSA-PSS. Conscrypt pads PSS itself and asks the key for a raw RSA operation, which a
 * Keystore key allows only with ENCRYPTION_PADDING_NONE. Ours did not have it, so the handshake failed on the
 * phone and the owner saw «Не удаётся связаться с приставкой» (12.11), while other remote apps, which keep their
 * key in software, worked on the same Wi-Fi. A software key signs PSS natively.
 */
class AtvSoftwareIdentity(private val held: HeldCertificate) : AtvIdentity {
    override val certificate: X509Certificate get() = held.certificate

    override fun keyManagers(): Array<KeyManager> = arrayOf(OnlyKey(held.certificate, held.keyPair.private))

    /** Certificate and PKCS#8 key as PEM, for the encrypted file. */
    fun encode(): String = held.certificatePem() + held.privateKeyPkcs8Pem()

    /** Offers this one certificate for every client-auth request; never acts as a server. */
    private class OnlyKey(private val cert: X509Certificate, private val key: PrivateKey) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == ALIAS) arrayOf(cert) else null
        override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == ALIAS) key else null
    }

    companion object {
        private const val ALIAS = "atvremote"

        /** A new self-signed certificate valid for 30 years (the TV stores it at pairing; it must not expire). */
        fun create(commonName: String = "atvremote"): AtvSoftwareIdentity {
            val now = System.currentTimeMillis()
            val held = HeldCertificate.Builder()
                .rsa2048()
                .commonName(commonName)
                .organizationalUnit("YAiPlayer")
                .validityInterval(now - TimeUnit.DAYS.toMillis(1), now + TimeUnit.DAYS.toMillis(365L * 30))
                .build()
            return AtvSoftwareIdentity(held)
        }

        fun decode(pem: String): AtvSoftwareIdentity = AtvSoftwareIdentity(HeldCertificate.decode(pem))
    }
}
