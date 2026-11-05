package tv.own.owntv.core.stalker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Test matrix for Stalker portal authentication and device/URL helpers.
 * Verifies the login flow in [StalkerAuthManager.sessionFor], device derivation,
 * and channel logo URL resolution.
 */
class StalkerLoginMatrixTest {

    private val baseCreds = StalkerCredentials(
        sourceId = 42L,
        portalUrl = "http://portal/c/",
        mac = "00:1A:79:AA:BB:CC",
    )

    /**
     * Fake client recording API calls and parameters, allowing tests to simulate
     * portal responses and failures.
     */
    private class FakeClient(
        var loginException: IOException? = null,
        var profileException: IOException? = null,
    ) : StalkerClient(okhttp3.OkHttpClient()) {
        val calls = ArrayList<String>()
        var lastLogin: String? = null
        var lastPassword: String? = null
        var doAuthIdentity: StalkerDeviceIdentity? = null
        var getProfileIdentity: StalkerDeviceIdentity? = null

        override suspend fun resolveHandshake(portalUrl: String, mac: String, userAgent: String?): Handshake {
            calls += "handshake"
            return Handshake(apiBase = "http://portal/portal.php", token = "t${calls.size}")
        }

        override suspend fun doAuth(
            apiBase: String, mac: String, token: String, userAgent: String?,
            login: String, password: String, identity: StalkerDeviceIdentity,
        ) {
            calls += "do_auth"
            lastLogin = login
            lastPassword = password
            doAuthIdentity = identity
            loginException?.let { throw it }
        }

        override suspend fun getProfile(
            apiBase: String, mac: String, token: String, userAgent: String?, identity: StalkerDeviceIdentity,
        ): Map<String, String> {
            calls += "profile"
            getProfileIdentity = identity
            profileException?.let { throw it }
            return mapOf("status" to "1")
        }
    }

    // 1. Только MAC (логин пустой): do_auth не вызывается, get_profile вызывается, сессия открывается.
    @Test
    fun macOnlyLoginDoesNotCallDoAuthCallsGetProfileOpensSession() = runBlocking {
        val client = FakeClient()
        val auth = StalkerAuthManager(client)
        val session = auth.sessionFor(baseCreds.copy(login = null, password = null))

        assertEquals(listOf("handshake", "profile"), client.calls)
        assertEquals("t1", session.token)
        assertEquals("http://portal/portal.php", session.apiBase)
        assertEquals("1", session.profile["status"])

        // Also verify a blank login string behaves identically
        val clientBlank = FakeClient()
        val authBlank = StalkerAuthManager(clientBlank)
        val sessionBlank = authBlank.sessionFor(baseCreds.copy(login = "   ", password = "secret"))
        assertEquals(listOf("handshake", "profile"), clientBlank.calls)
        assertEquals("t1", sessionBlank.token)
    }

    // 2. Логин+пароль, портал принимает: do_auth вызван с логином/паролем, затем get_profile, сессия открыта.
    @Test
    fun loginAndPasswordAcceptedCallsDoAuthThenGetProfileOpensSession() = runBlocking {
        val client = FakeClient()
        val auth = StalkerAuthManager(client)
        val creds = baseCreds.copy(login = "stalkerUser", password = "secretPassword")

        val session = auth.sessionFor(creds)

        assertEquals(listOf("handshake", "do_auth", "profile"), client.calls)
        assertEquals("stalkerUser", client.lastLogin)
        assertEquals("secretPassword", client.lastPassword)
        assertEquals("t1", session.token)
        assertEquals("1", session.profile["status"])
    }

    // 3. Логин+пароль, портал отвечает отказом (StalkerAuthException), но get_profile по MAC проходит → сессия ОТКРЫТА (запасной вход по MAC).
    @Test
    fun loginRefusedWithAuthExceptionFallbackToMacProfileSucceedsOpensSession() = runBlocking {
        val client = FakeClient(
            loginException = StalkerClient.StalkerAuthException("Portal login rejected — check username and password"),
        )
        val auth = StalkerAuthManager(client)
        val creds = baseCreds.copy(login = "stalkerUser", password = "secretPassword")

        val session = auth.sessionFor(creds)

        assertEquals(listOf("handshake", "do_auth", "profile"), client.calls)
        assertEquals("t1", session.token)
        assertEquals("1", session.profile["status"])
    }

    // 4. Логин+пароль, отказ, и get_profile тоже падает → в итоге выбрасывается именно исходный StalkerAuthException (сообщение портала).
    @Test
    fun loginRefusedAndGetProfileFailsThrowsOriginalStalkerAuthException() {
        val client = FakeClient(
            loginException = StalkerClient.StalkerAuthException("Portal login rejected: invalid credentials"),
            profileException = StalkerClient.StalkerAuthException("MAC authorization failed"),
        )
        val auth = StalkerAuthManager(client)
        val creds = baseCreds.copy(login = "stalkerUser", password = "wrongPassword")

        val thrown = assertThrows(StalkerClient.StalkerAuthException::class.java) {
            runBlocking { auth.sessionFor(creds) }
        }

        assertEquals("Portal login rejected: invalid credentials", thrown.message)
        assertEquals(listOf("handshake", "do_auth", "profile"), client.calls)
    }

