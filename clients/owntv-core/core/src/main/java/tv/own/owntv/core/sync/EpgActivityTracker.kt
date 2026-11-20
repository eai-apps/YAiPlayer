package tv.own.owntv.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide "an EPG sync is running" signal for the shell's status pill. Mirrors [SyncActivityTracker]:
 * [tv.own.owntv.core.sync.work.EpgSyncWorker] reports here (started / progress / finished), so the same
 * unobtrusive pill that tracks catalog syncs also reflects guide/EPG downloads. Mostly observational;
 * [guideChanged] tells live screens a guide download ended.
 */
class EpgActivityTracker {

    data class ActiveEpgSync(
        val sourceId: Long,
        val sourceName: String,
        val channels: Int = 0,
        val programmes: Int = 0,
        /** 0..100 when the source can tell how far it is; null shows counts only (no made-up bar). */
        val percent: Int? = null,
        /** Owner 16.11: the reserve guide's long step — channels matched so far of all that lacked a guide. */
        val matched: Int = 0,
        val matchTotal: Int = 0,
    )

    private val _active = MutableStateFlow<Map<Long, ActiveEpgSync>>(emptyMap())

    /** All currently-running EPG syncs keyed by sourceId (usually 0 or 1 entries). */
    val active: StateFlow<Map<Long, ActiveEpgSync>> = _active.asStateFlow()

    fun started(sourceId: Long, sourceName: String) {
        _active.value = _active.value + (sourceId to ActiveEpgSync(sourceId, sourceName))
    }

    fun progress(sourceId: Long, channels: Int, programmes: Int) {
        val existing = _active.value[sourceId] ?: return
        _active.value = _active.value + (sourceId to existing.copy(channels = channels, programmes = programmes))
    }

    fun fraction(sourceId: Long, fraction: Float) {
        val existing = _active.value[sourceId] ?: return
        val pct = (fraction * 100).toInt().coerceIn(0, 99)
        if (existing.percent == pct) return
        _active.value = _active.value + (sourceId to existing.copy(percent = pct))
    }

    /** The reserve guide is matching [done] of [total] channels without a programme to its own channels. */
    fun matching(sourceId: Long, done: Int, total: Int) {
        val existing = _active.value[sourceId] ?: return
        val pct = if (total > 0) (done * 100 / total).coerceIn(0, 99) else existing.percent
        _active.value = _active.value + (sourceId to existing.copy(matched = done, matchTotal = total, percent = pct))
    }

    fun finished(sourceId: Long) {
        _active.value = _active.value - sourceId
        _guideChanged.value = _guideChanged.value + 1
    }

    private val _guideChanged = MutableStateFlow(0L)

    /**
     * Owner 20.11: bumped every time a guide download ends. Live screens drop what they remembered about
     * channels «without a programme» and read the guide again — otherwise a programme loaded a minute ago
     * stayed hidden (and came and went) until their own caches ran out.
     */
    val guideChanged: StateFlow<Long> = _guideChanged.asStateFlow()
}
