package tv.own.owntv.player.diagnostic

import android.content.Context
import android.os.Build
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.repository.splitGuideUrls
import tv.own.owntv.core.util.CrashRecorder
import tv.own.owntv.core.util.GuideTrace
import tv.own.owntv.player.LiveDiagnosticsLog
import tv.own.owntv.player.PlaybackErrorLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compiles a sanitized diagnostic text report for support and troubleshooting.
 * All sensitive information (passwords, tokens, credentials, provider URLs) is redacted.
 */
object DiagnosticLogExporter {

    private const val PREFIX = "yaiplayer-log-"
    private const val EXTENSION = ".txt"
    private const val REDACTED = "*** (redacted)"

    private val URL_HOST = Regex("(?i)(https?://)(?:[^/\\s@]*@)?[^/\\s:?#]+(?::\\d+)?")
    private val MAC_ADDRESS = Regex("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b")
    private val AUTH_HEADER = Regex("(?i)\\b(authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n]+")
    private val BEARER = Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+")

    fun formatFilename(timestamp: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        return "$PREFIX${fmt.format(Date(timestamp))}$EXTENSION"
    }

    /**
     * Sanitizes strings by replacing passwords, usernames, tokens, credentials, and API keys.
     */
    fun maskSensitiveText(input: String): String {
        var text = input
        // Mask passwords, tokens, API keys in query parameters or key-value pairs
        text = text.replace(Regex("(?i)(password|pass|passwd|pwd|token|signature|secret|key|api_key)=([^&\\s,;\"'<>]+)")) { match ->
            "${match.groupValues[1]}=***"
        }
        // Mask usernames
        text = text.replace(Regex("(?i)(username|user|login)=([^&\\s,;\"'<>]+)")) { match ->
            "${match.groupValues[1]}=***"
        }
        // Mask inline URL credentials (http://user:pass@host)
        text = text.replace(Regex("(https?://)([^:/\\s]+):([^@/\\s]+)@")) { match ->
            "${match.groupValues[1]}***:***@"
        }
        // Xtream/Stalker credentials that sit in the PATH (/live/user/pass/1.ts, /timeshift/..., short form)
        // and Stalker's mac=; the app's own URL redaction already knows these shapes.
        text = tv.own.owntv.core.network.HttpClient.redactUrl(text)
        // A provider's host identifies the account's panel as surely as the credentials do: keep scheme and path only.
        text = text.replace(URL_HOST, "$1***")
        text = text.replace(MAC_ADDRESS, "**:**:**:**:**:**")
        text = text.replace(AUTH_HEADER) { m -> "${m.groupValues[1]}: ***" }
        text = text.replace(BEARER) { m -> "${m.groupValues[1]}***" }
        return text
    }

    /**
     * Builds the full human-readable plain text diagnostic report.
     */
    fun buildReport(
        context: Context,
        sources: List<SourceEntity> = emptyList(),
        timestamp: Long = System.currentTimeMillis(),
    ): String = buildString {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        appendLine("======================================================================")
        appendLine("YAiPlayer Diagnostic Report")
        appendLine("======================================================================")
        val appVer = CoreBuildInfo.versionName.ifBlank { "unknown" }
        val appCode = CoreBuildInfo.versionCode
        appendLine("App Version : YAiPlayer $appVer ($appCode)")
        appendLine("Device      : ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android     : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Export Time : ${dateFmt.format(Date(timestamp))}")
        appendLine()

        appendLine("----------------------------------------------------------------------")
        appendLine("Active Sources (${sources.size})")
        appendLine("----------------------------------------------------------------------")
        if (sources.isEmpty()) {
            appendLine("No active sources configured.")
        } else {
            sources.forEachIndexed { idx, s ->
                appendLine("[Source #${idx + 1}]")
                appendLine("  Name     : ${s.name}")
                appendLine("  Type     : ${s.type}")
                appendLine("  Address  : $REDACTED")
                appendLine("  User     : $REDACTED")
                appendLine("  Password : $REDACTED")
                if (!s.mac.isNullOrBlank()) {
                    appendLine("  MAC      : $REDACTED")
                }
                if (!s.epgUrl.isNullOrBlank()) {
                    val count = splitGuideUrls(s.epgUrl).size
                    appendLine("  EPG URLs : $REDACTED ($count feed(s) configured)")
                }
                appendLine("  Sync     : Live=${s.syncLive}, Movies=${s.syncMovies}, Series=${s.syncSeries}")
                appendLine("  PreferHls: ${s.preferHls}")
                appendLine()
            }
        }

        appendLine("----------------------------------------------------------------------")
        appendLine("Guide Trace")
        appendLine("----------------------------------------------------------------------")
        val guideTrace = GuideTrace.snapshot()
        if (guideTrace.isEmpty()) {
            appendLine("No guide trace recorded.")
        } else {
            guideTrace.forEach { appendLine(maskSensitiveText(it)) }
        }
        appendLine()

        appendLine("----------------------------------------------------------------------")
        appendLine("Playback Error Log")
        appendLine("----------------------------------------------------------------------")
        val playbackErrors = runCatching { PlaybackErrorLog.read(context) }.getOrDefault(emptyList())
        if (playbackErrors.isEmpty()) {
            appendLine("No playback errors recorded.")
        } else {
            playbackErrors.forEach { e ->
                val timeStr = dateFmt.format(Date(e.atMs))
                val liveStr = if (e.live) "live" else "vod"
                appendLine("[$timeStr] ${e.kind} · ${e.engine} · $liveStr")
                val reasonText = e.reason?.name ?: e.legacyReason
                reasonText?.let { appendLine("  reason: ${maskSensitiveText(it)}") }
                e.spec?.let { appendLine("  spec  : ${maskSensitiveText(it)}") }
                e.raw?.let { appendLine("  raw   : ${maskSensitiveText(it)}") }
                appendLine()
            }
        }

        appendLine("----------------------------------------------------------------------")
        appendLine("Recent Errors & Live Diagnostics")
        appendLine("----------------------------------------------------------------------")
        val crash = CrashRecorder.read(context)
        if (!crash.isNullOrBlank()) {
            appendLine("--- Last Crash ---")
            appendLine(maskSensitiveText(crash))
            appendLine()
        }
        val liveDiag = LiveDiagnosticsLog.snapshot()
        if (liveDiag.isNotBlank()) {
            appendLine("--- Live Diagnostics ---")
            appendLine(maskSensitiveText(liveDiag))
        }
        val perf = tv.own.owntv.core.PerfJournal.snapshot()
        if (perf.isNotBlank()) {
            appendLine()
            appendLine("--- Performance ---")
            appendLine(maskSensitiveText(perf))
        }
    }

    /**
     * Writes the report to cache for FileProvider sharing.
     */
    fun exportToCache(
        context: Context,
        sources: List<SourceEntity> = emptyList(),
        dirName: String = "backup-share",
    ): File {
        val dir = File(context.cacheDir, dirName)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, formatFilename())
        val content = buildReport(context, sources)
        file.writeText(content, Charsets.UTF_8)
        return file
    }
}
