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
    private const val ALIAS = "yaiplayer_remote_tls_v1"
    @Synchronized private fun store(): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setCertificateNotBefore(Date(0))
                    .setCertificateNotAfter(Date(4102444800000L)).build())
            }.generateKeyPair()
        }
        return store
    }
    fun fingerprint(): String = RemoteSecurity.hex(MessageDigest.getInstance("SHA-256").digest(store().getCertificate(ALIAS).encoded))
    fun serverFactory(): SSLServerSocketFactory {
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store(), null) }
        return SSLContext.getInstance("TLSv1.2").apply { init(managers.keyManagers, null, null) }.serverSocketFactory
    }
    /**
     * Owner 16.11: pairing asked the user to type the TV certificate's 64-character fingerprint. The phone
     * now reads it from the TV itself, once, when pairing — the 6-digit code shown on the TV screen is what
     * proves it is the right TV; after that the fingerprint is pinned as before.
     */
    fun probeFingerprint(host: String, port: Int): String? = runCatching {
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
            socket.connect(java.net.InetSocketAddress(host, port), 4_000)
            socket.soTimeout = 4_000
            socket.startHandshake()
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
