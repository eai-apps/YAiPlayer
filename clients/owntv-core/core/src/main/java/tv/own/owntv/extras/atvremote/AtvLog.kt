// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.atvremote

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Short in-memory journal of the system remote: every connection attempt, TLS result and protocol step with its
 * time. The owner copies it from the error card («Скопировать журнал») so a failure on his TV can be diagnosed
 * without a computer. Holds no secrets: no keys, no pairing codes, no typed text.
 */
object AtvLog {
    private const val MAX = 300
    private val lines = ArrayDeque<String>()
    private val time = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }

    @Synchronized
    fun add(message: String) {
        lines.addLast("${time.get()!!.format(Date())} $message")
        while (lines.size > MAX) lines.removeFirst()
    }

    fun error(step: String, t: Throwable) {
        val chain = generateSequence(t) { it.cause }.take(4)
            .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message?.take(120).orEmpty()}" }
        add("$step FAILED $chain")
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() = lines.clear()
}
