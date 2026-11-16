// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.epg

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.own.owntv.core.CorePerf
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.matchName
import tv.own.owntv.core.live.epgKeyOf
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XmltvParser
import tv.own.owntv.core.repository.EpgRepository

/** One ordered pass, shared by TV and phone. No persisted manual match is replaced. */
class EpgGapFill(
    private val db: OwnTVDatabase,
    private val store: EpgSourceStore,
    private val repository: EpgRepository,
    private val customize: CustomizationStore,
) {
    private data class Missing(val profile: Long, val channel: ChannelEntity, val key: String?, val from: Long = 0, val to: Long = 0)

    suspend fun run(stalkerOnly: Boolean = false, onlyFeed: Long? = null): Int = passLock.withLock {
        withContext(Dispatchers.IO) {
            if (!store.builtinEnabled.first()) return@withContext 0
            purgeMixedReserveOnce()
            val started = SystemClock.elapsedRealtime()
            // Only the profile in use (owner 12.11): a profile nobody opened is not filled, and a profile with no
            // playlist or portal downloads nothing at all — it used to fetch every feed for the other profiles'
            // channels, which on a phone looked like an endless load in the notification shade.
            val settings = org.koin.core.context.GlobalContext.get().get<tv.own.owntv.core.settings.SettingsRepository>()
            val active = db.profileDao().resolveExistingProfileId(settings.activeProfileId.first())
                ?: return@withContext 0
            val own = db.sourceDao().sourceIdsForProfile(active).toSet()
            // Owner 12.11: the reserve guide never runs over a working guide. A portal takes it only when
            // "Additional guide for the portal" is on; a playlist only when its own guide (url-tvg) is missing
            // or failed to load.
            val portalExtra = settings.portalBuiltinEpgSources.first()
            val userFeeds = store.getAll().filterNot { it.builtin }
            val nowMs = System.currentTimeMillis()
            // A playlist's own guide is the user feed registered from its url-tvg (or Xtream API); "missing"
            // = it offers none, or none of its feeds stored anything from now on.
            suspend fun ownGuideMissing(src: tv.own.owntv.core.database.entity.SourceEntity): Boolean {
                val urls = repository.guideUrls(src).toSet()
                if (urls.isEmpty()) return true
                return userFeeds.filter { it.url in urls }
                    .none { feed -> runCatching { db.epgDao().hasProgrammesAfter(feed.id, nowMs) }.getOrDefault(false) }
            }
            val playlists = db.sourceDao().getAllOnce()
                .filter { it.id in own && (!stalkerOnly || it.type == SourceType.STALKER) }
                .filter { src ->
                    when (src.type) {
                        SourceType.STALKER -> src.id in portalExtra
                        // Asked for by name («Load from source N», owner 13.11): fill whatever channels still
                        // lack a programme, even beside a working playlist guide — the offer counted exactly those.
                        else -> onlyFeed != null || ownGuideMissing(src)
                    }
                }
            if (playlists.isEmpty()) return@withContext 0
            val profileIds = listOf(active)
            // Playlist URLs are registered before the pass is enqueued. Preserve the user's order.
            if (!stalkerOnly) {
                // Only a stale one: EpgSyncWorker has usually just downloaded it and then queued this
                // pass, and fetching every user guide twice per refresh was too much for weak boxes.
                val now = System.currentTimeMillis()
                for (source in store.getAll().filterNot { it.builtin }) {
                    if (source.lastError == null && (source.lastSyncAt ?: 0L) > now - USER_GUIDE_FRESH_MS) continue
                    // A guide that failed waits for the weekly schedule or a manual refresh (owner 12.11: no
                    // download outside them); only one never fetched is fetched here.
                    if (source.lastSyncAt != null || source.lastError != null) continue
                    attempt(source) { repository.refreshUrl(source.id, source.url, source.userAgent) }
                }
            }
            val channels = db.channelDao().allForSources(playlists.map { it.id }, Int.MAX_VALUE)
            suspend fun missing(): List<Missing> {
                val result = ArrayList<Missing>()
                for (pid in profileIds) {
                    val cust = customize.observe(pid, MediaType.LIVE).first()
                    val keyed = channels.filter { CustomizeKeys.channel(it) !in cust.hiddenItems }
                        .map { Missing(pid, it, epgKeyOf(it, cust)) }
                    val globalShift = org.koin.core.context.GlobalContext.get()
                        .get<tv.own.owntv.core.settings.SettingsRepository>().epgOffsetMinutes.first()
                    for ((shift, group) in keyed.groupBy { EpgShift.minutesFor(cust, it.channel, globalShift) }) {
                        val now = EpgShift.toStored(System.currentTimeMillis(), shift)
                        val have = HashSet<String>()
                        group.mapNotNull { it.key }.distinct().chunked(500).forEach {
                            have.addAll(db.epgDao().keysWithGuide(it, now, now + 86_400_000L))
                        }
                        result.addAll(group.filter { EpgGapPlan.needsGuide(it.key, have) }
                            .map { it.copy(from = now, to = now + 86_400_000L) })
                    }
                }
                return result
            }
            var filled = 0
            // Feed 1 (light) always; feeds 2 and 3 only when switched on, and only for playlists — a portal
            // channel is never filled from them (owner 12.11).
            val extraOn = store.extraReserveOn.first()
            val builtins = store.getAll().filter { it.builtin }
            val firstBuiltin = builtins.firstOrNull()?.id
            val feeds = builtins.filter { it.id == firstBuiltin || it.url in extraOn }
                .filter { onlyFeed == null || it.id == onlyFeed }
            val attempted = HashSet<Long>()
            EpgGapPlan.visitFeeds(feeds.distinctBy { it.id }, ::missing) { source, allGaps ->
                // Owner 16.11 (replaces 12.11): a portal whose reserve guide is on may take sources 2 and 3 as well —
                // they are offered to it one by one, like to a playlist.
                val gaps = allGaps
                if (gaps.isEmpty()) return@visitFeeds
                attempted.add(source.id)
                // Download once, keeping channel names/cache but no unrelated programme rows.
                attempt(source) {
                    try {
                        repository.refreshUrl(source.id, source.url, source.userAgent, programmeIds = emptySet())
                    } catch (_: EpgRepository.NoProgrammesInWindowException) { /* metadata/cache still usable */ }
                    val candidates = db.epgDao().fallbackChannels(source.id)
                    val ids = candidates.mapTo(HashSet()) { it.epgChannelId }
                    val prepared = EpgMatcher.prepare(candidates.flatMap { c ->
                        c.displayName?.split(XmltvParser.NAME_JOIN)?.map { EpgMatcher.Candidate(c.epgChannelId, it) }
                            ?: listOf(EpgMatcher.Candidate(c.epgChannelId, null))
                    })
                    // In chunks, so the pill can say how far it is (owner 16.11: «бесконечно грузит», no numbers).
                    val tracker = runCatching {
                        org.koin.core.context.GlobalContext.get().get<tv.own.owntv.core.sync.EpgActivityTracker>()
                    }.getOrNull()
                    val hits = ArrayList<EpgMatcher.Result?>(gaps.size)
                    gaps.chunked(MATCH_CHUNK).forEach { chunk ->
                        hits.addAll(EpgMatcher.bestEpgMatchBulk(chunk.map { it.channel.matchName }, prepared))
                        tracker?.matching(source.id, hits.size, gaps.size)
                    }
                    val aliases = HashMap<String, MutableSet<String>>()
                    val windows = HashMap<String, MutableList<Pair<Long, Long>>>()
                    val newLinks = ArrayList<Pair<Missing, String>>()
                    // Owner 16.11: the provider gives dozens of different channels («Кинозал | …», «Мужское кино»,
                    // «Легенда»…) ONE shared guide id. Each was matched to its own reserve channel by name, but all
                    // the programmes were written under that one shared id — so every one of those channels showed
                    // a minute-by-minute mix of many channels (an adult film among them). A shared id now never
                    // receives a name match: such a channel is linked to its own reserve channel instead, and a
                    // target that would still collect more than one reserve channel is dropped.
                    val sharedKeys = gaps.filter { it.key != null && it.key !in ids }
                        .groupBy { it.key }
                        .filterValues { g -> g.map { it.channel.matchName }.distinct().size > 1 }
                        .keys
                    val picked = ArrayList<Triple<Missing, String, String>>() // gap, reserve id, stored under
                    for ((gap, hit) in gaps.zip(hits)) {
                        val key = gap.key
                        if (key != null && key in ids) { picked.add(Triple(gap, key, key)); continue }
                        val id = hit?.takeIf { it.score >= EpgMatcher.AUTO_THRESHOLD }?.epgChannelId ?: continue
                        val target = if (key != null && key !in sharedKeys) key else id
                        picked.add(Triple(gap, id, target))
                    }
                    val sources = HashMap<String, MutableSet<String>>()
                    picked.forEach { (_, id, target) -> sources.getOrPut(target) { HashSet() }.add(id) }
                    val ambiguous = sources.filterValues { it.size > 1 }.keys
                    if (ambiguous.isNotEmpty()) {
                        runCatching { db.epgDao().deleteProgrammesForChannels(source.id, ambiguous.toList()) }
                    }
                    for ((gap, id, target) in picked) {
                        if (target in ambiguous) continue
                        aliases.getOrPut(id) { HashSet() }.add(target)
                        windows.getOrPut(target) { ArrayList() }.add(gap.from to gap.to)
                        if (gap.key == null || gap.key in sharedKeys) newLinks.add(gap to id)
                    }
                    val added = repository.fillFallbackCache(source.id, aliases, windows)
                    filled += added.size
                    tracker?.progress(source.id, filled, 0)
                    for ((pid, links) in newLinks.filter { it.second in added }.groupBy { it.first.profile }) {
                        customize.update(pid, MediaType.LIVE) { current ->
                            val safe = links.filter { current.epgMatchResolver.epgIdFor(it.first.channel) == null }
                                .associate { CustomizeKeys.channel(it.first.channel) to it.second }
                            current.copy(epgMatches = current.epgMatches + safe)
                        }
                    }
                }
            }
            feeds.firstOrNull { it.id !in attempted }?.let { store.setSynced(it.id, System.currentTimeMillis(), null) }
            CorePerf.log { "epg_gap_pass atMs=${SystemClock.elapsedRealtime()} stalker=$stalkerOnly filled=$filled ms=${SystemClock.elapsedRealtime() - started}" }
            filled
        }
    }

    /**
     * The reserve guide written before 16.11 could hold several channels' programmes mixed under one shared
     * id, and such a channel then counted as «has a guide» and was never filled again. Once, its rows are
     * cleared; this very pass fills them again, now correctly. The playlist's own guide is not touched.
     */
    private suspend fun purgeMixedReserveOnce() {
        val prefs = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
            .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(PURGED_V2, false)) return
        store.getAll().filter { it.builtin }.forEach { runCatching { db.epgDao().clearSource(it.id) } }
        prefs.edit().putBoolean(PURGED_V2, true).apply()
    }

    private suspend fun attempt(source: EpgSource, block: suspend () -> Unit) {
        try {
            block()
            store.setSynced(source.id, System.currentTimeMillis(), null)
        } catch (c: CancellationException) { throw c }
        catch (e: Exception) {
            store.markError(source.id, e.message)
            Log.w("EpgGapFill", "sourceId=${source.id} failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        private val passLock = Mutex()
        private const val USER_GUIDE_FRESH_MS = 30 * 60 * 1000L
        private const val PREFS = "epg_gap_fill"
        private const val MATCH_CHUNK = 400
        private const val PURGED_V2 = "reserve_purged_shared_ids_v2"
    }
}

internal object EpgGapPlan {
    fun inWindow(start: Long, stop: Long, windows: List<Pair<Long, Long>>): Boolean =
        windows.any { (from, to) -> stop > from && start < to }
    fun needsGuide(key: String?, covered: Set<String>): Boolean = key == null || key !in covered
    suspend fun <S, G> visitFeeds(feeds: List<S>, missing: suspend () -> List<G>, fill: suspend (S, List<G>) -> Unit) {
        for (feed in feeds) {
            val gaps = missing()
            if (gaps.isEmpty()) break
            fill(feed, gaps)
        }
    }
    fun ordered(ids: List<Pair<Long, Boolean>>): List<Long> =
        ids.filterNot { it.second }.map { it.first } + ids.filter { it.second }.map { it.first }
}
