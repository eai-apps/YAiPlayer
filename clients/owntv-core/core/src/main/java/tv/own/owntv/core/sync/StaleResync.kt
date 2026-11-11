// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.core.sync

/**
 * Pure staleness decision for background and startup resync.
 *
 * A source is considered stale when:
 * - [lastSyncAt] is null or non-positive (never synced or missing data);
 * - or the elapsed time since [lastSyncAt] meets or exceeds [thresholdHours].
 */
fun isStale(lastSyncAt: Long?, nowMs: Long, thresholdHours: Int): Boolean {
    if (lastSyncAt == null || lastSyncAt <= 0L) return true
    if (thresholdHours <= 0) return true
    val thresholdMs = thresholdHours * 3_600_000L
    return (nowMs - lastSyncAt) >= thresholdMs
}
