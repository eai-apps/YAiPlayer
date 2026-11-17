// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Owner 17.11: «если у меня в профиле 20 подпрофилей и все 20 начнут обновляться одновременно — это положит
 * всё». Channel/film loads and guide loads take turns: one at a time for the whole app, the rest wait in line.
 * Only the workers' own runs pass through here (never a call that waits on another worker), so it cannot
 * deadlock.
 */
object SyncGate {
    private const val PERMITS = 1
    private val heavy = Semaphore(PERMITS)

    suspend fun <T> heavy(block: suspend () -> T): T = heavy.withPermit { block() }
}
