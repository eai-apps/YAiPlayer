// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import org.junit.Assert.*
import org.junit.Test

class RemoteSecurityTest {
    @Test fun pinIsSingleUseAndTokenStoredOnlyAsHash() {
        val store = InMemoryRemotePairingStore()
        val manager = RemotePairingManager(store)
        val pin = manager.generateNewCode()
        val token = (manager.attemptPair(pin, "phone", "id") as RemotePairingManager.PairAttemptResult.Success).token
        assertEquals(RemoteSecurity.hash(token), store.loadDevices().single().token)
        assertFalse(manager.isValidToken("wrong"))
        assertTrue(manager.attemptPair(pin, "other", "other") is RemotePairingManager.PairAttemptResult.InvalidCode)
        val nonce = "${System.currentTimeMillis()}:one"
        val body = RemoteCommand.Nav(RemoteNavKey.OK).toJson()
        val sig = RemoteSecurity.signature(token, "POST", "/cmd", nonce, body)
        assertFalse(manager.authorize("POST", "/cmd", nonce, RemoteSecurity.signature("wrong", "POST", "/cmd", nonce, body), body))
        assertFalse(manager.authorize("POST", "/cmd", nonce, sig, body + " "))
        assertFalse(manager.authorize("GET", "/state", nonce, sig, body))
        assertTrue(manager.authorize("POST", "/cmd", nonce, sig, body))
        assertFalse(manager.authorize("POST", "/cmd", nonce, sig, body))
        manager.forgetDevice(manager.getPairedDevices().single().id)
        val next = "${System.currentTimeMillis()}:two"
        assertFalse(manager.authorize("POST", "/cmd", next, RemoteSecurity.signature(token, "POST", "/cmd", next, body), body))
        assertFalse(RemotePairingManager(store).isValidToken(token))
    }
    @Test fun expiryAndLockoutCannotBeResetByRegeneratingCode() {
        var now = 1000L
        val manager = RemotePairingManager(clock = { now })
        val pin = manager.generateNewCode()
        now += RemotePairingManager.CODE_LIFETIME_MS
        assertTrue(manager.attemptPair(pin, "phone", "id") is RemotePairingManager.PairAttemptResult.InvalidCode)
        repeat(4) { manager.attemptPair("invalid", "phone", "id") }
        val newPin = manager.generateNewCode()
        assertTrue(manager.attemptPair(newPin, "phone", "id") is RemotePairingManager.PairAttemptResult.RateLimited)
        now += RemotePairingManager.LOCKOUT_DURATION_MS
        val valid = manager.generateNewCode()
        assertTrue(manager.attemptPair(valid, "phone", "id") is RemotePairingManager.PairAttemptResult.Success)
        val stopped = manager.generateNewCode()
        manager.invalidateCode()
        assertTrue(manager.attemptPair(stopped, "phone", "id") is RemotePairingManager.PairAttemptResult.InvalidCode)
    }
    @Test fun shareLinksExcludeCredentialForms() {
        assertTrue(RemoteSecurity.shareableLink("https://example.org/video.mp4"))
        listOf("file:///data/secret.mp4", "http://user:pass@example.org/a.mp4", "https://example.org/a.mp4?token=secret", "http://example.org/live/user/pass/1.ts").forEach {
            assertFalse(RemoteSecurity.shareableLink(it))
        }
    }
    @Test fun onlyPrivateIpv4Destinations() {
        listOf("10.0.0.2", "192.168.1.3", "172.16.1.4").forEach { RemoteSecurity.localAddress(it) }
        listOf("127.0.0.1", "8.8.8.8", "169.254.1.1", "0.0.0.0", "224.0.0.1", "::1", "example.com", "192.168.1.1@evil.com").forEach {
            assertTrue(it, runCatching { RemoteSecurity.localAddress(it) }.isFailure)
        }
    }
}
