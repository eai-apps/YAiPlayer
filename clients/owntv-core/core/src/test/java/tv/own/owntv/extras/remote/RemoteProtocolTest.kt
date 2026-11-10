// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class RemoteProtocolTest {

    @Test
    fun testCommandSerialization() {
        val navCmd = RemoteCommand.Nav(RemoteNavKey.UP)
        val navParsed = RemoteCommand.fromJson(navCmd.toJson()) as? RemoteCommand.Nav
        assertEquals(RemoteNavKey.UP, navParsed?.key)

        val mediaCmd = RemoteCommand.Media(RemoteMediaKey.PLAY_PAUSE)
        val mediaParsed = RemoteCommand.fromJson(mediaCmd.toJson()) as? RemoteCommand.Media
        assertEquals(RemoteMediaKey.PLAY_PAUSE, mediaParsed?.key)

        val numCmd = RemoteCommand.Number(7)
        val numParsed = RemoteCommand.fromJson(numCmd.toJson()) as? RemoteCommand.Number
        assertEquals(7, numParsed?.digit)

        val textCmd = RemoteCommand.Text("Discovery Science")
        val textParsed = RemoteCommand.fromJson(textCmd.toJson()) as? RemoteCommand.Text
        assertEquals("Discovery Science", textParsed?.query)

        val tuneCmd = RemoteCommand.Tune(channelId = 42L, streamUrl = "http://stream.m3u8", title = "Channel 1")
        val tuneParsed = RemoteCommand.fromJson(tuneCmd.toJson()) as? RemoteCommand.Tune
        assertEquals(42L, tuneParsed?.channelId)
        assertEquals("http://stream.m3u8", tuneParsed?.streamUrl)
        assertEquals("Channel 1", tuneParsed?.title)
    }

    @Test
    fun testStateSerialization() {
        val state = RemoteState(
            title = "News Today",
            channelName = "BBC News",
            isPlaying = true,
            positionMs = 120_000L,
            durationMs = 360_000L,
            volumePct = 85,
            isMuted = false,
        )
        val json = state.toJson()
        val parsed = RemoteState.fromJson(json)

        assertEquals("News Today", parsed.title)
        assertEquals("BBC News", parsed.channelName)
        assertTrue(parsed.isPlaying)
        assertEquals(120_000L, parsed.positionMs)
        assertEquals(360_000L, parsed.durationMs)
        assertEquals(85, parsed.volumePct)
        assertFalse(parsed.isMuted)
    }

    @Test
    fun testFavoritesSerialization() {
        val favs = listOf(
            RemoteFavoriteItem(1L, "Channel One", "http://logo1.png"),
            RemoteFavoriteItem(2L, "Channel Two", null),
        )
        val json = RemoteFavoriteItem.listToJson(favs)
        val parsed = RemoteFavoriteItem.listFromJson(json)

        assertEquals(2, parsed.size)
        assertEquals("Channel One", parsed[0].name)
        assertEquals("http://logo1.png", parsed[0].logoUrl)
        assertEquals("Channel Two", parsed[1].name)
    }

    @Test
    fun testPairingSuccessAndValidation() {
        val manager = RemotePairingManager()
        val pin = manager.generateNewCode()
        assertEquals(6, pin.length)

        val result = manager.attemptPair(pin, "Phone 1", "phone_device_1")
        assertTrue(result is RemotePairingManager.PairAttemptResult.Success)
        val token = (result as RemotePairingManager.PairAttemptResult.Success).token
        assertTrue(token.isNotBlank())
        assertTrue(manager.isValidToken(token))

        assertEquals(1, manager.getPairedDevices().size)
        assertTrue(manager.getPairedDevices()[0].id.isNotBlank())

        // Forget device
        manager.forgetDevice("phone_device_1")
        assertFalse(manager.isValidToken(token))
        assertEquals(0, manager.getPairedDevices().size)
    }

    @Test
    fun testPairingRateLimitAfterFiveFailures() {
        val manager = RemotePairingManager()
        val pin = manager.generateNewCode()

        for (i in 1..4) {
            val res = manager.attemptPair(if (pin == "999999") "000000" else "999999", "Bad Device", "dev_bad")
            assertEquals(RemotePairingManager.PairAttemptResult.InvalidCode, res)
            assertFalse(manager.isLockedOut())
        }

        // 5th failed attempt triggers rate limit
        val fifth = manager.attemptPair(if (pin == "999999") "000000" else "999999", "Bad Device", "dev_bad")
        assertTrue("Expected RateLimited on 5th failure", fifth is RemotePairingManager.PairAttemptResult.RateLimited)
        assertTrue(manager.isLockedOut())

        // Even with correct PIN, during lockout it must reject
        val duringLockout = manager.attemptPair(pin, "Bad Device", "dev_bad")
        assertTrue("Expected RateLimited during lockout", duringLockout is RemotePairingManager.PairAttemptResult.RateLimited)
    }

    @Test
    fun testServerClientEndToEnd() = runBlocking {
        val pairingManager = RemotePairingManager()
        val lastCommand = AtomicReference<RemoteCommand>()

        val commandHandler = object : RemoteCommandHandler {
            override fun handleCommand(command: RemoteCommand): Boolean {
                lastCommand.set(command)
                return true
            }
        }

        val stateProvider = object : RemoteStateProvider {
            override fun getState(): RemoteState = RemoteState(
                title = "Live Broadcast",
                channelName = "Euronews",
                isPlaying = true,
                volumePct = 70,
            )

            override fun getFavorites(): List<RemoteFavoriteItem> = listOf(
                RemoteFavoriteItem(10L, "Euronews"),
            )

            override fun getDiagnosticLogs(): String = "YAiPlayer log: status OK (no secrets here)"
        }

        val certificate = okhttp3.tls.HeldCertificate.Builder().commonName("TV").build()
        val tls = okhttp3.tls.HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val fingerprint = RemoteSecurity.hex(java.security.MessageDigest.getInstance("SHA-256").digest(certificate.certificate.encoded))
        val server = RemoteTvServer(
            pairingManager = pairingManager,
            commandHandler = commandHandler,
            stateProvider = stateProvider,
            tvName = "Living Room TV",
            socketFactory = { tls.sslContext().serverSocketFactory },
        )
        val port = server.start(0)
        assertNotNull(port)
        assertTrue(port!! > 0)

        val client = RemoteClient()
        try {
            // Pair client
            val pin = pairingManager.generateNewCode()
            val pairRes = client.pair(server.host!!, port, pin, "TestPhone", "client_123", fingerprint)
            assertTrue(pairRes is RemotePairResult.Success)
            val success = pairRes as RemotePairResult.Success
            assertEquals("Living Room TV", success.tvName)
            val token = success.token

            // Send command
            val sendRes = client.sendCommand(server.host!!, port, token, RemoteCommand.Nav(RemoteNavKey.OK))
            assertTrue(sendRes.isSuccess)
            val executed = lastCommand.get() as? RemoteCommand.Nav
            assertEquals(RemoteNavKey.OK, executed?.key)

            // Fetch state
            val stateRes = client.fetchState(server.host!!, port, token)
            assertTrue(stateRes.isSuccess)
            val state = stateRes.getOrThrow()
            assertEquals("Live Broadcast", state.title)
            assertEquals("Euronews", state.channelName)

            // Fetch logs
            val logsRes = client.fetchLogs(server.host!!, port, token)
            assertTrue(logsRes.isSuccess)
            val logs = logsRes.getOrThrow()
            assertEquals("", logs)

            // Fetch favorites
            val favsRes = client.fetchFavorites(server.host!!, port, token)
            assertTrue(favsRes.isSuccess)
            assertEquals(0, favsRes.getOrThrow().size)

        } finally {
            server.stop()
        }
    }
}
