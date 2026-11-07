package tv.own.owntv.core.util

import java.util.ArrayDeque

/**
 * Short in-memory record of what the last portal syncs did (guide route taken, programmes kept, channels with
 * a logo). Shown on the error-log page so a missing guide or logo can be diagnosed from a photo of the screen.
 * Never holds URLs, tokens or credentials — callers pass counts and route names only.
 */
object GuideTrace {
    private const val MAX = 24
    private val lines = ArrayDeque<String>()
    private val lock = Any()

    fun add(message: String) {
        synchronized(lock) {
            lines.addLast(message)
            while (lines.size > MAX) lines.pollFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }
}
