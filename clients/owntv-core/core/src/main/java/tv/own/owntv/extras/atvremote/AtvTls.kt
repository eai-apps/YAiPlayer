// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.io.Closeable
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** The phone's own certificate and key, which the TV remembers after pairing. RSA is required by the protocol. */
interface AtvIdentity {
    val certificate: X509Certificate
    fun keyManagers(): Array<KeyManager>
}

class AtvAddressException(message: String) : IOException(message)

/** TCP connected but TLS failed: on the command port the TV closes it when it does not know the phone. */
class AtvHandshakeException(cause: Throwable) : IOException("TLS handshake failed", cause)

/** The TV's certificate does not match the one remembered at pairing (another device took its address). */
class AtvCertificateMismatchException : IOException("TV certificate changed")

/** One TLS connection to a TV port, carrying length-prefixed protobuf messages. */
class AtvChannel internal constructor(
    private val socket: SSLSocket,
    val serverCertificate: X509Certificate,
) : Closeable {
    private val input = socket.inputStream.buffered()
    private val output = socket.outputStream
    private val writeLock = Any()

    fun send(message: ByteArray) = synchronized(writeLock) { AtvFraming.write(output, message) }

    fun receive(): ByteArray = AtvFraming.read(input)

    fun setReadTimeout(ms: Int) {
        socket.soTimeout = ms
    }

    val isClosed: Boolean get() = socket.isClosed

    override fun close() {
        runCatching { socket.close() }
    }
}

object AtvTls {
    const val PAIRING_PORT = 6467
    const val REMOTE_PORT = 6466

    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    /**
     * Only addresses inside the home network are allowed: private IPv4, link-local, IPv6 unique-local, and the
     * loopback address used by tests. The remote never connects to the internet.
     */
    fun isLocalAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) return true
        if (address is Inet6Address) return (address.address[0].toInt() and 0xFE) == 0xFC
        // 100.64.0.0/10 is carrier-grade NAT, not a home network; everything else public is refused.
        return false
    }

    /**
     * Opens a TLS connection with the phone's certificate. The TV's certificate is self-signed, so it cannot be
     * checked against a CA; instead the SHA-256 of the certificate seen at pairing is required on every later
     * connection ([pinnedSha256]). Pairing itself passes null and remembers what it saw.
     */
    fun connect(
        identity: AtvIdentity,
        host: String,
        port: Int,
        pinnedSha256: String?,
        connectTimeoutMs: Int = 5_000,
    ): AtvChannel {
        val address = InetAddress.getByName(host)
        if (!isLocalAddress(address)) throw AtvAddressException("Not a home network address")
        var seen: X509Certificate? = null
        var mismatch = false
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Client mode only")
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("No certificate")
                if (pinnedSha256 != null && !MessageDigest.isEqual(sha256(leaf).toByteArray(), pinnedSha256.toByteArray())) {
                    mismatch = true
                    throw CertificateException("TV certificate changed")
                }
                seen = leaf
            }
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(identity.keyManagers(), arrayOf<TrustManager>(trust), null)
        }
        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(address, port), connectTimeoutMs)
            plain.soTimeout = 10_000
            val ssl = context.socketFactory.createSocket(plain, address.hostAddress, port, true) as SSLSocket
            ssl.useClientMode = true
            try {
                ssl.startHandshake()
            } catch (e: IOException) {
                runCatching { ssl.close() }
                if (mismatch) throw AtvCertificateMismatchException()
                throw AtvHandshakeException(e)
            }
            val cert = seen ?: (ssl.session.peerCertificates.firstOrNull() as? X509Certificate)
                ?: throw IOException("No TV certificate")
            return AtvChannel(ssl, cert)
        } catch (e: Exception) {
            runCatching { plain.close() }
            throw e
        }
    }
}
