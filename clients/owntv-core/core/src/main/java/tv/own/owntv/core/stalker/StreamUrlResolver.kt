package tv.own.owntv.core.stalker

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.model.SourceType

/**
 * Turns a stored item URL into a playable URL at play time. M3U/Xtream already store a
 * final URL, so they pass through unchanged. Stalker stores the portal `cmd` (not playable) — this
 * resolves it via `create_link` every call (Stalker links are short-lived, so never cache), with a
 * one-shot token re-handshake on expiry. Keep the stored `cmd` as the item's identity everywhere;
 * only the value handed to the player engine comes from here.
 *
 * **Live URL expiry:** a Stalker resolved URL dies after ~2-4h, so the playback
 * engines' auto-reconnect must NOT replay the dead URL for Stalker. Instead they call back into a
 * [ReconnectUrlProvider] (installed by `LiveViewModel` only for a Stalker source) to mint a fresh
 * URL before retrying. M3U/Xtream have no provider → the engines replay their stored URL as before.
 */
class StreamUrlResolver(
    private val stalkerAuth: StalkerAuthManager,
    private val stalkerClient: StalkerClient,
) {
    /** True when this source needs play-time resolution (so callers can skip the work entirely). */
    fun needsResolve(source: SourceEntity?): Boolean = source?.type == SourceType.STALKER

    /** Keep the selected episode attached to its season command on every playback route. */
    suspend fun resolveEpisode(source: SourceEntity?, episode: EpisodeEntity): String {
        android.util.Log.i(TAG, "episode resolve sourceId=${source?.id} episodeId=${episode.id} season=${episode.seasonNumber} episode=${episode.episodeNumber}")
        return if (source == null) episode.streamUrl else resolve(
            source, episode.streamUrl, vod = true, episode = episode.episodeNumber,
        )
    }

    /**
     * Resolve [storedUrl] to a playable URL. For Stalker this is a fresh `create_link`; for anything
     * else it returns [storedUrl] untouched. Throws on a portal/auth failure so the caller can show
     * an error instead of handing a dead URL to the engine.
     *
     * Every call is a FRESH resolve (never cached) — this is what the engines' reconnect paths call
     * ("resolve fresh"). For direct-play portals the fresh URL equals the stored one
     * (their embedded token is static); for placeholder-cmd portals it's a brand-new `create_link`.
     */
    suspend fun resolve(source: SourceEntity, storedUrl: String, vod: Boolean = false, episode: Int? = null): String {
        if (source.type != SourceType.STALKER) return storedUrl
        StalkerClient.parseVodEpisodeRef(storedUrl)?.let { return resolveVodEpisode(source, it) }
        // Many portals embed the full playable URL right in the channel cmd — play it as-is. Only a
        // placeholder cmd (localhost / no query) needs create_link (which on the former kind blanks
        // the stream id → HTTP 405). Series episodes are the exception: the season cmd is shared by
        // every episode, so it always goes through create_link to mint the episode's URL (series=<ep>).
        val direct = StalkerClient.stripCmdPrefix(storedUrl)
        // A film is the opposite case: its cmd can look like a ready URL (`http://host/2027/24/Film.mkv`,
        // a path with a dot) while the portal only plays it with a short-lived token that create_link
        // appends — handed over bare, the file server refuses it and the player just spins. So a film
        // asks the portal first and keeps the stored URL only as a fallback.
        val directLooksPlayable = StalkerClient.isDirectPlayUrl(direct)
        if (episode == null && !vod && directLooksPlayable) return direct
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) } ?: return direct
        val creds = source.stalkerCredentials(mac)
        return try {
            val minted = stalkerAuth.withAuthRetry(creds) { session ->
                stalkerClient.createLink(
                    session.apiBase, mac, session.token, creds.userAgent, storedUrl,
                    type = if (vod) "vod" else "itv", episode = episode,
                )
            }
            completeLink(minted, direct)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (vod && episode == null && directLooksPlayable) {
                android.util.Log.w(TAG, "film create_link failed (${e.javaClass.simpleName}) — playing the stored URL", e)
                direct
            } else {
                throw e
            }
        }
    }

    /**
     * Some portals answer `create_link` with only the tail of the address — `?token=…` — and leave the
     * client to put it after the file URL it already holds (the film's own cmd). Played as returned, the
     * player is handed a string with no host and sits spinning. A tail that begins with `?` replaces the
     * stored URL's query; one that begins with `/` takes its scheme and host. Anything that already
     * has a scheme is returned untouched.
     */
    internal fun completeLink(minted: String, storedDirect: String): String {
        val tail = minted.trim()
        if (tail.isEmpty() || tail.contains("://")) return minted
        val base = storedDirect.toHttpUrlOrNull() ?: return minted
        return when {
            tail.startsWith("?") -> storedDirect.substringBefore('?').substringBefore('#') + tail
            tail.startsWith("&") -> storedDirect.substringBefore('#') + (if ('?' in storedDirect) "" else "?") +
                (if ('?' in storedDirect) tail else tail.drop(1))
            tail.startsWith("/") -> "${base.scheme}://${base.host}:${base.port}$tail"
            else -> minted
        }
    }

    /**
     * An episode of a show from the film catalogue's three-level tree: list the episode's files for
     * its cmd, then `create_link` it as a film. The episode number is not sent — the file already
     * is the episode.
     */
    private suspend fun resolveVodEpisode(source: SourceEntity, ref: StalkerClient.VodEpisodeRef): String {
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) }
            ?: throw java.io.IOException("Stalker source ${source.id} has no valid MAC")
        val creds = source.stalkerCredentials(mac)
        return stalkerAuth.withAuthRetry(creds) { session ->
            val cmd = stalkerClient.getVodEpisodeFileCmd(
                session.apiBase, mac, session.token, creds.userAgent, ref.movieId, ref.seasonId, ref.episodeId,
            ) ?: throw java.io.IOException("Portal listed no file for episode ${ref.episodeId}")
            stalkerClient.createLink(session.apiBase, mac, session.token, creds.userAgent, cmd, type = "vod")
        }
    }

    /**
     * Mint a playable archive URL for a past programme on a catch-up channel.
     *
     * The archive cmd is `auto /media/<programme>_<channel>.mpg`, and **the first half is the
     * portal's own programme id**, which only the portal's guide table knows. So this asks for the
     * day's guide ([StalkerClient.getArchiveDay]), finds the row covering [startMs], and sends that
     * row's id. Like [resolve], every call is fresh — archive URLs are as short-lived as live ones.
     *
     * Until 1.0.43 the id was synthesized as `<channel>_<start unix>_<duration s>`, which no portal
     * can resolve: a Ministra facade splits the filename on `_`, looks up `(programme, channel)`,
     * finds nothing and returns an empty 200. Catch-up was dead on every such portal, silently.
     *
     * When the guide table yields nothing — a portal that does not serve it, or a day it has no rows
     * for — the legacy cmd is still tried, because that is the shape genuine Ministra accepts and it
     * costs one request to keep those portals working.
     *
     * Throws on portal/auth failure — callers show an error/no-op.
     */
    suspend fun resolveCatchup(source: SourceEntity, channelRemoteId: String, startMs: Long, endMs: Long): String {
        require(source.type == SourceType.STALKER) { "resolveCatchup is Stalker-only" }
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) }
            ?: throw java.io.IOException("Stalker source ${source.id} has no valid MAC")
        val creds = source.stalkerCredentials(mac)
        return stalkerAuth.withAuthRetry(creds) { session ->
            val programmeId = runCatching {
                archiveProgrammeId(session.apiBase, mac, session.token, creds.userAgent, channelRemoteId, startMs)
            }.onFailure {
                android.util.Log.w(TAG, "archive guide lookup failed ch=$channelRemoteId", it)
            }.getOrNull()
            val cmd = if (programmeId != null) {
                "auto /media/$programmeId.mpg"
            } else {
                val durationS = ((endMs - startMs) / 1000).coerceAtLeast(60)
                android.util.Log.w(TAG, "no archive programme id for ch=$channelRemoteId — trying the legacy cmd")
                "auto /media/${channelRemoteId}_${startMs / 1000}_$durationS.mpg"
            }
            stalkerClient.createLink(session.apiBase, mac, session.token, creds.userAgent, cmd, type = "tv_archive")
        }
    }

    /**
     * The portal's id for the programme airing at [startMs] on [channelRemoteId], or null if its guide
     * has no such row.
     *
     * The table is per calendar day and paged, and the pages run in time order, so this **binary
     * searches** them rather than reading the day from the beginning. That matters: a busy channel
     * lists eighty programmes a day over eight pages, and an evening programme sat on the last one —
     * eight round trips before playback could even be requested. Four at the very worst now, usually
     * two or three, and it no longer gets slower as the day goes on.
     *
     * The day is [startMs]'s own, in the device timezone — the one this client tells the portal it is
     * in, so the day boundary agrees with the portal's. A row covering the instant wins; failing that
     * the nearest start within [MATCH_TOLERANCE_MS] does, because our guide comes from XMLTV and the
     * portal's own listings routinely differ by a minute or two on the same programme.
     */
    private suspend fun archiveProgrammeId(
        apiBase: String, mac: String, token: String, userAgent: String?, channelRemoteId: String, startMs: Long,
    ): String? {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getDefault() }
            .format(java.util.Date(startMs))

        suspend fun pageAt(page: Int) =
            stalkerClient.getArchiveDay(apiBase, mac, token, userAgent, channelRemoteId, date, page)

        var nearest: StalkerClient.ArchiveEntry? = null
        fun consider(items: List<StalkerClient.ArchiveEntry>) {
            items.minByOrNull { kotlin.math.abs(it.startMs - startMs) }?.let { best ->
                val soFar = nearest
                if (soFar == null || kotlin.math.abs(best.startMs - startMs) < kotlin.math.abs(soFar.startMs - startMs)) {
                    nearest = best
                }
            }
        }
        fun covering(items: List<StalkerClient.ArchiveEntry>) =
            items.firstOrNull { startMs >= it.startMs && startMs < it.stopMs }

        // The first page is also the probe that reveals how many there are.
        val first = pageAt(1)
        if (first.items.isEmpty()) return null
        covering(first.items)?.let { return it.id }
        consider(first.items)

        val perPage = first.maxPageItems.takeIf { it > 0 } ?: first.items.size
        val pages = ((first.totalItems + perPage - 1) / perPage).coerceAtMost(MAX_GUIDE_PAGES)
        var low = 2
        var high = pages
        while (low <= high) {
            val mid = (low + high) / 2
            val result = pageAt(mid)
            if (result.items.isEmpty()) break
            covering(result.items)?.let { return it.id }
            consider(result.items)
            // Pages are chronological and do not overlap, so one page's bounds say which half to keep.
            when {
                startMs < result.items.first().startMs -> high = mid - 1
                startMs >= result.items.last().stopMs -> low = mid + 1
                // Inside the page's span but in a gap between two of its programmes — no other page can
                // hold it, so the nearest row is the best answer there will be.
                else -> break
            }
        }
        return nearest?.takeIf { kotlin.math.abs(it.startMs - startMs) <= MATCH_TOLERANCE_MS }?.id
    }

    private companion object {
        const val TAG = "StreamUrlResolver"

        /** A day of guide is a handful of pages; the cap only stops a portal that miscounts its totals. */
        const val MAX_GUIDE_PAGES = 12

        /** Days of one channel read at the same time. */
        const val DAY_PARALLEL = 4

        /** How far a portal listing may sit from our XMLTV start and still be the same programme. */
        const val MATCH_TOLERANCE_MS = 15 * 60 * 1000L
    }


    /**
     * The portal's own schedule of one channel for the calendar days [dayOffsets] (0 = today, -1 =
     * yesterday, 1 = tomorrow), read from the per-day guide table ([StalkerClient.getArchiveDay]).
     *
     * The bulk `get_epg_info` only carries a few hours, while the portal keeps whole days (operators
     * see them from the first of the month), so this is where the past days and the rest of today
     * come from. Days are read at most [DAY_PARALLEL] at a time; a day that fails is left out rather
     * than failing the rest. Sorted by start, one row per start time.
     */
    suspend fun scheduleDays(
        source: SourceEntity, channelRemoteId: String, dayOffsets: List<Int>,
    ): List<StalkerClient.ArchiveEntry> {
        require(source.type == SourceType.STALKER) { "scheduleDays is Stalker-only" }
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) }
            ?: throw java.io.IOException("Stalker source ${source.id} has no valid MAC")
        val creds = source.stalkerCredentials(mac)
        val now = System.currentTimeMillis()
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return stalkerAuth.withAuthRetry(creds) { session ->
            val gate = kotlinx.coroutines.sync.Semaphore(DAY_PARALLEL)
            kotlinx.coroutines.coroutineScope {
                dayOffsets.map { offset ->
                    async {
                        gate.withPermit {
                            val date = today.plusDays(offset.toLong()).toString()
                            try {
                                readWholeDay(session.apiBase, mac, session.token, creds.userAgent, channelRemoteId, date)
                            } catch (c: kotlinx.coroutines.CancellationException) {
                                throw c
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }
                }.awaitAll().flatten()
            }.distinctBy { it.startMs }.sortedBy { it.startMs }
        }
    }

    private suspend fun readWholeDay(
        apiBase: String, mac: String, token: String, userAgent: String?, channelRemoteId: String, date: String,
    ): List<StalkerClient.ArchiveEntry> {
        val first = stalkerClient.getArchiveDay(apiBase, mac, token, userAgent, channelRemoteId, date, 1)
        val out = ArrayList(first.items)
        val perPage = first.maxPageItems.takeIf { it > 0 } ?: first.items.size
        if (perPage <= 0) return out
        val pages = ((first.totalItems + perPage - 1) / perPage).coerceAtMost(MAX_GUIDE_PAGES)
        for (page in 2..pages) {
            val next = stalkerClient.getArchiveDay(apiBase, mac, token, userAgent, channelRemoteId, date, page)
            if (next.items.isEmpty()) break
            out += next.items
        }
        return out
    }

    /**
     * The channel's now/next programmes via the portal's per-channel `get_short_epg`
     * (the Stalker counterpart of Xtream's short-EPG fallback in `LiveViewModel.loadEpg`). Throws on
     * portal/auth failure — the caller treats that as "no guide data".
     */
    suspend fun shortEpg(source: SourceEntity, channelRemoteId: String, size: Int = 8): List<StalkerClient.ShortEpgEntry> {
        require(source.type == SourceType.STALKER) { "shortEpg is Stalker-only" }
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) }
            ?: throw java.io.IOException("Stalker source ${source.id} has no valid MAC")
        val creds = source.stalkerCredentials(mac)
        return stalkerAuth.withAuthRetry(creds) { session ->
            stalkerClient.getShortEpg(session.apiBase, mac, session.token, creds.userAgent, channelRemoteId, size)
        }
    }
}

/**
 * Installed on a playback engine when the current item's URL can't simply be replayed on reconnect
 * (Stalker — live, and VOD, whose `create_link` URL expires in a couple of hours,
 * comfortably inside a film). Before retrying, the engine awaits [freshUrl]; a null return means
 * "replay the stored URL as-is" (also the default when no provider is set — i.e. M3U/Xtream, whose
 * URLs are stable forever).
 *
 * Engines call this on their MAIN scope (ExoPlayer is single-threaded; mpv dispatches to main) and
 * treat the returned value as the URL to (re)load. A throw means resolution failed — the engine
 * surfaces its normal reconnect-exhausted / error state.
 */
fun interface ReconnectUrlProvider {
    /** Return a fresh playable URL, or null to replay the current (non-expiring) URL as-is. */
    suspend fun freshUrl(): String?
}
