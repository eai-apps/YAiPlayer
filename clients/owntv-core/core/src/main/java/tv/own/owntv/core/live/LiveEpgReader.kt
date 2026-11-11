package tv.own.owntv.core.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.CorePerf
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.computeContentHash
import tv.own.owntv.core.epg.ArchiveGuidePlan
import tv.own.owntv.core.epg.EpgShift
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.stalker.StreamUrlResolver

/** Archive window assumed when the playlist doesn't say how many days it keeps. One shared value:
 *  the catch-up picker used to assume 1 day while live rewind assumed 7, so the same channel
 *  offered a day of programmes in the list and a week of rewinding on the timeline. */
const val DEFAULT_CATCHUP_DAYS = 7

/** Upper bound on a playlist's `catchup-days`. Guards the arithmetic that turns it into seconds. */
const val MAX_CATCHUP_DAYS = 31

// How far back the Live TV catch-up picker may look. Must stay within EpgRepository's retention (7
// days) and matches the Guide's own cap, so the same channel offers the same archive from either
// screen — this used to be 48h, left over from when only ~2 days of guide were retained, which meant
// a programme the Guide happily replayed was not even listed in Live TV.
private const val CATCHUP_LOOKBACK_CAP_MS = 14L * 24 * 60 * 60 * 1000

// How far past a finished catch-up programme to look for the next one. Beyond this the guide has a
// gap rather than a next programme, and auto-play stops instead of leaping hours ahead.
private const val NEXT_PROGRAMME_GAP_CAP_MS = 3L * 60 * 60 * 1000

/**
 * How long a resolved now/next stays usable. Long enough that scrolling back to a channel costs
 * nothing, short enough that a programme changing over is noticed.
 */
private const val CACHE_TTL_MS = 5 * 60_000L

/** Backoff for channels whose provider returned no short-EPG data. */
private const val EMPTY_PROVIDER_TTL_MS = 10 * 60_000L

/** How many entries the provider's short-EPG endpoint is asked for. Its own practical maximum. */
private const val SHORT_EPG_LIMIT = 8

/** A Stalker channel's day tables are read again after this long, sooner after a failure. */
private const val PORTAL_DAYS_REFETCH_MS = 3L * 60 * 60 * 1000
private const val PORTAL_DAYS_RETRY_MS = 10L * 60 * 1000
private const val PORTAL_DAYS_TIMEOUT_MS = 30_000L

/** How many past days of a portal channel's guide table are read: the longest archive portals keep. */
private const val PORTAL_PAST_DAYS = 7

/**
 * How many stored rows to read for Now/Next/Later. Well over the six those show, because a second
 * feed carrying the channel interleaves its own rows, and the lower-priority ones are dropped
 * afterwards ([tv.own.owntv.core.epg.GuidePriority]) and would otherwise eat the list.
 */
private const val UPCOMING_LIMIT = 120
private const val UPCOMING_HORIZON_MS = 24L * 60 * 60 * 1000

/**
 * Every guide read Live TV performs: now/next for the focused channel, the batched "what's on now" for a
 * list of channels, the catch-up programme list, and the "Match EPG" picker.
 *
 * Split out of the TV app's Live view model, which kept none of this state beyond the now/next cache this class now
 * owns. Nothing here touches playback, the engine ladder or the tune state machine — it reads the
 * database (falling back to the provider's short-EPG API) and maps rows. The guide shift is passed in
 * rather than observed, so the caller stays the single place that knows a customization changed.
 */
