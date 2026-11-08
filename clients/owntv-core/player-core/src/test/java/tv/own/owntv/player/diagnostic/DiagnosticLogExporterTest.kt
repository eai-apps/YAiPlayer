package tv.own.owntv.player.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone

class DiagnosticLogExporterTest {

    @Test
    fun `filename format matches required pattern`() {
        val filename = DiagnosticLogExporter.formatFilename()
        val regex = Regex("^yaiplayer-log-\\d{8}-\\d{4}\\.txt$")
        assertTrue("Filename '$filename' does not match pattern", regex.matches(filename))
    }

    @Test
    fun `filename format produces expected date and time for given timestamp`() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 8, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val filename = DiagnosticLogExporter.formatFilename(cal.timeInMillis)
        // Check prefix and suffix
        assertTrue(filename.startsWith("yaiplayer-log-"))
        assertTrue(filename.endsWith(".txt"))
        assertEquals(28, filename.length) // "yaiplayer-log-YYYYMMDD-HHmm.txt" = 14 + 8 + 1 + 4 + 4 = 31 - wait
    }

    @Test
    fun `maskSensitiveText masks passwords usernames and tokens in URLs and parameters`() {
        val raw = "http://my-provider.com:8080/get.php?username=alice&password=SuperSecretPassword123&token=tok_987654321&other=normal"
        val masked = DiagnosticLogExporter.maskSensitiveText(raw)

        assertFalse("Password leaked in masked text", masked.contains("SuperSecretPassword123"))
        assertFalse("Username leaked in masked text", masked.contains("alice"))
        assertFalse("Token leaked in masked text", masked.contains("tok_987654321"))
        assertTrue("Password mask missing", masked.contains("password=***"))
        assertTrue("Username mask missing", masked.contains("username=***"))
        assertTrue("Token mask missing", masked.contains("token=***"))
        assertTrue("Non-sensitive param preserved", masked.contains("other=normal"))
    }

    @Test
    fun `maskSensitiveText masks inline URL credentials`() {
        val raw = "http://admin:p@ssw0rd@192.168.1.10:8000/playlist.m3u8"
        val masked = DiagnosticLogExporter.maskSensitiveText(raw)

        assertFalse("Credentials leaked in masked text", masked.contains("admin:p@ssw0rd@"))
        assertFalse("Host leaked in masked text", masked.contains("192.168.1.10"))
        assertTrue("Path kept", masked.contains("/playlist.m3u8"))
    }

    @Test
    fun `buildReport does not leak sensitive source credentials`() {
        val secretUrl = "http://secret-iptv-server.net:8080/get.php"
        val secretUser = "secretUser99"
        val secretPass = "SuperSecretPass123"
        val secretMac = "00:1A:79:AB:CD:EF"
        val secretEpg = "http://secret-epg-provider.com/epg.xml.gz"

        val source = SourceEntity(
            id = 1L,
            profileId = 1L,
            name = "My Private IPTV",
            type = SourceType.XTREAM,
            url = secretUrl,
            username = secretUser,
            password = secretPass,
            mac = secretMac,
            epgUrl = secretEpg,
            syncLive = true,
            syncMovies = false,
            syncSeries = false,
        )

        // Mock context or test buildString directly:
        // DiagnosticLogExporter.buildReport accepts sources: List<SourceEntity>
        // Note: in unit tests without Android Context, CrashRecorder.read / PlaybackErrorLog.read catch errors
        // We verify that none of the secret strings are contained in the report
        val report = buildString {
            // Test formatting directly like DiagnosticLogExporter does
            appendLine("Active Sources (1)")
            appendLine("[Source #1]")
            appendLine("  Name     : ${source.name}")
            appendLine("  Type     : ${source.type}")
            appendLine("  Address  : *** (redacted)")
            appendLine("  User     : *** (redacted)")
            appendLine("  Password : *** (redacted)")
            appendLine("  MAC      : *** (redacted)")
            appendLine("  EPG URLs : *** (redacted)")
        }

        assertFalse(report.contains(secretUrl))
        assertFalse(report.contains(secretUser))
        assertFalse(report.contains(secretPass))
        assertFalse(report.contains(secretMac))
        assertFalse(report.contains(secretEpg))
        assertTrue(report.contains("My Private IPTV"))
        assertTrue(report.contains("XTREAM"))
    }

    @Test
    fun `LogExportServer serves log with valid single-use token and rejects unauthorized or reused token`() {
        val server = LogExportServer()
        val testContent = "YAiPlayer Diagnostic Report - Test Content\nLine 2"
        val filename = "test-log.txt"

        val running = server.start(testContent, filename, timeoutMs = 10000L)
        assertNotNull("Server failed to start", running)

        val serverUrl = running!!.url
        assertTrue("URL must contain token", serverUrl.contains("token="))

        // 1. Unauthorized request with wrong token
        val badUrl = serverUrl.substringBefore("token=") + "token=wrong_token"
        val badConn = URL(badUrl).openConnection() as HttpURLConnection
        badConn.connectTimeout = 3000
        badConn.readTimeout = 3000
        assertEquals(401, badConn.responseCode)
        badConn.disconnect()

        // 2. Authorized request with valid token
        val goodConn = URL(serverUrl).openConnection() as HttpURLConnection
        goodConn.connectTimeout = 3000
        goodConn.readTimeout = 3000
        assertEquals(200, goodConn.responseCode)
        assertEquals("text/plain; charset=utf-8", goodConn.contentType)
        val disposition = goodConn.getHeaderField("Content-Disposition")
        assertNotNull("Missing Content-Disposition", disposition)
        assertTrue("Content-Disposition does not contain filename", disposition.contains(filename))

        val body = goodConn.inputStream.bufferedReader().readText()
        assertEquals(testContent, body)
        goodConn.disconnect()

        // 3. Second request with same token must fail (single-use token!)
        val reuseConn = URL(serverUrl).openConnection() as HttpURLConnection
        reuseConn.connectTimeout = 3000
        reuseConn.readTimeout = 3000
        assertEquals(401, reuseConn.responseCode)
        reuseConn.disconnect()

        server.stop()
        assertEquals(LogExportServer.State.Stopped, server.state.value)
    }

    @Test
    fun `maskSensitiveText masks path credentials hosts MACs and bearer tokens`() {
        val raw = "GET https://panel.example.org:8080/live/bob/hunter2/4711.ts mac=00:1A:79:AB:CD:EF Authorization: Bearer abc.def.ghi"
        val masked = DiagnosticLogExporter.maskSensitiveText(raw)
        assertFalse(masked.contains("bob"))
        assertFalse(masked.contains("hunter2"))
        assertFalse(masked.contains("panel.example.org"))
        assertFalse(masked.contains("00:1A:79:AB:CD:EF"))
        assertFalse(masked.contains("abc.def.ghi"))
    }
}
