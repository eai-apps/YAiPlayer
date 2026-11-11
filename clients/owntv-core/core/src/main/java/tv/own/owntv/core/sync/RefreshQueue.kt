// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One automatic refresh, run one after another by [tv.own.owntv.core.sync.work.RefreshQueueWorker]. */
sealed interface RefreshJob {
    val id: Long

    /** Channels, categories and VOD of a playlist or portal. */
    data class Catalog(override val id: Long, val baseItemCount: Int) : RefreshJob

    /** One guide feed (an XMLTV URL, a portal's own guide, or the first built-in feed). */
    data class Guide(override val id: Long, val baseProgrammes: Int) : RefreshJob
}

/** What [RefreshQueuePlanner] needs to know about a guide feed. */
data class GuideFeedInfo(
    val id: Long,
    val url: String,
    val builtin: Boolean,
    /** Its own schedule or the stale catch-up asks for it now. */
    val due: Boolean,
    val baseProgrammes: Int,
)

/** What [RefreshQueuePlanner] needs to know about a playlist or portal. */
data class PlaylistInfo(
    val id: Long,
    val active: Boolean,
    val due: Boolean,
    val baseItemCount: Int,
    /** Guide URLs this playlist offers (`stalker://<id>` for a portal's own guide). */
    val guideUrls: List<String>,
)

/**
 * The order automatic refreshes run in at start-up, instead of all at once:
 * 1. the active profile's playlists and portals;
 * 2. the guides those playlists use;
 * 3. guide feeds no playlist claims (added by hand), one by one;
 * 4. the built-in guide, last.
 * Guides that belong only to playlists of other profiles are left for when that profile is opened (or for the
 * nightly refresh), so the user waits for none of them.
 */
object RefreshQueuePlanner {
    /**
     * [includeOtherProfiles]: the background refresh (nothing on screen waits for it) also brings the other
     * profiles up to date, after the active one.
     */
    fun plan(playlists: List<PlaylistInfo>, feeds: List<GuideFeedInfo>, includeOtherProfiles: Boolean = false): List<RefreshJob> {
        val activeUrls = playlists.filter { it.active }.flatMap { it.guideUrls }.map(::key).toSet()
        val otherUrls = playlists.filterNot { it.active }.flatMap { it.guideUrls }.map(::key).toSet()
        val jobs = ArrayList<RefreshJob>()
        playlists.filter { it.active && it.due }.forEach { jobs += RefreshJob.Catalog(it.id, it.baseItemCount) }
        val due = feeds.filter { it.due }
        due.filter { !it.builtin && key(it.url) in activeUrls }.forEach { jobs += RefreshJob.Guide(it.id, it.baseProgrammes) }
        due.filter { !it.builtin && key(it.url) !in activeUrls && key(it.url) !in otherUrls }
            .forEach { jobs += RefreshJob.Guide(it.id, it.baseProgrammes) }
        if (includeOtherProfiles) {
            playlists.filter { !it.active && it.due }.forEach { jobs += RefreshJob.Catalog(it.id, it.baseItemCount) }
            due.filter { !it.builtin && key(it.url) !in activeUrls && key(it.url) in otherUrls }
                .forEach { jobs += RefreshJob.Guide(it.id, it.baseProgrammes) }
        }
        due.firstOrNull { it.builtin }?.let { jobs += RefreshJob.Guide(it.id, it.baseProgrammes) }
        return jobs.distinctBy { (if (it is RefreshJob.Catalog) "c" else "g") + it.id }
    }

    private fun key(url: String) = url.trim().trimEnd('/').lowercase()
}

/**
 * The one summary the status pill shows while the queue runs («Обновление… 2 из 4»), instead of a ring per
 * source. [RefreshQueueWorker][tv.own.owntv.core.sync.work.RefreshQueueWorker] reports here.
 */
class RefreshQueueTracker {
    data class Progress(val current: Int, val total: Int)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    /** [current] is 1-based: the job that is running now. */
    fun update(current: Int, total: Int) {
        _progress.value = if (total <= 0) null else Progress(current.coerceIn(1, total), total)
    }

    /** The WorkManager unique name of the job the queue waits on now (so pausing can stop it too). */
    @Volatile var currentWork: String? = null

    fun finished() {
        _progress.value = null
        currentWork = null
    }
}