class LiveEpgReader(
    private val epgDao: EpgDao,
    private val epgSourceStore: EpgSourceStore,
    private val sourceDao: SourceDao,
    private val xtreamClient: XtreamClient,
    private val streamUrlResolver: StreamUrlResolver,
) {
    private data class CachedEpg(val at: Long, val data: EpgNowNext)

    /** Written from the IO dispatcher (the now/next fetch) and cleared from Main (EPG mapping/shift
     *  changes), so it cannot be a plain HashMap — concurrent structural modification of one is
     *  undefined, up to a corrupt table or an infinite loop inside `get`. */
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, CachedEpg>()

    private data class CachedRows(val at: Long, val rows: List<EpgProgrammeEntity>)

    /** Provider-fetched guide rows per channel, so the grid and the preview share one request. */
    private val providerRows = java.util.concurrent.ConcurrentHashMap<Long, CachedRows>()

    /** Channels where provider short-EPG returned nothing: backoff until timestamp to avoid repeating requests. */
    private val emptyProviderEpgUntil = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    /** Clear negative caching of channels with no provider EPG so visible channels can retry. */
    fun clearEmptyProviderCache() {
        emptyProviderEpgUntil.clear()
    }

    /** True if [channelId] was recently probed from provider and yielded no programme (backoff 10 min). */
    fun isKnownEmptyProvider(channelId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val until = emptyProviderEpgUntil[channelId] ?: return false
        if (now < until) return true
        emptyProviderEpgUntil.remove(channelId)
        return false
    }

    /** Record that [channelId] has no programme at the provider for [ttlMs] (defaults to 10 min). */
    fun markEmptyProvider(channelId: Long, now: Long = System.currentTimeMillis(), ttlMs: Long = EMPTY_PROVIDER_TTL_MS) {
        emptyProviderEpgUntil[channelId] = now + ttlMs
    }

    /**
     * Prefetch a single visible channel's current programme via [nowNext] if not already cached.
     * Caches empty results for 10 minutes to avoid repeatedly hammering channels without short EPG.
     */
    suspend fun prefetchVisibleProgramme(
        channel: ChannelEntity,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): XtEpgEntry? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (isKnownEmptyProvider(channel.id, now)) return@withContext null
        cachedNow(channel.id, now)?.let { return@withContext it }

        val res = nowNext(channel, cust, globalShiftMinutes)
        val nowEntry = res?.now?.takeIf { it.title.isNotBlank() }
        if (nowEntry != null) {
            nowEntry
        } else {
            markEmptyProvider(channel.id, now)
            null
        }
    }

    /** Drop every cached now/next — the global guide offset moved, so all of them are stale. */
    fun clearCache() {
        cache.clear()
        emptyProviderEpgUntil.clear()
    }

    /** Drop one channel's cached now/next (its EPG match or per-channel shift changed). */
    fun invalidate(channelId: Long) {
        cache.remove(channelId)
        emptyProviderEpgUntil.remove(channelId)
    }

    /** Now/next for [ch], cached ~5 min. Prefers a manual EPG match / stored bulk guide, then falls
     *  back to Xtream's short EPG API. */
    suspend fun nowNext(ch: ChannelEntity, cust: SectionCustomizations, globalShiftMinutes: Int): EpgNowNext? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            cache[ch.id]?.takeIf { now - it.at < CACHE_TTL_MS }?.let { return@withContext it.data }

            // 1) Bulk guide via the effective EPG id (manual match overrides the channel's own id).
            val epgKey = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            // Guide shift (global or per-channel): the stored rows keep the feed's own clock, so we look
            // up the SHIFTED "now" and move what comes back — display and catch-up then agree.
            val shift = EpgShift.minutesFor(cust, ch, globalShiftMinutes)
            if (epgKey != null) {
                val at = EpgShift.toStored(now, shift)
                // Resolved first: two feeds covering one channel list every programme twice, which put
                // the same title in Next AND at the head of Later, and Now came from whichever feed's
                // block started last. Now is read from the same resolved rows, so it is the
                // higher-priority feed's programme too.
                val horizon = at + UPCOMING_HORIZON_MS
                var rows = epgSourceStore.priority().resolve(epgDao.programmesForChannel(epgKey, at, horizon))
                if ((rows.maxOfOrNull { it.stopMs } ?: at) < horizon && sourceDao.getById(ch.sourceId)?.type == SourceType.STALKER) {
                    val supplement = providerEntries(ch, shift).orEmpty().map {
                        EpgProgrammeEntity(id = -it.startMs, sourceId = ch.sourceId, epgChannelId = epgKey,
                            startMs = EpgShift.toStored(it.startMs, shift), stopMs = EpgShift.toStored(it.stopMs, shift),
                            title = it.title, description = it.description)
                    }
                    rows = previewGuideWindow(rows, supplement, at, horizon)
                }
                val nowProg = rows.onAirAt(at)
                val future = rows.filter { it.startMs > (nowProg?.startMs ?: 0) && it.startMs < horizon }
                val nextProg = future.firstOrNull()
                if (nowProg != null || nextProg != null) {
                    val prevProg = epgDao.previousProgramme(epgKey, nowProg?.startMs ?: at)
                    // Days of stored guide — accurate for bulk-guide channels (the short-EPG API path
                    // leaves this null, since its ~8 entries only span a few hours).
                    val days = runCatching { epgDao.coverageDays(epgKey) }.getOrNull()?.takeIf { it > 0 }
                    fun EpgProgrammeEntity.shifted() = EpgShift.apply(this, shift).toXt()
                    val result = EpgNowNext(nowProg?.shifted(), nextProg?.shifted(), future.drop(1).map { it.shifted() }, previous = prevProg?.shifted(), coverageDays = days, nowDetails = nowProg?.details)
                    cache[ch.id] = CachedEpg(now, result)
                    return@withContext result
                }
            }

            // 2) Provider short-EPG API fallback (Xtream get_short_epg / Stalker get_short_epg).
            val entries = providerEntries(ch, shift) ?: return@withContext null
            // A gap in the provider's own guide data around "now" (nothing covers this instant) must leave
            // current null — picking the next entry that simply hasn't ended yet would mislabel an upcoming
            // programme as live. "Next"/"Later" are computed independently below, so a genuine
            // gap correctly shows no "Now" while the upcoming programme still appears as "Next".
            val current = entries.firstOrNull { it.startMs <= now && it.stopMs > now }
            val future = entries.filter { it.startMs > (current?.startMs ?: now) && it.startMs < now + UPCOMING_HORIZON_MS }.sortedBy { it.startMs }
            // Short-EPG responses sometimes include the just-finished programme — surface it as "Before".
            val previous = entries.filter { it.stopMs <= (current?.startMs ?: now) }.maxByOrNull { it.stopMs }
            val result = EpgNowNext(current, future.firstOrNull(), future.drop(1), previous = previous)
            cache[ch.id] = CachedEpg(now, result)
            result
        }

    /**
     * The channel's guide as the **provider** reports it right now, on the user's clock.
     *
     * This is the second of the two guides OwnTV reads, and the reason the preview pane could show a
     * programme while the grid and the channel row beside it stayed blank: these entries are fetched
     * live and are never written to `epg_programmes`, so anything reading only the table sees nothing.
     * Pulled out of [nowNext] so the guide grid and the channel rows can read exactly the same answer.
     *
     * Null — as distinct from empty — means there is no provider guide to ask for at all: no stream
     * id, no source, or a source type with no short-EPG endpoint. Callers use that to tell "the
     * provider says nothing is on" from "there was nobody to ask".
     *
     * **It is only ever a few hours.** `get_short_epg` returns about eight entries, so this fills now
     * and the rest of the evening, never a whole scrollable day.
     */
    private suspend fun providerEntries(ch: ChannelEntity, shift: Int): List<XtEpgEntry>? {
        val streamId = ch.remoteId ?: return null
        val source = sourceDao.getById(ch.sourceId) ?: return null
        val raw = when (source.type) {
            SourceType.XTREAM -> providerOrEmpty { xtreamClient.getShortEpg(source, streamId, limit = SHORT_EPG_LIMIT) }
            SourceType.STALKER -> providerOrEmpty {
                streamUrlResolver.scheduleDays(source, streamId, listOf(0, 1))
                    .filter { it.title.isNotBlank() && it.stopMs > it.startMs }
                    .map { XtEpgEntry(title = it.title, description = it.description, startMs = it.startMs, stopMs = it.stopMs) }
            }.ifEmpty { providerOrEmpty {
                streamUrlResolver.shortEpg(source, streamId)
                    .map { XtEpgEntry(title = it.title, description = it.description, startMs = it.startMs, stopMs = it.stopMs) }
            } }
            else -> return null
        }
        // The provider's own guide needs the same shift as the stored one — same channel, same clock.
        return if (shift == 0) raw else raw.map {
            it.copy(startMs = it.startMs + shift * 60_000L, stopMs = it.stopMs + shift * 60_000L)
        }
    }

    private suspend fun <T> providerOrEmpty(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * The provider's guide for [channel] as guide rows, ready to draw — the grid's half of the same
     * fallback the preview pane has always had.
     *
     * The rows are **synthetic** — they exist nowhere in the database — so anything that looks a
     * programme up by id must fall back to the row it already holds, which is why the description
     * travels with it rather than being fetched later.
     *
     * Their ids are the **negated start time**: negative, so they can never collide with a real row's
     * (Room generates those upwards from 1), and distinct within a channel, so a caller that gathers
     * ids into a set — the grid marks its catch-up cells that way — cannot have one synthetic row
     * stand for all of them.
     *
     * Cached per channel for [CACHE_TTL_MS], so a row scrolling in and out of view, and the list's
     * own periodic refresh, cost one request rather than one per look.
     */
    suspend fun providerProgrammes(
        channel: ChannelEntity,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): List<EpgProgrammeEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        providerRows[channel.id]?.takeIf { now - it.at < CACHE_TTL_MS }?.let { return@withContext it.rows }
        val shift = EpgShift.minutesFor(cust, channel, globalShiftMinutes)
        val key = (cust.epgMatchResolver.epgIdFor(channel) ?: channel.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: channel.streamUrl
        val rows = providerEntries(channel, shift).orEmpty().map {
            EpgProgrammeEntity(
                id = -it.startMs,
                sourceId = channel.sourceId,
                epgChannelId = key,
                startMs = it.startMs,
                stopMs = it.stopMs,
                title = it.title,
                description = it.description,
            )
        }.sortedBy { it.startMs }
        providerRows[channel.id] = CachedRows(now, rows)
        rows
    }

    /**
     * The programme currently airing on each of [channels] (channel id → title), looked up in ONE batch
     * against the stored bulk guide — same query the Home "On Now" rail uses. This powers the small
     * "current programme" subtitle under each channel row in the Live list and the in-player channel
     * overlay. Returns only channels that actually have something airing right now.
     *
     * **Two passes, because there are two guides.** The stored bulk guide answers first, in one query,
     * and covers most channels for the cost of a single round trip. Whatever it cannot answer then goes
     * through [nowNext] — *the very function that fills the preview pane* — which checks its own cache,
     * then the stored guide, then falls back to the provider's short-EPG API.
     *
     * That second pass is the whole point. Those short-EPG programmes are never written to the table, so
     * a channel served that way showed a full guide in the preview pane and a blank second line in the
     * list right beside it, permanently — the two were reading different guides. Now there is one answer
     * for one channel, and the row and the pane agree by construction rather than by coincidence: if the
     * preview has a programme the row shows it, and if the preview has none the row is blank too.
     *
     * **Nothing here goes to the network.** That is a hard rule, not an optimisation: see the note at
     * the second pass for what filling a list of hundreds by asking the provider actually did.
     */
    suspend fun nowPlayingFor(
        channels: List<ChannelEntity>,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): Map<Long, String> = nowProgrammesFor(channels, cust, globalShiftMinutes).mapValues { it.value.title }

    /**
     * [nowPlayingFor] with the programme's start and stop as well, in display time (the channel's
     * guide offset applied), for the rows that show "1 h 23 min left" and a progress bar. Same two
     * passes, same no-network rule; the description is always null.
     */
    suspend fun nowProgrammesFor(
        channels: List<ChannelEntity>,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): Map<Long, XtEpgEntry> = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext emptyMap()
        val now = System.currentTimeMillis()
        // Every playlist plus every EPG feed — NOT just the sources the visible page happens to come
        // from. Guide rows are keyed by epgChannelId and routinely live under a different source than
        // the channel showing them (one playlist's xmltv.php covering another's channels), and
        // [nowNext] — which fills the preview pane — applies no source filter at all. Narrowing this to
        // the page's own sources made the two disagree: the preview showed a programme while the row
        // under it stayed blank, until an EPG re-sync happened to rewrite the rows under an id the
        // filter included.
        val epgIds = epgSourceStore.getAll().map { it.id }
        val sourceIds = (sourceDao.allSourceIds() + epgIds).distinct()
        // Manual EPG-match overrides take precedence over the channel's own epgChannelId, mirroring nowNext.
        // Shifted channels look up a different instant, so the batch is grouped by shift — with no
        // offsets configured (the normal case) that's still exactly one query group.
        val channelKeys = channels.mapNotNull { ch ->
            val key = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)
                ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (key != null) Triple(ch.id, key, EpgShift.minutesFor(cust, ch, globalShiftMinutes)) else null
        }
        if (channelKeys.isEmpty()) return@withContext emptyMap()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        CorePerf.log { "perf_now_programmes_for_start channels=${channels.size} at=$startedAt" }
        var chunks = 0
        val result = HashMap<Long, XtEpgEntry>()
        val priority = epgSourceStore.priority()
        for ((shift, group) in channelKeys.groupBy { it.third }) {
            val delta = shift * 60_000L
            val at = EpgShift.toStored(now, shift)
            val rowsByKey = group
                .map { it.second }.distinct()
                .chunked(400)
                .flatMap { keys ->
                    chunks++
                    epgDao.programmeSummariesForChannels(keys, at, at + 1)
                }
                .groupBy { it.epgChannelId }
            for ((channelId, epgKey, _) in group) {
                rowsByKey[epgKey]
                    ?.let { priority.resolve(it) }
                    ?.firstOrNull { at in it.startMs until it.stopMs }
                    ?.let { result[channelId] = XtEpgEntry(it.title, null, it.startMs + delta, it.stopMs + delta) }
            }
        }
        // Second pass: whatever the preview pane has already resolved for a channel the stored guide
        // could not answer — **from cache, never from the network**.
        //
        // Fetching here was tried and was a mistake worth recording. A list holds hundreds of
        // channels, so "ask the provider for the ones we cannot answer" became hundreds of
        // `get_short_epg` requests, most of them returning nothing because that provider has no short
        // guide for those channels at all. Worse, the batch could not return until the last one
        // finished, so the list that used to fill from one query instantly now filled from nothing:
        // EVERY row went blank, which is the opposite of the bug it set out to fix.
        //
        // The provider is still asked — just never in bulk. The channel under the cursor is fetched
        // by the preview pane itself, and a guide row fetches as it scrolls into view. Both land in
        // this cache, so the list fills in behind them for free.
        val fromStored = result.size
        for (ch in channels) {
            if (ch.id in result) continue
            cachedNow(ch.id, now)?.let { result[ch.id] = it }
        }
        CorePerf.log {
            "live_nowplaying channels=${channels.size} keyed=${channelKeys.size} " +
                "keys=${channelKeys.map { it.second }.distinct().size} chunks=$chunks " +
                "fromStored=$fromStored fromCache=${result.size - fromStored} " +
                "totalMs=${android.os.SystemClock.elapsedRealtime() - startedAt}"
        }
        CorePerf.log { "perf_now_programmes_for_end channels=${channels.size} durationMs=${android.os.SystemClock.elapsedRealtime() - startedAt} at=${android.os.SystemClock.elapsedRealtime()}" }
        result
    }

    /**
     * The current programme already resolved for [channelId], if it is still fresh — from either
     * cache, because the preview pane and the guide grid fill different ones.
     *
     * Deliberately never fetches. See the note in [nowPlayingFor] for what happened when it did.
     */
    private fun cachedNow(channelId: Long, now: Long): XtEpgEntry? {
        cache[channelId]?.takeIf { now - it.at < CACHE_TTL_MS }?.data?.now
            ?.takeIf { it.title.isNotBlank() }?.let { return it.copy(description = null) }
        return providerRows[channelId]
            ?.takeIf { now - it.at < CACHE_TTL_MS }
            ?.rows?.firstOrNull { it.startMs <= now && it.stopMs > now }
            ?.takeIf { it.title.isNotBlank() }
            ?.let { XtEpgEntry(it.title, null, it.startMs, it.stopMs) }
    }

    /**
     * What was on [ch] at [atMs], and what followed it — the archive counterpart of [nowNext], for the
     * "PLAYING / THEN" card shown while a recording is playing.
     *
     * Stored-guide only, deliberately: the provider short-EPG APIs report *now and upcoming*, so they
     * can say nothing about yesterday afternoon. A channel whose guide comes only from that API gets
     * no archive card, which is correct — there is genuinely no data for the moment being watched.
     *
     * Returns null when nothing is known at [atMs], so the caller can simply omit the card.
     */
    suspend fun nowNextAt(
        ch: ChannelEntity,
        atMs: Long,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): EpgNowNext? = withContext(Dispatchers.IO) {
        val epgKey = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        // Same shift dance as [nowNext]: stored rows keep the feed's own clock, so look up the shifted
        // instant and move what comes back, or the card would disagree with the archive it describes.
        val shift = EpgShift.minutesFor(cust, ch, globalShiftMinutes)
        val at = EpgShift.toStored(atMs, shift)
        val rows = epgSourceStore.priority().resolve(epgDao.upcoming(epgKey, at, UPCOMING_LIMIT).first())
        val playing = rows.onAirAt(at)
        val then = rows.firstOrNull { it.startMs > (playing?.startMs ?: 0) }
        if (playing == null && then == null) return@withContext null
        fun EpgProgrammeEntity.shifted() = EpgShift.apply(this, shift).toXt()
        EpgNowNext(playing?.shifted(), then?.shifted())
    }


    /** When each Stalker channel's day tables were last read — memory only, see [ensurePortalDays]. */
    private val portalDayTries = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    /**
     * Fill a Stalker channel's guide with the whole days the portal keeps: the archive's past days and
     * today with tomorrow. The portal's bulk guide carries only a few hours per channel, so without
     * this the catch-up list ends hours back. One read per channel every few hours, kept under a store
     * of its own ([ArchiveGuidePlan.storeId]) and written only where the playlist's own guide rows are
     * not, so the two never list a programme twice. A failure leaves the stored guide as it was.
     */
    suspend fun ensurePortalDays(ch: ChannelEntity, epgKey: String): Boolean {
        val remoteId = ch.remoteId?.takeIf { it.isNotBlank() } ?: return false
        if (sourceDao.getById(ch.sourceId)?.type != SourceType.STALKER) return false
        val now = System.currentTimeMillis()
        synchronized(portalDayTries) {
            val last = portalDayTries[ch.id]
            if (last != null && now - last < PORTAL_DAYS_REFETCH_MS) return true
            portalDayTries[ch.id] = now
        }
        try {
            val source = sourceDao.getById(ch.sourceId)?.takeIf { it.type == SourceType.STALKER } ?: return true
            // Whatever the portal keeps, up to a week: its own "archive days" figure is often smaller than
            // the days its guide table still lists, and the list should show every one of them.
            val past = if (ch.catchup) PORTAL_PAST_DAYS else 0
            val listed = kotlinx.coroutines.withTimeoutOrNull(PORTAL_DAYS_TIMEOUT_MS) {
                streamUrlResolver.scheduleDays(source, remoteId, (-past..1).toList())
            } ?: run { portalDayTries[ch.id] = now - PORTAL_DAYS_REFETCH_MS + PORTAL_DAYS_RETRY_MS; return true }
            val rows = listed.filter { it.title.isNotBlank() && it.stopMs > it.startMs }
            if (rows.isEmpty()) return true
            val store = ArchiveGuidePlan.storeId(ch.sourceId)
            val existing = epgDao.programmeSummariesForChannel(epgKey, Long.MIN_VALUE / 2, Long.MAX_VALUE)
            val others = existing.filter { it.sourceId != store }
            val lo = others.minOfOrNull { it.startMs }
            val hi = others.maxOfOrNull { it.stopMs }
            val keep = rows.filter { lo == null || hi == null || it.stopMs <= lo || it.startMs >= hi }
            epgDao.clearChannelForSources(epgKey, listOf(store))
            if (keep.isNotEmpty()) {
                epgDao.upsertProgrammes(keep.map {
                    EpgProgrammeEntity(
                        sourceId = store, epgChannelId = epgKey, startMs = it.startMs, stopMs = it.stopMs,
                        title = it.title, description = it.description,
                    ).let { p -> p.copy(contentHash = p.computeContentHash()) }
                })
            }
            tv.own.owntv.core.util.GuideTrace.add("portal days: ${keep.size} programmes added for one channel")
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            portalDayTries[ch.id] = now - PORTAL_DAYS_REFETCH_MS + PORTAL_DAYS_RETRY_MS
            tv.own.owntv.core.util.GuideTrace.add("portal days: failed (${e.javaClass.simpleName})")
        }
        return true
    }

    /** Recent (already-aired) programmes for a catch-up channel, newest first — drives the Live TV
     *  catch-up picker. Bounded to the EPG we retain (7 days) and the channel's archive window. */
    suspend fun catchupProgrammes(
        ch: ChannelEntity,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
        liveSourceIds: List<Long>,
    ): List<EpgProgrammeEntity> = withContext(Dispatchers.IO) {
        if (!ch.catchup) return@withContext emptyList()
        val epgKey = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
        val portal = ensurePortalDays(ch, epgKey)
        val now = System.currentTimeMillis()
        val days = if (portal) PORTAL_PAST_DAYS else ch.catchupDays.takeIf { it > 0 } ?: DEFAULT_CATCHUP_DAYS
        val windowMs = (days * 24L * 60 * 60 * 1000).coerceAtMost(CATCHUP_LOOKBACK_CAP_MS)
        val ids = liveSourceIds + epgSourceStore.getAll().map { it.id }
        // Shifted guide → shifted window, and the rows come back on the corrected clock, so the
        // archive URL built from the picked programme asks for the time it really aired.
        val shift = EpgShift.minutesFor(cust, ch, globalShiftMinutes)
        val at = EpgShift.toStored(now, shift)
        epgSourceStore.priority().resolve(epgDao.programmesForChannel(epgKey, at - windowMs, at + 60 * 60 * 1000))
            .filter { it.startMs <= at }           // already started → catch-up applies
            .sortedByDescending { it.startMs }      // most recent first
            .take(900)
            .map { EpgShift.apply(it, shift) }
    }

    /**
     * The guide entry that follows [afterStopMs] on [ch] — what a finished catch-up programme rolls
     * into. Null when the guide stops there, or when the gap to the next entry is longer than
     * [NEXT_PROGRAMME_GAP_CAP_MS]: a channel whose guide resumes tomorrow morning has nothing to
     * continue with tonight, and jumping hours forward is not "the next programme".
     *
     * [afterStopMs] is on the clock the user sees; the result comes back on it too, so the archive URL
     * built from it asks for the time the programme really aired.
     */
    suspend fun programmeAfter(
        ch: ChannelEntity,
        afterStopMs: Long,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
        liveSourceIds: List<Long>,
    ): EpgProgrammeEntity? = withContext(Dispatchers.IO) {
        val epgKey = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        val shift = EpgShift.minutesFor(cust, ch, globalShiftMinutes)
        val from = EpgShift.toStored(afterStopMs, shift)
        val ids = liveSourceIds + epgSourceStore.getAll().map { it.id }
        epgSourceStore.priority().resolve(epgDao.programmesForChannel(epgKey, from, from + NEXT_PROGRAMME_GAP_CAP_MS))
            // The query keeps anything still running at [from]; the next programme is the one that
            // starts at or after it.
            .firstOrNull { it.startMs >= from }
            ?.let { EpgShift.apply(it, shift) }
    }

    /** Full description for a programme picked in the catch-up dialog. The list query drops it to stay
     *  under the CursorWindow limit, so the detail popup fetches it on demand (same as the Guide). */
    suspend fun programmeDescription(programmeId: Long): String? =
        withContext(Dispatchers.IO) { runCatching { epgDao.programmeDescription(programmeId) }.getOrNull() }

    // The "Match EPG" picker used to be answered from here, filtered to the profile's own sources.
    // It is now [tv.own.owntv.core.epg.GuideCandidates], which filters by no source at all — the same
    // rule every guide read already followed, and the half of it that was missing.

    /** The programme airing at [at] in start-ordered rows: the latest to have started, if two overlap. */
    private fun List<EpgProgrammeEntity>.onAirAt(at: Long): EpgProgrammeEntity? =
        lastOrNull { it.startMs <= at && it.stopMs > at }

    private fun EpgProgrammeEntity.toXt() =
        XtEpgEntry(title = title, description = description, startMs = startMs, stopMs = stopMs)
}
