// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Owner 16.11: the TV menu froze on fast scrolling and the app sometimes closed with nothing in the error
 * log — a Java crash is recorded by [CrashRecorder], so this was a hang (the system closes an app that stops
 * answering) or the box running out of memory. This watches the main thread: when it does not answer for
 * [STALL_MS], one line goes to [report] with how long, what the main thread was doing (top frames) and the
 * memory left. Nothing is written while the app runs smoothly.
 */
object MainThreadWatchdog {
    private const val STALL_MS = 700L
    private const val TICK_MS = 250L
    private const val MAX_FRAMES = 14
    private const val MIN_GAP_MS = 3_000L

    @Volatile private var started = false

    fun start(context: Context, report: (String) -> Unit) {
        if (started) return
        started = true
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        val thread = Thread({
            val answeredAt = java.util.concurrent.atomic.AtomicLong(SystemClock.uptimeMillis())
            var lastReport = 0L
            var reportedThisStall = false
            val ping = Runnable { answeredAt.set(SystemClock.uptimeMillis()) }
            while (true) {
                main.post(ping)
                try { Thread.sleep(TICK_MS) } catch (_: InterruptedException) { return@Thread }
                val now = SystemClock.uptimeMillis()
                val waited = now - answeredAt.get()
                if (waited < STALL_MS) { reportedThisStall = false; continue }
                if (reportedThisStall || now - lastReport < MIN_GAP_MS) continue
                reportedThisStall = true
                lastReport = now
                val line = describe(app, waited)
                runCatching { report(line) }
                runCatching { keep(app, line) }
            }
        }, "yai-watchdog")
        thread.isDaemon = true
        thread.priority = Thread.MIN_PRIORITY
        thread.start()
    }

    /** A memory line for [android.app.Application.onTrimMemory] and the like. */
    fun memory(context: Context): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / MB
        val max = rt.maxMemory() / MB
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        return "heap ${used}/${max} MB, box free ${info.availMem / MB} MB of ${info.totalMem / MB} MB${if (info.lowMemory) ", LOW" else ""}"
    }

    /**
     * Stalls (and memory warnings) kept on disk: if the system then closes the hung app, the in-memory
     * journal goes with it, while this file is read back into the next export. Last [KEEP_LINES] lines.
     */
    fun keep(context: Context, line: String) {
        val f = file(context)
        f.parentFile?.mkdirs()
        val stamp = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val lines = (if (f.exists()) f.readLines() else emptyList()) + "$stamp $line"
        f.writeText(lines.takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()

    private fun file(context: Context) = java.io.File(java.io.File(context.filesDir, "diagnostics"), "stalls.txt")

    private const val KEEP_LINES = 30

    private fun describe(context: Context, waited: Long): String {
        val frames = Looper.getMainLooper().thread.stackTrace
            .take(MAX_FRAMES)
            .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        return "main-thread stall ${waited}ms; ${memory(context)}; at $frames"
    }

    private const val MB = 1024L * 1024L
}
