package tv.own.owntv.core.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.EpgSyncScheduler

/**
 * The app's own hidden guides: three XMLTV feeds, tried in order per channel, with
 * channel logos. It is registered once after a playlist syncs, sits LAST in the guide priority (so a
 * guide the user adds, or a portal's own, wins), is hidden from the Settings → EPG list and can be
 * switched off. Stalker portals get it too, as a fallback for channels their own guide lacks.
 */
class BuiltinGuide(
    private val store: EpgSourceStore,
    private val settings: SettingsRepository,
    private val scheduler: EpgSyncScheduler,
) {
    /** State of the hidden guides, shared by the phone and TV settings screens. */
    data class Status(val syncing: Boolean = false, val lastSyncAt: Long? = null, val failed: Boolean = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun status(): Flow<Status> = store.sources.flatMapLatest { all ->
        val builtin = all.filter { it.builtin }
        val syncing = if (builtin.isEmpty()) flowOf(false) else combine(builtin.map { scheduler.observeSync(it.id) }) { st -> st.any { it.isActive } }
        syncing.map { Status(it, builtin.mapNotNull { b -> b.lastSyncAt }.maxOrNull(), builtin.isNotEmpty() && builtin.all { b -> b.lastError != null }) }
    }

    /** How often the hidden guides refresh on their own (they share one choice); weekly until changed. */
    fun refreshMode(): Flow<EpgRefresh> = combine(store.sources, settings.epgAutoRefresh) { all, modes ->
        all.firstOrNull { it.builtin }?.let { modes[it.id] } ?: DEFAULT_REFRESH
    }

    suspend fun setRefreshMode(refresh: EpgRefresh) {
        store.getAll().filter { it.builtin }.forEach { settings.setEpgAutoRefresh(it.id, refresh) }
    }

    /** The three schedules offered for the built-in guide, in the order a tap cycles through them. */
    enum class Schedule(val refresh: EpgRefresh) {
        WEEKLY(DEFAULT_REFRESH),
        DAILY(EpgRefresh(EpgAutoRefresh.HOURS_24)),
        MANUAL_ONLY(EpgRefresh(EpgAutoRefresh.OFF));

        fun next(): Schedule = entries[(ordinal + 1) % entries.size]

        companion object {
            fun of(r: EpgRefresh): Schedule = when (r.mode) {
                EpgAutoRefresh.OFF -> MANUAL_ONLY
                EpgAutoRefresh.MANUAL -> WEEKLY
                else -> DAILY
            }
        }
    }

    fun schedule(): Flow<Schedule> = refreshMode().map { Schedule.of(it) }

    suspend fun cycleSchedule() {
        val now = Schedule.of(refreshMode().first())
        setRefreshMode(now.next().refresh)
    }

    /** Manual refresh: re-sync the hidden guides, creating them first if no playlist has synced yet. */
    suspend fun refresh() {
        fixUrls()
        val builtin = store.getAll().filter { it.builtin }
        if (builtin.isEmpty()) ensure() else builtin.firstOrNull()?.let { scheduler.enqueueSync(it.id, "manual_builtin") }
    }

    /**
     * A playlist was just imported or re-synced. If the guide already exists, its feeds were fetched before
     * this playlist's channels existed, so run the chain again: it matches the new channels by name.
     */
    suspend fun onPlaylistImported() {
        fixUrls()
        val builtin = store.getAll().filter { it.builtin }
        if (builtin.isEmpty()) ensure() else scheduler.enqueueSync(builtin.first().id, "builtin_playlist", policy = androidx.work.ExistingWorkPolicy.KEEP)
    }

    /** One built-in feed as the settings screens show it. */
    data class Feed(
        val id: Long,
        val number: Int,
        val syncing: Boolean,
        val lastSyncAt: Long?,
        val failed: Boolean,
        /** Channels of the user's playlists that this feed supplies programmes for. */
        val channels: Int,
    )

    private val epgDao: tv.own.owntv.core.database.dao.EpgDao by lazy {
        org.koin.core.context.GlobalContext.get().get()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun feeds(): Flow<List<Feed>> = store.sources.flatMapLatest { all ->
        val builtin = all.filter { it.builtin }
        if (builtin.isEmpty()) flowOf(emptyList())
        else combine(
            builtin.mapIndexed { i, b ->
                combine(scheduler.observeSync(b.id), epgDao.channelsWithProgrammesFlow(b.id)) { st, n ->
                    Feed(b.id, i + 1, st.isActive, b.lastSyncAt, b.lastError != null, n)
                }
            },
        ) { it.toList() }
    }

    /** Refresh one feed on its own, without starting the others. */
    suspend fun refreshFeed(id: Long) {
        fixUrls()
        scheduler.enqueueSync(id, "manual_feed")
    }

    /** Older installs carry the first three addresses; point them at the current list, keeping their order. */
    suspend fun fixUrls() {
        store.getAll().filter { it.builtin }.forEachIndexed { i, s ->
            val url = FEEDS.getOrNull(i) ?: return@forEachIndexed
            if (s.url != url) store.update(s.copy(url = url, lastSyncAt = null, lastError = null))
        }
    }

    suspend fun ensure() {
        fixUrls()
        // Owner 12.11: one reserve guide only — the light one. The two big feeds of older installs go, with
        // everything they stored, so they neither download nor sit in the database.
        store.getAll().filter { it.builtin }.drop(FEEDS.size).forEach { extra ->
            runCatching { epgDao.clearSource(extra.id) }
            store.remove(extra.id)
        }
        FEEDS.forEachIndexed { i, url ->
            val created = store.ensureBuiltin("$NAME ${i + 1}", url) ?: return@forEachIndexed
            settings.setEpgUseLogos(created.id, true)
            settings.setEpgAutoRefresh(created.id, DEFAULT_REFRESH)
            // Queue only after all feeds have been registered.
        }
        store.getAll().firstOrNull { it.builtin }?.let { scheduler.enqueueSync(it.id, "builtin", policy = androidx.work.ExistingWorkPolicy.KEEP) }
    }

    companion object {
        const val NAME = "Built-in guide"

        /** For screens whose view model cannot take it as a constructor argument. */
        fun fromKoin(): BuiltinGuide = org.koin.core.context.GlobalContext.get().let { BuiltinGuide(it.get(), it.get(), it.get()) }

        /** A guide covers about a week, so a weekly refresh is plenty; more often is the user's choice. */
        val DEFAULT_REFRESH = EpgRefresh(EpgAutoRefresh.MANUAL, manualDays = 7)

        /** In fallback order: a channel missing from one feed is looked up in the next. */
        val FEEDS = listOf(
            "https://iptvx.one/EPG_LITE",
        )
    }
}
