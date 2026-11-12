package tv.own.owntv.core.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.stalker.StalkerAuthManager
import tv.own.owntv.core.stalker.StalkerClient
import tv.own.owntv.core.stalker.StalkerCredentials
import tv.own.owntv.core.stalker.stalkerCredentials

/**
 * What opening a show's episode list came to. A provider refusing the request used to read exactly
 * like a show with no episodes; [Failed] keeps the two apart on screen.
 */
sealed interface EpisodeLoad {
    /** Episodes are stored — cached, or just fetched. */
    data object Ready : EpisodeLoad

    /** Nothing stored, and there is nothing to fetch or the provider sent none. */
    data object Empty : EpisodeLoad

    /** Nothing stored, because the request failed. [reason] is technical and untranslated. */
    data class Failed(val reason: String) : EpisodeLoad
}

/**
 * The reason a [EpisodeLoad.Failed] shows: the provider's HTTP status, else the exception's kind.
 * Never the exception's message — it can carry the provider's host.
 */
internal fun episodeLoadFailure(e: Exception): String =
    if (e is HttpClient.HttpStatusException) "HTTP ${e.code}" else e.javaClass.simpleName.ifEmpty { e.javaClass.name }

/** Loads a series' seasons/episodes on demand (Xtream `get_series_info`; Stalker paged
 *  `get_ordered_list&movie_id=` season rows). Episodes carry their season number
 *  directly, so no separate Season rows are needed for browsing. */
