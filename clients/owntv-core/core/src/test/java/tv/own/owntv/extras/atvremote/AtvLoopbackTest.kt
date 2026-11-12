// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.math.BigInteger
import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A fake TV on 127.0.0.1 speaks the server side of both ports over real TLS with client certificates, so the
 * client is exercised end to end: handshake, pairing secret, certificate pinning, and the command loop.
 */
class AtvLoopbackTest {
    private val tvCert = HeldCertificate.Builder().rsa2048().commonName("tv").build()
    private val phoneCert = HeldCertificate.Builder().rsa2048().commonName("atvremote").build()
    private val strangerCert = HeldCertificate.Builder().rsa2048().commonName("stranger").build()
    private val sockets = mutableListOf<SSLServerSocket>()

    private fun identity(held: HeldCertificate) = object : AtvIdentity {
        override val certificate: X509Certificate = held.certificate
        override fun keyManagers(): Array<KeyManager> =
            arrayOf(HandshakeCertificates.Builder().heldCertificate(held).build().keyManager)
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** TLS 1.2 so a rejected client certificate fails inside the handshake, deterministically. */
    private fun server(acceptedClient: X509Certificate?, protocol: String = "TLSv1.2"): SSLServerSocket {
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                if (acceptedClient != null && !chain[0].encoded.contentEquals(acceptedClient.encoded)) {
                    throw CertificateException("unknown client")
                }
            }
        }
        val ctx = SSLContext.getInstance("TLS").apply {
            init(arrayOf(HandshakeCertificates.Builder().heldCertificate(tvCert).build().keyManager), arrayOf<TrustManager>(trust), null)
        }
        return (ctx.serverSocketFactory.createServerSocket(0, 5, InetAddress.getLoopbackAddress()) as SSLServerSocket).also {
            it.needClientAuth = true
            it.enabledProtocols = arrayOf(protocol)
            sockets += it
        }
    }

    @After fun tearDown() = sockets.forEach { runCatching { it.close() } }

    private fun unsigned(v: BigInteger): ByteArray = v.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

    /** The code a real TV would show: check byte, then the four random hex digits. */
    private fun codeFor(tail: String): Pair<String, ByteArray> {
        val c = phoneCert.certificate.publicKey as RSAPublicKey
        val s = tvCert.certificate.publicKey as RSAPublicKey
        val d = MessageDigest.getInstance("SHA-256")
        listOf(c.modulus, c.publicExponent, s.modulus, s.publicExponent).forEach { d.update(unsigned(it)) }
        d.update(hex(tail))
        val hash = d.digest()
        return "%02X".format(hash[0].toInt() and 0xFF) + tail to hash
    }

    private fun serve(socket: SSLServerSocket, body: (SSLSocket) -> Unit): Thread =
        Thread {
            runCatching { (socket.accept() as SSLSocket).use { body(it) } }
        }.apply { isDaemon = true; start() }

    @Test fun pairingSucceedsAfterLocalTypoAndPinsTvCertificate() {
        val server = server(acceptedClient = null)
        val (code, expectedSecret) = codeFor("1234")
        val received = LinkedBlockingQueue<ByteArray>()
        val tv = serve(server) { s ->
            val input = s.inputStream
            val out = s.outputStream
            val request = ProtoReader.fields(AtvFraming.read(input)).msg(10)!!
            assertEquals("atvremote", request.str(1))
            assertEquals("YAiPlayer test", request.str(2))
            AtvFraming.write(out, hex("080210c8015a040a025456"))
            assertEquals(PoloMessages.Kind.OPTIONS, PoloMessages.parse(AtvFraming.read(input)).kind)
            AtvFraming.write(out, PoloMessages.options())
            assertTrue(ProtoReader.fields(AtvFraming.read(input)).has(30))
            AtvFraming.write(out, hex("080210c801fa0100"))
            received += ProtoReader.fields(AtvFraming.read(input)).msg(40)!!.first { it.number == 1 }.bytes!!
            AtvFraming.write(out, hex("080210c801ca02030a0101"))
        }
        AtvPairing.open(identity(phoneCert), "127.0.0.1", server.localPort).use { pairing ->
            pairing.start("YAiPlayer test")
            val typo = (if (code[0] == 'A') "B" else "A") + code.substring(1)
            try {
                pairing.finish(typo)
                fail("typo accepted")
            } catch (e: AtvPairingException) {
                assertEquals(AtvPairingException.LOCAL_CODE_MISMATCH, e.status)
            }
            pairing.finish(code.lowercase())
            assertEquals(AtvTls.sha256(tvCert.certificate), pairing.tvCertificateSha256)
        }
        assertArrayEquals(expectedSecret, received.poll(5, TimeUnit.SECONDS))
        tv.join(5_000)
    }

    @Test fun tvRejectingTheSecretIsReportedAsWrongCode() {
        val server = server(acceptedClient = null)
        val (code, _) = codeFor("BEEF")
        serve(server) { s ->
            val input = s.inputStream
            val out = s.outputStream
            AtvFraming.read(input); AtvFraming.write(out, hex("080210c8015a040a025456"))
            AtvFraming.read(input); AtvFraming.write(out, PoloMessages.options())
            AtvFraming.read(input); AtvFraming.write(out, hex("080210c801fa0100"))
            AtvFraming.read(input); AtvFraming.write(out, hex("0802109203"))
        }
        AtvPairing.open(identity(phoneCert), "127.0.0.1", server.localPort).use { pairing ->
            pairing.start("x")
            try {
                pairing.finish(code)
                fail("402 ignored")
            } catch (e: AtvPairingException) {
                assertTrue(e.wrongCode)
            }
        }
    }

    @Test fun commandSessionAnswersTvAndSendsKeysTextAndApps() {
        val server = server(acceptedClient = phoneCert.certificate)
        val fromPhone = LinkedBlockingQueue<String>()
        val ready = CountDownLatch(1)
        val volume = LinkedBlockingQueue<Triple<Int, Int, Boolean>>()
        serve(server) { s ->
            val input = s.inputStream
            val out = s.outputStream
            fun next() = AtvFraming.read(input).joinToString("") { "%02x".format(it) }
            AtvFraming.write(out, hex("0a1a08ee0412150a06534849454c4412064e56494449413203352e32"))
            fromPhone += next()
            AtvFraming.write(out, hex("1200"))
            fromPhone += next()
            AtvFraming.write(out, hex("4204082a1007"))
            fromPhone += next()
            AtvFraming.write(out, hex("a201270a210809621d636f6d2e676f6f676c652e616e64726f69642e796f75747562652e747612020804"))
            AtvFraming.write(out, hex("9203091a0158306438234001"))
            AtvFraming.write(out, hex("c202020801"))
            repeat(3) { fromPhone += next() }
        }
        val apps = LinkedBlockingQueue<String>()
        val session = AtvRemoteSession.connect(
            identity(phoneCert), "127.0.0.1", AtvTls.sha256(tvCert.certificate),
            object : AtvSessionListener {
                override fun onReady(info: AtvTvInfo) {
                    assertEquals("SHIELD", info.model)
                    ready.countDown()
                }
                override fun onVolume(level: Int, max: Int, muted: Boolean) { volume += Triple(level, max, muted) }
                override fun onCurrentApp(packageName: String) { apps += packageName }
            },
            port = server.localPort,
        )
        session.use {
            assertEquals("0a1c08e604121718012201312a0961747672656d6f74653205312e302e30", fromPhone.poll(5, TimeUnit.SECONDS))
            assertEquals("120308e604", fromPhone.poll(5, TimeUnit.SECONDS))
            assertEquals("4a02082a", fromPhone.poll(5, TimeUnit.SECONDS))
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            assertEquals(Triple(35, 100, true), volume.poll(5, TimeUnit.SECONDS))
            assertEquals("com.google.android.youtube.tv", apps.poll(5, TimeUnit.SECONDS))
            assertTrue(it.supports(AtvFeatures.APP_LINK))
            assertTrue(!it.supports(AtvFeatures.PING)) // the TV did not offer it (622)
            it.key(AtvKeys.DPAD_CENTER)
            it.text("Привет")
            it.launch("https://www.youtube.com")
            assertEquals("520408171003", fromPhone.poll(5, TimeUnit.SECONDS))
            // Counters 9 and 4 came from the TV's ime_key_inject.
            assertEquals("aa011c080910041a1608011212080610061a0cd09fd180d0b8d0b2d0b5d182", fromPhone.poll(5, TimeUnit.SECONDS))
            assertEquals("d205190a1768747470733a2f2f7777772e796f75747562652e636f6d", fromPhone.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test fun anotherDeviceAtTheSameAddressIsRefused() {
        val server = server(acceptedClient = null)
        serve(server) { it.startHandshake() }
        try {
            AtvRemoteSession.connect(identity(phoneCert), "127.0.0.1", "0".repeat(64), object : AtvSessionListener {}, server.localPort)
            fail("pin ignored")
        } catch (_: AtvCertificateMismatchException) {
        }
    }

    @Test fun unknownPhoneMustPairAgain() {
        val server = server(acceptedClient = phoneCert.certificate)
        serve(server) { it.startHandshake() }
        try {
            AtvRemoteSession.connect(identity(strangerCert), "127.0.0.1", AtvTls.sha256(tvCert.certificate), object : AtvSessionListener {}, server.localPort)
            fail("stranger accepted")
        } catch (_: AtvNeedsPairingException) {
        }
    }

    @Test fun publicAddressIsNeverContacted() {
        try {
            AtvTls.connect(identity(phoneCert), "8.8.8.8", 6466, null)
            fail("public address used")
        } catch (_: AtvAddressException) {
        }
    }

    /** The phone's real identity type (software RSA, owner 12.11) survives the encrypted file and pairs over TLS 1.3. */
    @Test fun softwareIdentityPairsOverTls13() {
        val phone = AtvSoftwareIdentity.decode(AtvSoftwareIdentity.create().encode())
        assertTrue(phone.certificate.notAfter.time - System.currentTimeMillis() > TimeUnit.DAYS.toMillis(365L * 20))
        val server = server(acceptedClient = null, protocol = "TLSv1.3")
        val c = phone.certificate.publicKey as RSAPublicKey
        val sKey = tvCert.certificate.publicKey as RSAPublicKey
        val d = MessageDigest.getInstance("SHA-256")
        listOf(c.modulus, c.publicExponent, sKey.modulus, sKey.publicExponent).forEach { d.update(unsigned(it)) }
        d.update(hex("ABCD"))
        val hash = d.digest()
        val code = "%02X".format(hash[0].toInt() and 0xFF) + "ABCD"
        val received = LinkedBlockingQueue<ByteArray>()
        val clientCert = LinkedBlockingQueue<String>()
        serve(server) { s ->
            val input = s.inputStream
            val out = s.outputStream
            clientCert += s.session.protocol + " " + (s.session.peerCertificates[0] as X509Certificate).subjectX500Principal.name
            AtvFraming.read(input); AtvFraming.write(out, hex("080210c8015a040a025456"))
            AtvFraming.read(input); AtvFraming.write(out, PoloMessages.options())
            AtvFraming.read(input); AtvFraming.write(out, hex("080210c801fa0100"))
            received += ProtoReader.fields(AtvFraming.read(input)).msg(40)!!.first { it.number == 1 }.bytes!!
            AtvFraming.write(out, hex("080210c801ca02030a0101"))
        }
        AtvPairing.open(phone, "127.0.0.1", server.localPort).use { pairing ->
            pairing.start("YAiPlayer")
            pairing.finish(code)
        }
        assertArrayEquals(hash, received.poll(5, TimeUnit.SECONDS))
        assertTrue(clientCert.poll(5, TimeUnit.SECONDS)!!.startsWith("TLSv1.3 "))
    }

    /** TLS 1.3: the TV checks the phone only after the phone's handshake is done, then drops the line. */
    @Test fun tls13UnknownPhoneMustPairAgain() {
        val server = server(acceptedClient = phoneCert.certificate, protocol = "TLSv1.3")
        serve(server) { runCatching { it.startHandshake(); it.inputStream.read() } }
        try {
            AtvRemoteSession.connect(identity(strangerCert), "127.0.0.1", AtvTls.sha256(tvCert.certificate), object : AtvSessionListener {}, server.localPort)
            fail("stranger accepted")
        } catch (_: AtvNeedsPairingException) {
        }
    }

    @Test fun probeTellsOpenFromClosedPorts() {
        val open = java.net.ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val closedPort = java.net.ServerSocket(0, 5, InetAddress.getLoopbackAddress()).let { val p = it.localPort; it.close(); p }
        try {
            assertEquals(AtvProbe.Port.OPEN, AtvProbe.port(InetAddress.getLoopbackAddress(), open.localPort))
            assertEquals(AtvProbe.Port.CLOSED, AtvProbe.port(InetAddress.getLoopbackAddress(), closedPort))
        } finally {
            open.close()
        }
        assertTrue(AtvLog.text().contains("connected"))
    }
}
