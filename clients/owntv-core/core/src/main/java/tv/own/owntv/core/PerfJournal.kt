// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few hundred performance marks (start-up phases, home ready, EPG and catalogue syncs, the series merge),
 * kept in memory in every build. «Экспорт журнала» (Settings → Error log) appends them, so the owner can send
 * real numbers from his TV box without a computer or a developer build (task Opus №3, п.4).
 */
object PerfJournal {
    private const val MAX = 400
    private val lines = ArrayDeque<String>()
    private val time = ThreadLocal.withInitial { SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT) }

    @Synchronized
    fun add(message: String) {
        lines.addLast("${time.get()!!.format(Date())} $message")
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized
    fun snapshot(): String = lines.joinToString("\n")

    /** Runs [block] and records how long it took under [label]. */
    inline fun <T> measure(label: String, block: () -> T): T {
        val t0 = System.nanoTime()
        val result = block()
        add("$label ${(System.nanoTime() - t0) / 1_000_000} ms")
        return result
    }
}