class SeriesRepository(
    private val seriesDao: SeriesDao,
    private val sourceDao: SourceDao,
    private val xtream: XtreamClient,
    private val userData: tv.own.owntv.core.backup.UserDataResolver,
    private val stalkerClient: StalkerClient,
    private val stalkerAuth: StalkerAuthManager,
    private val categoryDao: tv.own.owntv.core.database.dao.CategoryDao,
) {
    /**
     * Returns true if episodes are available (cached or freshly fetched). Xtream + Stalker.
     *
     * The cache used to be write-once — `episodeCount > 0` meant "done, never ask again" — and since
     * the syncers deliberately don't fetch episodes either, nothing in the app could ever add an
     * episode to a show the user had already opened. New episodes only appeared if the source was
     * deleted and re-added (which cascade-drops the rows). Now the cache expires, and a
     * source sync invalidates it outright.
     */
    suspend fun loadEpisodes(shown: SeriesEntity): EpisodeLoad = withContext(Dispatchers.IO) {
        // The row as stored, never the card the screen drew: the TV list renames a season-record card to its
        // bare title («Извне / Ззовні [S01] [ua] [ru]» → «Извне / Ззовні»), and every rule below that finds the
        // show's other season records reads the season tag in the name (owner 12.11: 4 seasons on the phone,
        // 1 on the TV box).
        val series = seriesDao.getSeriesById(shown.id) ?: shown
        val cachedCount = seriesDao.episodeCount(series.id)
        val hasCache = cachedCount > 0
        val stored = if (hasCache) EpisodeLoad.Ready else EpisodeLoad.Empty
        val source = sourceDao.getById(series.sourceId) ?: return@withContext stored

        // M3U is not affected and deliberately doesn't take this path: its episodes come from
        // the playlist during the sync itself, and `M3uSyncer.flushSeries` folds the episode list
        // into the show's content hash — so a playlist that gains an episode rewrites that show's
        // hierarchy on the next resync. There is nothing to fetch on demand and no cache to expire.
        if (source.type != SourceType.XTREAM && source.type != SourceType.STALKER) return@withContext stored

        // Read the stamp from the database rather than the passed-in entity: callers hold list rows
        // that may have been loaded before a sync invalidated the cache.
        val syncedAt = seriesDao.getSeriesById(series.id)?.episodesSyncedAt ?: series.episodesSyncedAt
        // A season record cached before the sibling rule changed (12.11: bilingual and plain titles of one show)
        // holds only part of the show; read it once more instead of waiting out the cache.
        // Owner 12.11 (evening): on the TV box no show offered more than one season, while the phone, which had
        // read them later, did. Every portal show cached before this rule is read once more, whatever its title.
        val oldSiblingRule = source.type == SourceType.STALKER && syncedAt < SIBLING_RULE_EPOCH_MS
        // A season record whose other seasons did not arrive last time (owner 12.11: «Извне» — 4 seasons on the
        // phone, 1 on the TV box, read while the portal was slow) is read again instead of waiting out the cache.
        val missingSiblings = source.type == SourceType.STALKER && cachedCount > 0 &&
            System.currentTimeMillis() - syncedAt > SIBLING_RECHECK_MS &&
            tv.own.owntv.core.content.SeasonRecordTitle.isSeasonRecord(series.name) &&
            runCatching { seriesDao.episodesBySeriesOnce(series.id).map { it.seasonNumber }.distinct().size <= 1 && siblingRecords(series).isNotEmpty() }
                .getOrDefault(false)
        if (!oldSiblingRule && !missingSiblings && !shouldRefreshEpisodes(cachedCount, syncedAt, System.currentTimeMillis())) return@withContext EpisodeLoad.Ready

        val remoteId = series.remoteId
        if (remoteId.isNullOrBlank()) return@withContext stored
        val t0 = System.currentTimeMillis()
        // Exported with «Экспорт журнала»: which show, which strategy answered, how long (owner 12.11: a show that
        // spins forever must be diagnosable from the TV box or phone without a computer).
        tv.own.owntv.core.PerfJournal.add("episodes start id=${series.id} remote=$remoteId cached=$cachedCount name=${series.name.take(60)}")
        // A failed or empty fetch must never empty a populated show: a provider hiccup would
        // otherwise wipe a season the user was midway through. Leave the stamp alone so the next
        // open retries instead of waiting out the TTL on nothing.
        val fetched = try {
            // The page must not spin forever when the portal does not answer: after the budget it fails and can be retried.
            var answered = false
            val list = withTimeoutOrNull(OWN_FETCH_BUDGET_MS) {
                val got = when (source.type) {
                    SourceType.XTREAM -> fetchXtreamEpisodes(series, source, remoteId)
                    else -> fetchStalkerEpisodes(series, source, remoteId)
                }
                answered = true
                got
            }
            if (!answered) throw java.io.IOException()
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "${source.type} episode load failed seriesId=${series.id}", e)
            tv.own.owntv.core.PerfJournal.add(
                listOf("episodes failed", series.id, System.currentTimeMillis() - t0, e.javaClass.simpleName, e.message?.take(80)).joinToString(" "),
            )
            return@withContext if (hasCache) EpisodeLoad.Ready else EpisodeLoad.Failed(episodeLoadFailure(e))
        }
        var complete = true
        val withSiblings = if (source.type == SourceType.STALKER && !fetched.isNullOrEmpty()) {
            val (more, allAnswered) = siblingSeasonEpisodes(series, source, fetched)
            complete = allAnswered
            fetched + more
        } else fetched
        tv.own.owntv.core.PerfJournal.add(
            listOf("episodes done", series.id, fetched?.size, withSiblings?.size, System.currentTimeMillis() - t0).joinToString(" "),
        )
        if (withSiblings.isNullOrEmpty()) return@withContext stored
        // Not every season record answered: show what came, but leave the show unstamped so the next open
        // reads the missing seasons instead of keeping one season for the whole cache lifetime.
        applyEpisodes(series.id, withSiblings, markSynced = complete)
        EpisodeLoad.Ready
    }

    /**
     * Writes a fetched list over the stored one, preserving the row id of every episode that
     * survives — see [planEpisodeMerge]. Rebuilding the rows wholesale would detach watch history,
     * resume positions and next-episode autoplay on every single refresh.
     */
    private suspend fun applyEpisodes(seriesId: Long, fetched: List<EpisodeEntity>, markSynced: Boolean = true) {
        val plan = planEpisodeMerge(seriesDao.episodesBySeriesOnce(seriesId), fetched)
        plan.updates.chunked(CHUNK).forEach { seriesDao.updateEpisodes(it) }
        plan.inserts.chunked(CHUNK).forEach { seriesDao.upsertEpisodes(it) }
        plan.deleteIds.chunked(CHUNK).forEach { seriesDao.deleteEpisodesByIds(it) }
        if (markSynced) seriesDao.markEpisodesSynced(seriesId, System.currentTimeMillis())
        android.util.Log.i(
            TAG,
            "episodes seriesId=$seriesId fetched=${fetched.size} new=${plan.inserts.size} " +
                "updated=${plan.updates.size} removed=${plan.deleteIds.size}",
        )
        // Episode rows just appeared — restored/pending episode history and resume can attach now.
        if (plan.inserts.isNotEmpty()) runCatching { userData.resolvePending() }
    }

    /**
     * A panel that files each season as its own record («Title [S01]», «Title [S02]»): the other records'
     * seasons are folded into the one opened, so every card of the show opens the whole show (П8).
     * Each episode keeps its own record's season command, so playback is untouched. A season the opened
     * record already lists wins; a sibling that fails to answer is skipped, never fatal.
     */
    /** The show's other season records in the same playlist («Title [S02]», «Title: Subtitle [S03]»). */
    private suspend fun siblingRecords(series: SeriesEntity): List<SeriesEntity> {
        if (!tv.own.owntv.core.content.SeasonRecordTitle.isSeasonRecord(series.name)) return emptyList()
        val base = tv.own.owntv.core.content.SeasonRecordTitle.base(series.name)
        if (base.length < 2) return emptyList()
        // The part before a season subtitle («…: Отель»), so a subtitled record also finds the plain ones.
        val prefix = base.takeWhile { it !in ":–—(|/" }.trim().takeIf { it.length >= 2 } ?: base
        return runCatching { seriesDao.seriesByNamePrefix(series.sourceId, prefix) }.getOrDefault(emptyList())
            .filter { it.id != series.id && !it.remoteId.isNullOrBlank() && tv.own.owntv.core.content.SeasonRecordTitle.sameShowLoose(it.name, series.name) }
            .take(MAX_SIBLING_RECORDS)
    }

    /**
     * A panel that files each season as its own record («Title [S01]», «Title [S02]»): the other records'
     * seasons are folded into the one opened, so every card of the show opens the whole show (П8).
     * Each episode keeps its own record's season command, so playback is untouched. A season the opened
     * record already lists wins. Records are read a few at a time; the second value says whether every
     * one of them answered (a slow portal must not leave the show at one season for good).
     */
    private suspend fun siblingSeasonEpisodes(
        series: SeriesEntity, source: SourceEntity, own: List<EpisodeEntity>,
    ): Pair<List<EpisodeEntity>, Boolean> {
        val siblings = siblingRecords(series)
        if (siblings.isEmpty()) return emptyList<EpisodeEntity>() to true
        val gate = kotlinx.coroutines.sync.Semaphore(SIBLING_PARALLEL)
        val results = withTimeoutOrNull(SIBLING_BUDGET_MS) {
            kotlinx.coroutines.coroutineScope {
                siblings.map { sibling ->
                    async {
                        gate.withPermit {
                            try {
                                fetchStalkerEpisodes(sibling, source, sibling.remoteId!!)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w(TAG, "sibling season record failed seriesId=${sibling.id}", e)
                                null
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        val haveSeasons = own.mapTo(HashSet()) { it.seasonNumber }
        val out = ArrayList<EpisodeEntity>()
        results?.forEach { eps ->
            eps ?: return@forEach
            eps.filter { it.seasonNumber !in haveSeasons }.forEach { out.add(it.copy(id = 0, seriesId = series.id)) }
            haveSeasons.addAll(eps.map { it.seasonNumber })
        }
        val complete = results != null && results.none { it == null }
        android.util.Log.i(TAG, "sibling season records seriesId=${series.id} siblings=${siblings.size} episodes=${out.size} complete=$complete")
        tv.own.owntv.core.PerfJournal.add("episodes siblings id=${series.id} records=${siblings.size} added=${out.size} complete=$complete")
        return out to complete
    }

    private suspend fun fetchXtreamEpisodes(series: SeriesEntity, source: SourceEntity, remoteId: String): List<EpisodeEntity> {
        val info = xtream.getSeriesInfo(source, remoteId)
        return info.episodes.map { e ->
            EpisodeEntity(
                seriesId = series.id,
                seasonId = null,
                seasonNumber = e.seasonNumber,
                episodeNumber = e.episodeNumber,
                name = e.title,
                streamUrl = xtream.seriesEpisodeUrl(source, e.id, e.containerExt),
                containerExt = e.containerExt,
                remoteId = e.id,
                airDateMs = e.airDateMs,
            )
        }
    }

    /**
     * Stalker: the show's seasons come from paged `get_ordered_list&movie_id=<remoteId>`,
     * each row carrying a `series` array of episode numbers. Every episode stores the SEASON cmd as
     * its streamUrl (the item's identity); the playable per-episode URL is minted at play time via
     * `create_link&type=vod&series=<episode>` (StreamUrlResolver) — never at listing time, the links
     * are short-lived.
     */
    private suspend fun fetchStalkerEpisodes(series: SeriesEntity, source: SourceEntity, remoteId: String): List<EpisodeEntity>? {
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) } ?: return null
        val creds = source.stalkerCredentials(mac)
        // The film-catalogue tree is a fallback only, for every show (operator 12.11: after one show answered
        // through it, every NEW show of the portal went that way only and failed with IOException). A portal
        // known to use it gets it tried first, but the normal routes still follow when it brings nothing.
        if (source.id in vodTreeSources) {
            val first = runCatching { withTimeoutOrNull(TREE_BUDGET_MS) { fetchStalkerVodTree(series, mac, creds, remoteId) } }
                .getOrNull().orEmpty()
            if (first.isNotEmpty()) return first
        }
        suspend fun seasonsFrom(type: String): List<StalkerClient.SeasonItem> {
            val seasons = ArrayList<StalkerClient.SeasonItem>()
            var page = 1
            while (page <= MAX_SEASON_PAGES) {
                val p = stalkerAuth.withAuthRetry(creds) { session ->
                    stalkerClient.getSeriesSeasons(
                        session.apiBase, mac, session.token, creds.userAgent,
                        categoryId = null, movieId = remoteId, page = page, type = type,
                    )
                }
                // A portal that ignores `movie_id` on type=vod answers with the whole catalogue (twenty
                // pages of unrelated titles, which used to become "Season 85"). Only rows that belong to
                // this show count; none on the first page means the portal did not understand the question.
                val mine = if (type == "vod") p.items.filter { it.id == remoteId || it.id.startsWith("$remoteId:") } else p.items
                if (type == "vod" && page == 1 && mine.isEmpty()) break
                seasons += mine
                val maxPer = p.maxPageItems.takeIf { it > 0 } ?: p.items.size
                val pages = if (maxPer > 0) (p.totalItems + maxPer - 1) / maxPer else 1
                if (p.items.isEmpty() || page >= pages) break
                page++
            }
            return seasons
        }
        // The portal's own search, the way the STB menu finds a season record: its title, in its category.
        suspend fun seasonsFromSearch(): List<StalkerClient.SeasonItem> {
            val catRemote = series.categoryId?.let { categoryDao.getById(it)?.remoteId } ?: return emptyList()
            // The full title first; then shorter forms (owner 12.11: «Хорошая американская семья / Зразкова
            // американська сім'я [S01]» — a record with its episode list on its own row, which the portal's
            // search did not find by the whole bilingual title with brackets and an apostrophe).
            val queries = listOf(
                series.name,
                tv.own.owntv.core.content.SeasonRecordTitle.base(series.name),
                series.name.substringBefore('/').substringBefore('[').trim(),
            ).map { it.trim() }.filter { it.length >= 2 }.distinct()
            for (query in queries) {
                val found = ArrayList<StalkerClient.SeasonItem>()
                var seen = 0
                var page = 1
                while (page <= MAX_SEARCH_PAGES) {
                    val p = stalkerAuth.withAuthRetry(creds) { session ->
                        stalkerClient.searchSeasonRows(session.apiBase, mac, session.token, creds.userAgent, catRemote, query, page)
                    }
                    seen += p.items.size
                    found += p.items.filter { it.id == remoteId }
                    val maxPer = p.maxPageItems.takeIf { it > 0 } ?: p.items.size
                    val pages = if (maxPer > 0) (p.totalItems + maxPer - 1) / maxPer else 1
                    if (found.isNotEmpty() || p.items.isEmpty() || page >= pages) break
                    page++
                }
                // Which title the portal knew the show by, and what came back (exported with «Экспорт журнала»).
                tv.own.owntv.core.PerfJournal.add(
                    "episodes search id=${series.id} q=${query.take(48)} rows=$seen found=${found.size} ids=" +
                        found.take(3).joinToString { it.id },
                )
                if (found.isNotEmpty()) return found
            }
            return emptyList()
        }
        // A portal with no series section files its shows in the film catalogue, and lists their
        // seasons there too; the series listing is asked first, as it always was.
        var filmListingFailure: Exception? = null
        val seasons = try {
            seasonsFrom("series")
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            android.util.Log.i(TAG, "stalker seasons via type=series failed remoteId=$remoteId (${e.message})")
            emptyList()
        }.ifEmpty {
            try {
                seasonsFrom("vod")
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Exception) {
                filmListingFailure = e
                emptyList()
            }
        }.ifEmpty {
            try {
                seasonsFromSearch()
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Exception) {
                android.util.Log.i(TAG, "stalker season search failed remoteId=$remoteId (${e.message})")
                emptyList()
            }
        }
        val episodes = ArrayList<EpisodeEntity>()
        val seasonNumbers = plausibleSeasonNumbers(series.name, seasons.map { it.seasonNumber })
        seasons.forEachIndexed { index, season ->
            val cmd = season.cmd ?: return@forEachIndexed
            val seasonNo = seasonNumbers[index]
            season.episodes.forEach { epNo ->
                episodes.add(
                    EpisodeEntity(
                        seriesId = series.id,
                        seasonId = null,
                        seasonNumber = seasonNo,
                        episodeNumber = epNo,
                        // The guide has no provider title for these Stalker rows. Keep the persisted
                        // value locale-neutral; Compose renders the episode number when it is blank.
                        name = "",
                        streamUrl = cmd, // season cmd — resolved per-episode at play time
                        containerExt = null,
                        remoteId = "${season.id}:$epNo",
                    )
                )
            }
        }
        android.util.Log.i(
            TAG,
            "stalker episodes seriesId=${series.id} remoteId=$remoteId seasons=${seasons.size} episodes=${episodes.size}",
        )
        tv.own.owntv.core.PerfJournal.add("episodes stalker id=${series.id} seasons=${seasons.size} episodes=${episodes.size}")
        if (episodes.isNotEmpty()) return episodes
        if (source.id in vodTreeSources) return emptyList() // the tree was asked above
        // Some portals answer the film listing with seasons that carry neither an episode list nor a
        // cmd: Ministra's three-level tree, where each season is one more request. Asked only when
        // nothing playable came back, so every other portal costs nothing extra.
        val tree = try {
            // Bounded: a portal that ignores movie_id answers the tree with its whole catalogue, page after page,
            // which used to eat the show's whole minute and end in IOException («Хорошая американская семья»).
            withTimeoutOrNull(TREE_BUDGET_MS) { fetchStalkerVodTree(series, mac, creds, remoteId) }.orEmpty()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            filmListingFailure?.let { throw it }
            android.util.Log.i(TAG, "stalker vod tree failed remoteId=$remoteId (${e.message})")
            emptyList()
        }
        tv.own.owntv.core.PerfJournal.add("episodes tree id=${series.id} episodes=${tree.size}")
        if (tree.isNotEmpty()) vodTreeSources += source.id
        return tree
    }

    /**
     * A show from the film catalogue's three-level tree: seasons from `type=vod&movie_id=…&season_id=0`,
     * then each season's episodes. These rows carry the episode's own title. Each stores a
     * [StalkerClient.vodEpisodeRef]; its file is listed, and its URL minted, at play time.
     */
    private suspend fun fetchStalkerVodTree(
        series: SeriesEntity, mac: String, creds: StalkerCredentials, remoteId: String,
    ): List<EpisodeEntity> {
        suspend fun <T> pages(fetch: suspend (Int) -> StalkerClient.Page<T>): List<T> {
            val out = ArrayList<T>()
            var page = 1
            while (page <= MAX_SEASON_PAGES) {
                val p = fetch(page)
                out += p.items
                val maxPer = p.maxPageItems.takeIf { it > 0 } ?: p.items.size
                val total = if (maxPer > 0) (p.totalItems + maxPer - 1) / maxPer else 1
                if (p.items.isEmpty() || page >= total) break
                page++
            }
            return out
        }
        val seasons = pages { page ->
            stalkerAuth.withAuthRetry(creds) { s -> stalkerClient.getVodSeasons(s.apiBase, mac, s.token, creds.userAgent, remoteId, page) }
        }
        val episodes = ArrayList<EpisodeEntity>()
        seasons.forEachIndexed { index, season ->
            val seasonNo = season.number ?: StalkerClient.seasonNumberOf(season.id, season.name) ?: (index + 1)
            val rows = pages { page ->
                stalkerAuth.withAuthRetry(creds) { s ->
                    stalkerClient.getVodEpisodes(s.apiBase, mac, s.token, creds.userAgent, remoteId, season.id, page)
                }
            }
            rows.forEachIndexed { i, ep ->
                episodes.add(
                    EpisodeEntity(
                        seriesId = series.id,
                        seasonId = null,
                        seasonNumber = seasonNo,
                        episodeNumber = ep.number ?: (i + 1),
                        name = ep.title.orEmpty(),
                        streamUrl = StalkerClient.vodEpisodeRef(remoteId, season.id, ep.id),
                        containerExt = null,
                        remoteId = ep.id,
                    )
                )
            }
        }
        android.util.Log.i(
            TAG,
            "stalker vod tree episodes seriesId=${series.id} remoteId=$remoteId seasons=${seasons.size} episodes=${episodes.size}",
        )
        return episodes
    }

    /**
     * Stalker playlists whose shows turned out to use the three-level tree. Asking the series and
     * film listings first costs such a portal two wasted requests on every show opened; once one show
     * has answered from the tree, the rest of the session goes there directly.
     */
    private val vodTreeSources: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private companion object {
        const val TAG = "SeriesRepository"

        private val SEASON_TAG = Regex("""\[S(\d{1,3})]""")

        /**
         * Season numbers for a portal's season rows. A row's own number is trusted while it looks like
         * one; a portal that fills it with something else (ids such as 28, 57, 85 on one show) would
         * otherwise print "Season 85" and merge nothing. In that case the rows are numbered by position,
         * and a lone row of a show named "Title [S07]" is that season.
         */
        internal fun plausibleSeasonNumbers(showName: String?, parsed: List<Int?>): List<Int> {
            val own = parsed.mapIndexed { i, n -> n ?: (i + 1) }
            val tagged = showName?.let { SEASON_TAG.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            if (parsed.size == 1 && tagged != null) return listOf(tagged)
            val sane = own.distinct().size == own.size && own.all { it in 0..maxOf(30, parsed.size) }
            return if (sane) own else own.indices.map { it + 1 }
        }


        /** A show with more season records than this is not a show split by season. */
        const val MAX_SIBLING_RECORDS = 30
        private const val SIBLING_PARALLEL = 3
        /** Longest the film-catalogue tree may take for one show. */
        private const val TREE_BUDGET_MS = 20_000L
        /** A one-season record with other records is re-read at most this often (one show with a duplicate record). */
        private const val SIBLING_RECHECK_MS = 10L * 60 * 1000

        /** Seasons cached before this moment (2026-11-12 13:30 UTC) were folded by the older sibling rule. */
        const val SIBLING_RULE_EPOCH_MS = 1794517200000L

        /** Longest wait for the opened record, and then for all other season records together. */
        const val OWN_FETCH_BUDGET_MS = 60_000L
        const val SIBLING_BUDGET_MS = 45_000L

        /** Safety cap on season paging (a show never has hundreds of season pages). */
        const val MAX_SEASON_PAGES = 20

        /** Pages of search results read to find a show's own row. */
        const val MAX_SEARCH_PAGES = 5

        /** Write batch size for the episode merge. */
        const val CHUNK = 500
    }
}
