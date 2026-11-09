// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProtocolTest {

    @Test
    fun `serialize and parse navigation commands`() {
        val commands = listOf(
            RemoteCommand.Up,
            RemoteCommand.Down,
            RemoteCommand.Left,
            RemoteCommand.Right,
            RemoteCommand.Ok,
            RemoteCommand.Back,
            RemoteCommand.Home,
            RemoteCommand.Menu
        )

        for (cmd in commands) {
            val serialized = RemoteProtocol.serializeCommand(cmd)
            val parsed = RemoteProtocol.parseCommand(serialized)
            assertEquals(cmd, parsed)
        }
    }

    @Test
    fun `serialize and parse media playback commands`() {
        val commands = listOf(
            RemoteCommand.PlayPause,
            RemoteCommand.SeekBack10,
            RemoteCommand.SeekFwd10,
            RemoteCommand.SeekBack60,
            RemoteCommand.SeekFwd60,
            RemoteCommand.VolumeUp,
            RemoteCommand.VolumeDown,
            RemoteCommand.VolumeMute,
            RemoteCommand.ChannelNext,
            RemoteCommand.ChannelPrev,
            RemoteCommand.Digit(7),
            RemoteCommand.Live,
            RemoteCommand.Archive
        )

        for (cmd in commands) {
            val serialized = RemoteProtocol.serializeCommand(cmd)
            val parsed = RemoteProtocol.parseCommand(serialized)
            assertEquals(cmd, parsed)
        }
    }

    @Test
    fun `serialize and parse text, send to tv and log commands`() {
        val textCmd = RemoteCommand.InsertText("Discovery HD")
        val parsedText = RemoteProtocol.parseCommand(RemoteProtocol.serializeCommand(textCmd))
        assertEquals(textCmd, parsedText)

        val sendCmd = RemoteCommand.SendToTv(
            streamUrl = "http://iptv.example/stream.m3u8",
            title = "Nature Documentary",
            channelId = 1002L,
            catchupUrl = "http://iptv.example/catchup"
        )
        val parsedSend = RemoteProtocol.parseCommand(RemoteProtocol.serializeCommand(sendCmd))
        assertEquals(sendCmd, parsedSend)

        val logCmd = RemoteCommand.ExportLog
        val parsedLog = RemoteProtocol.parseCommand(RemoteProtocol.serializeCommand(logCmd))
        assertEquals(logCmd, parsedLog)
    }

    @Test
    fun `serialize and parse remote state`() {
        val state = RemoteState(
            isPlaying = true,
            title = "Big Buck Bunny",
            channelOrMovieName = "Cinema HD",
            positionMs = 45000L,
            durationMs = 600000L,
            volume = 75,
            isMuted = false,
            streamUrl = "http://example.com/video.mp4",
            catchupUrl = "http://example.com/archive"
        )

        val json = RemoteProtocol.serializeState(state)
        val parsed = RemoteProtocol.parseState(json)

        assertEquals(state.isPlaying, parsed.isPlaying)
        assertEquals(state.title, parsed.title)
        assertEquals(state.channelOrMovieName, parsed.channelOrMovieName)
        assertEquals(state.positionMs, parsed.positionMs)
        assertEquals(state.durationMs, parsed.durationMs)
        assertEquals(state.volume, parsed.volume)
        assertEquals(state.isMuted, parsed.isMuted)
        assertEquals(state.streamUrl, parsed.streamUrl)
        assertEquals(state.catchupUrl, parsed.catchupUrl)
    }

    @Test
    fun `pairing manager correctly verifies pin and issues token`() {
        val manager = RemotePairingManager()
        val pin = manager.generateNewPin()
        assertEquals(6, pin.length)

        val result = manager.verifyPin(pin, "Pixel 7")
        assertTrue(result is PairingResult.Success)
        val token = (result as PairingResult.Success).token
        assertTrue(token.isNotBlank())
        assertTrue(manager.isTokenValid(token))
        assertEquals("Pixel 7", manager.getPairedDevices()[token])
    }

    @Test
    fun `pairing manager blocks after 5 failed attempts`() {
        val manager = RemotePairingManager()
        val pin = manager.generateNewPin()

        // 4 failed attempts
        for (i in 1..4) {
            val r = manager.verifyPin("000000", "Pixel")
            assertEquals(PairingResult.InvalidPin, r)
            assertFalse(manager.isBlocked())
        }

        // 5th failed attempt -> blocked
        val fifth = manager.verifyPin("000000", "Pixel")
        assertTrue(fifth is PairingResult.Blocked)
        assertTrue(manager.isBlocked())
        assertTrue(manager.getRemainingBlockSeconds() > 0)

        // Even with correct PIN, blocked
        val blockedAttempt = manager.verifyPin(pin, "Pixel")
        assertTrue(blockedAttempt is PairingResult.Blocked)
    }

    @Test
    fun `pairing manager rejects expired pin`() {
        val manager = RemotePairingManager(pinValidityMs = 1L)
        val pin = manager.generateNewPin()
        Thread.sleep(10)

        val result = manager.verifyPin(pin, "Pixel")
        assertEquals(PairingResult.Expired, result)
    }

    @Test
    fun `remote entitlement allows use`() {
        assertTrue(DefaultRemoteEntitlement.canUseRemote())
    }
}
