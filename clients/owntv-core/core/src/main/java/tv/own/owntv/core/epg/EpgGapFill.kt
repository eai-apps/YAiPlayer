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
            val started = SystemClock.elapsedRealtime()
            val playlists = db.sourceDao().getAllOnce().filter { !stalkerOnly || it.type == SourceType.STALKER }
            val profileIds = db.profileDao().getAllOnce().map { it.id }
            // Playlist URLs are registered before the pass is enqueued. Preserve the user's order.
            if (!stalkerOnly) {
                for (source in store.getAll().filterNot { it.builtin }) attempt(source) {
                    repository.refreshUrl(source.id, source.url, source.userAgent)
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
                        for ((days, archived) in group.filter { it.channel.catchup }.groupBy { it.channel.catchupDays.coerceIn(1, 14) }) {
                            val from = now - days * 86_400_000L
                            val past = HashSet<String>()
                            archived.mapNotNull { it.key }.distinct().chunked(500).forEach {
                                past.addAll(db.epgDao().keysWithGuide(it, from, now))
                            }
                            result.addAll(archived.filter { EpgGapPlan.needsGuide(it.key, past) }
                                .map { it.copy(from = from, to = now) })
                        }
                    }
                }
                return result
            }
            var filled = 0
            val feeds = store.getAll().filter { it.builtin && (onlyFeed == null || it.id == onlyFeed) }
            val attempted = HashSet<Long>()
            EpgGapPlan.visitFeeds(feeds.distinctBy { it.id }, ::missing) { source, gaps ->
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
                    val hits = EpgMatcher.bestEpgMatchBulk(gaps.map { it.channel.matchName }, prepared)
                    val aliases = HashMap<String, MutableSet<String>>()
                    val windows = HashMap<String, MutableList<Pair<Long, Long>>>()
                    val newLinks = ArrayList<Pair<Missing, String>>()
                    for ((gap, hit) in gaps.zip(hits)) {
                        val id = gap.key?.takeIf { it in ids }
                            ?: hit?.takeIf { it.score >= EpgMatcher.AUTO_THRESHOLD }?.epgChannelId
                            ?: continue
                        val target = gap.key ?: id
                        aliases.getOrPut(id) { HashSet() }.add(target)
                        windows.getOrPut(target) { ArrayList() }.add(gap.from to gap.to)
                        if (gap.key == null) newLinks.add(gap to id)
                    }
                    val added = repository.fillFallbackCache(source.id, aliases, windows)
                    filled += added.size
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

    companion object { private val passLock = Mutex() }
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