    // 5. Логин без пароля: do_auth вызывается с пустым паролем.
    @Test
    fun loginWithoutPasswordCallsDoAuthWithEmptyPassword() = runBlocking {
        val client = FakeClient()
        val auth = StalkerAuthManager(client)
        val creds = baseCreds.copy(login = "userWithoutPassword", password = null)

        val session = auth.sessionFor(creds)

        assertEquals(listOf("handshake", "do_auth", "profile"), client.calls)
        assertEquals("userWithoutPassword", client.lastLogin)
        assertEquals("", client.lastPassword)
        assertEquals("t1", session.token)
    }

    // 6. Идентификаторы: при заданных deviceIdentity они передаются и в do_auth, и в get_profile.
    @Test
    fun deviceIdentityPassedToBothDoAuthAndGetProfile() = runBlocking {
        val identity = StalkerDeviceIdentity(
            serialNumber = "1234567890ABC",
            deviceId = "DEVICE_ID_1",
            deviceId2 = "DEVICE_ID_2",
            signature = "DEVICE_SIG",
        )
        val client = FakeClient()
        val auth = StalkerAuthManager(client)
        val creds = baseCreds.copy(
            login = "user",
            password = "password",
            deviceIdentity = identity,
        )

        auth.sessionFor(creds)

        assertEquals(listOf("handshake", "do_auth", "profile"), client.calls)
        assertEquals(identity, client.doAuthIdentity)
        assertEquals(identity, client.getProfileIdentity)
    }

    // 7. StalkerDeviceDerivation.identityFor(mac): одинаковый MAC всегда даёт одинаковые sn/deviceId/deviceId2; разный MAC → разные; sn ровно 13 символов, верхний регистр.
    @Test
    fun deviceDerivationConsistencyAndFormat() {
        val mac1 = "00:1A:79:AA:BB:CC"
        val mac2 = "00:1A:79:11:22:33"

        val id1A = StalkerDeviceDerivation.identityFor(mac1)
        val id1B = StalkerDeviceDerivation.identityFor(mac1)
        val id2 = StalkerDeviceDerivation.identityFor(mac2)

        // Same MAC gives identical sn/deviceId/deviceId2
        assertEquals(id1A, id1B)
        assertEquals(id1A.serialNumber, id1B.serialNumber)
        assertEquals(id1A.deviceId, id1B.deviceId)
        assertEquals(id1A.deviceId2, id1B.deviceId2)

        // Different MAC gives different values
        assertNotEquals(id1A.serialNumber, id2.serialNumber)
        assertNotEquals(id1A.deviceId, id2.deviceId)
        assertNotEquals(id1A.deviceId2, id2.deviceId2)

        // Serial number is exactly 13 characters, uppercase hex
        val sn = id1A.serialNumber
        assertNotNull(sn)
        assertEquals(13, sn!!.length)
        assertEquals(sn.uppercase(), sn)
        assertTrue(sn.all { it in '0'..'9' || it in 'A'..'F' })

        // Check the second serial number too
        val sn2 = id2.serialNumber
        assertNotNull(sn2)
        assertEquals(13, sn2!!.length)
        assertEquals(sn2.uppercase(), sn2)
        assertTrue(sn2.all { it in '0'..'9' || it in 'A'..'F' })
    }

    // 8. StalkerClient.absoluteLogoUrl(portalUrl, logo): "212.png" + "http://h/stalker_portal/c/" → "http://h/stalker_portal/misc/logos/320/212.png"; "http://x/y.png" остаётся как есть; "/a/b.png" → "http://h/a/b.png"; пусто/null → null.
    @Test
    fun absoluteLogoUrlResolvesAccordingToStalkerConventions() {
        val portalUrl = "http://h/stalker_portal/c/"

        // "212.png" + "http://h/stalker_portal/c/" -> "http://h/stalker_portal/misc/logos/320/212.png"
        assertEquals(
            "http://h/stalker_portal/misc/logos/320/212.png",
            StalkerClient.absoluteLogoUrl(portalUrl, "212.png"),
        )

        // "http://x/y.png" остаётся как есть
        assertEquals(
            "http://x/y.png",
            StalkerClient.absoluteLogoUrl(portalUrl, "http://x/y.png"),
        )

        // "/a/b.png" -> "http://h/a/b.png"
        assertEquals(
            "http://h/a/b.png",
            StalkerClient.absoluteLogoUrl(portalUrl, "/a/b.png"),
        )

        // пусто/null -> null
        assertNull(StalkerClient.absoluteLogoUrl(portalUrl, null))
        assertNull(StalkerClient.absoluteLogoUrl(portalUrl, ""))
        assertNull(StalkerClient.absoluteLogoUrl(portalUrl, "   "))
    }
}
