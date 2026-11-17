// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.cert.CertificateException
import java.util.Date
import javax.net.ssl.*
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

object RemoteTls {
    /**
     * Owner 17.11: the phone saw both TVs (name, address, port) yet «the TV did not answer» — every TLS handshake
     * failed. The TLS stack signs the handshake with NONEwithECDSA (it hashes first and hands the key the digest);
     * the v1 key allowed SHA-256/512 only, so the keystore refused to sign and the connection broke. Keys cannot
     * change their rules, so a new v2 key is made with DIGEST_NONE allowed. (Phones were never paired to v1.)
     */
    private const val ALIAS = "yaiplayer_remote_tls_v2"
    @Synchronized private fun store(): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setCertificateNotBefore(Date(0))
                    .setCertificateNotAfter(Date(4102444800000L)).build())
            }.generateKeyPair()
        }
        return store
    }
    fun fingerprint(): String = RemoteSecurity.hex(MessageDigest.getInstance("SHA-256").digest(store().getCertificate(ALIAS).encoded))
    /**
     * Owner 17.11 (beta-43): with the v2 key the phone still got «TLS: SSLHandshakeException Connection closed by
     * peer» — the TV hung up during the handshake. A KeyManagerFactory fed the AndroidKeyStore may hand the TLS
     * stack no usable key (it reads entries with a password object the keystore does not take), and the server
     * then has no certificate to offer and closes. The TV now gives TLS its key and certificate directly.
     */
    fun serverFactory(): SSLServerSocketFactory {
        val ks = store()
        val key = ks.getKey(ALIAS, null) as java.security.PrivateKey
        val chain = arrayOf(ks.getCertificate(ALIAS) as X509Certificate)
        val manager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = null
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?): String? = null
            override fun getServerAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String> = arrayOf(ALIAS)
            override fun chooseServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?): String? =
                if (keyType == null || keyType.startsWith("EC")) ALIAS else null
            override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, engine: SSLEngine?): String? =
                if (keyType == null || keyType.startsWith("EC")) ALIAS else null
            override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == ALIAS) chain else null
            override fun getPrivateKey(alias: String?): java.security.PrivateKey? = if (alias == ALIAS) key else null
        }
        return SSLContext.getInstance("TLSv1.2").apply { init(arrayOf<KeyManager>(manager), null, null) }.serverSocketFactory
    }
    /**
     * Owner 16.11: pairing asked the user to type the TV certificate's 64-character fingerprint. The phone
     * now reads it from the TV itself, once, when pairing — the 6-digit code shown on the TV screen is what
     * proves it is the right TV; after that the fingerprint is pinned as before.
     */
    /** Why the last probe failed («TLS: …», «connect: …»), shown on the phone under the TV's address. */
    @Volatile var lastProbeError: String? = null
        private set

    fun probeFingerprint(host: String, port: Int): String? = runCatching {
        lastProbeError = null
        var seen: String? = null
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException() }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw CertificateException("No certificate")
                seen = RemoteSecurity.hex(MessageDigest.getInstance("SHA-256").digest(chain[0].encoded))
            }
        }
        val ssl = SSLContext.getInstance("TLSv1.2").apply { init(null, arrayOf<TrustManager>(trust), null) }
        (ssl.socketFactory.createSocket() as javax.net.ssl.SSLSocket).use { socket ->
            runCatching { socket.connect(java.net.InetSocketAddress(host, port), 4_000) }
                .onFailure { lastProbeError = "connect: ${it.javaClass.simpleName} ${it.message.orEmpty()}".take(140); throw it }
            socket.soTimeout = 4_000
            runCatching { socket.startHandshake() }
                .onFailure { lastProbeError = "TLS: ${it.javaClass.simpleName} ${it.message.orEmpty()}".take(140); throw it }
        }
        seen
    }.getOrNull()

    fun client(fingerprint: String): OkHttpClient {
        require(fingerprint.matches(Regex("[a-f0-9]{64}")))
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException() }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty() || !RemoteSecurity.equal(fingerprint, RemoteSecurity.hex(MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)))) throw CertificateException("Certificate mismatch")
                chain[0].checkValidity()
            }
        }
        val ssl = SSLContext.getInstance("TLSv1.2").apply { init(null, arrayOf<TrustManager>(trust), null) }
        return OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust)
            // Identity is the exact out-of-band certificate fingerprint, rather than a DNS name.
            .hostnameVerifier { _, _ -> true }.proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS).build()
    }
}
