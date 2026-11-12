// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.util

import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Threads for the heavy refresh work (catalog sync, guide download and parse) at Android's background
 * priority. On the default IO pool a guide parse competed with the UI thread as an equal, and on a weak TV box
 * the menus froze for the whole 10+ minutes of a large guide (owner, 12.11). The scheduler now gives the UI and
 * the player the CPU first; the refresh takes what is left. Network waits and Room's own executors are unchanged.
 */
object BackgroundWork {
    /** Four threads: enough for the portal's parallel requests, few enough not to crowd a 4-core box. */
    const val THREADS = 4

    val dispatcher: CoroutineDispatcher by lazy {
        val n = AtomicInteger()
        Executors.newFixedThreadPool(THREADS, ThreadFactory { r ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                r.run()
            }, "owntv-refresh-${n.incrementAndGet()}").apply { isDaemon = true }
        }).asCoroutineDispatcher()
    }
}
