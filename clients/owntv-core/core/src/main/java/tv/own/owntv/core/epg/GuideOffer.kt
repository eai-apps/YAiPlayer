// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.epg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.live.epgKeyOf
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.BuiltinGuide

/**
 * The reserve guide is OFFERED, never downloaded by itself (owner 13.11: it loaded on its own after a playlist was
 * added, the box lagged and the player crashed). After a playlist's own guide is in, if channels still have no
 * programme, the app asks: «load the missing guide from additional source 1?» — one source at a time; after
 * source 1 has filled what it can and channels are still missing, source 2 is offered, then 3. «Not now» stops
 * the offers until the next playlist sync. Portals keep their own «additional guide for the portal» switch.
 */
class GuideOffer(
    private val db: OwnTVDatabase,
    private val store: EpgSourceStore,
    private val customize: CustomizationStore,
    private val settings: SettingsRepository,
) {
    /**
     * What the chooser shows: [number] of the source to load (1..3), channels [missing] of [total]. With
     * [portalId] it is a Stalker portal without a guide of its own (owner 16.11): accepting switches on the
     * reserve guide for that portal.
     */
    data class Offer(
        val feedId: Long, val number: Int, val missing: Int, val total: Int,
        val portalId: Long? = null, val portalName: String? = null,
    )

    private val _weekly = MutableStateFlow(false)
    /** Monday/Tuesday morning: this week's guide is not in yet — «Загрузить новый телегид?» (owner 16.11). */
    val weekly: StateFlow<Boolean> = _weekly

    private val _offer = MutableStateFlow<Offer?>(null)
    val offer: StateFlow<Offer?> = _offer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var declined = false

    /** A playlist's own guide finished: start over from source 1. */
    fun afterPlaylistGuide() { declined = false; evaluate(after = 0) }

    /** The portal the current chain of offers is for (after its own guide, then source 1, then 2). */
    @Volatile private var chainPortal: Long? = null

    /** A portal's own guide finished: what it covered, then source 1 for the rest (owner 16.11). */
    fun afterPortalGuide(portalId: Long) {
        scope.launch {
            runCatching {
                val active = db.profileDao().resolveExistingProfileId(settings.activeProfileId.first()) ?: return@launch
                val portal = db.sourceDao().getById(portalId) ?: return@launch
                val (missing, total) = portalCoverage(active, portal) ?: return@launch
                val context = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
                val text = context.getString(tv.own.owntv.core.R.string.guide_portal_done, portal.name, total - missing, total)
                kotlinx.coroutines.withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show() }
                declined = false
                if (missing > 0 && store.builtinEnabled.first()) {
                    kotlinx.coroutines.delay(NEXT_OFFER_DELAY_MS)
                    val feed = store.getAll().firstOrNull { it.builtin } ?: return@launch
                    chainPortal = portalId
                    _offer.value = Offer(feed.id, 1, missing, total, portalId, portal.name)
                }
            }
        }
    }

    /** Additional source [number] finished: offer the next one if channels are still missing. */
    fun afterFeed(feedId: Long, filled: Int = 0) {
        scope.launch {
            val n = store.getAll().filter { it.builtin }.indexOfFirst { it.id == feedId }
            if (n < 0) return@launch
            // Owner 13.11: the next offer came at once and nothing seemed loaded. Say what this source gave first.
            runCatching {
                val context = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
                val text = if (filled > 0) context.resources.getQuantityString(tv.own.owntv.core.R.plurals.guide_offer_filled, filled, n + 1, filled)
                else context.getString(tv.own.owntv.core.R.string.guide_offer_filled_none, n + 1)
                kotlinx.coroutines.withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show() }
            }
            kotlinx.coroutines.delay(NEXT_OFFER_DELAY_MS)
            // A portal's chain: the next source for the same portal (owner 16.11: source 2 offered too).
            val portalId = chainPortal
            if (portalId != null) {
                runCatching {
                    val active = db.profileDao().resolveExistingProfileId(settings.activeProfileId.first()) ?: return@launch
                    val portal = db.sourceDao().getById(portalId) ?: return@launch
                    val next = store.getAll().filter { it.builtin }.getOrNull(n + 1)
                    val (missing, total) = portalCoverage(active, portal) ?: return@launch
                    if (next == null || missing == 0 || declined) { chainPortal = null; _offer.value = null; return@launch }
                    _offer.value = Offer(next.id, n + 2, missing, total, portalId, portal.name)
                }
                return@launch
            }
            evaluate(after = n + 1)
        }
    }

    fun decline() { declined = true; chainPortal = null; _offer.value = null }

    /** «Load»: source 1 directly; sources 2 and 3 are switched on first (they are off by default). */
    fun accept(o: Offer) {
        _offer.value = null
        scope.launch {
            o.portalId?.let { settings.setPortalBuiltinEpg(it, true); chainPortal = it }
            val guide = BuiltinGuide.fromKoin()
            val feed = guide.feeds().first().firstOrNull { it.id == o.feedId } ?: return@launch
            if (feed.number == 1) guide.refreshFeed(feed.id) else guide.setFeedEnabled(feed, true)
        }
    }

    /**
     * On entering a profile (after it settles): the weekly offer on Monday/Tuesday morning, and — when a
     * portal of the profile has no programme for most of its channels and its reserve guide is off — the
     * offer to load the reserve for it.
     */
    fun onProfileEntered() {
        scope.launch {
            runCatching {
                val active = db.profileDao().resolveExistingProfileId(settings.activeProfileId.first()) ?: return@launch
                val own = db.sourceDao().sourceIdsForProfile(active).toSet()
                val sources = db.sourceDao().getAllOnce().filter { it.id in own && it.lastSyncAt != null }
                if (sources.isEmpty()) return@launch
                val guides = store.getAll()
                val repo = org.koin.core.context.GlobalContext.get().get<tv.own.owntv.core.repository.EpgRepository>()
                val ownGuides = sources.flatMap { src ->
                    val urls = repo.guideUrls(src).toSet() +
                        (if (src.type == SourceType.STALKER) setOf(tv.own.owntv.core.repository.EpgRepository.stalkerGuideUrl(src.id)) else emptySet())
                    guides.filter { !it.builtin && it.url in urls }
                }
                val lastGuide = (ownGuides + guides.filter { it.builtin }).mapNotNull { it.lastSyncAt }.maxOrNull()
                val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                if (tv.own.owntv.core.sync.WeeklySchedule.isGuideOfferTime(lastGuide, System.currentTimeMillis()) &&
                    prefs().getString(WEEKLY_DECLINED, null) != today
                ) {
                    _weekly.value = true
                    return@launch
                }
                portalOffer(active, sources.filter { it.type == SourceType.STALKER })?.let { _offer.value = it }
            }
        }
    }

    /** «От провайдера»: every guide the profile's playlists and portals name. */
    fun weeklyFromProvider() {
        _weekly.value = false
        scope.launch {
            runCatching {
                val active = db.profileDao().resolveExistingProfileId(settings.activeProfileId.first()) ?: return@launch
                val own = db.sourceDao().sourceIdsForProfile(active).toSet()
                val quick = org.koin.core.context.GlobalContext.get().get<tv.own.owntv.core.sync.QuickActionsSync>()
                db.sourceDao().getAllOnce().filter { it.id in own }.forEach { quick.refreshEpg(it) }
            }
        }
    }

    /** «Из наших источников»: the reserve sources that are on. */
    fun weeklyFromReserve() {
        _weekly.value = false
        scope.launch {
            runCatching {
                val guide = BuiltinGuide.fromKoin()
                guide.feeds().first().filter { it.enabled }.forEach { guide.refreshFeed(it.id) }
            }
        }
    }

    /** «Не сейчас»: not again today. */
    fun weeklyLater() {
        _weekly.value = false
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        prefs().edit().putString(WEEKLY_DECLINED, today).apply()
    }

    private suspend fun portalOffer(active: Long, portals: List<tv.own.owntv.core.database.entity.SourceEntity>): Offer? {
        if (declined || !store.builtinEnabled.first() || portals.isEmpty()) return null
        val extra = settings.portalBuiltinEpgSources.first()
        val feed = store.getAll().firstOrNull { it.builtin } ?: return null
        for (portal in portals.filter { it.id !in extra }) {
            val (missing, total) = portalCoverage(active, portal) ?: continue
            // Most of the portal without a programme: it has no guide of its own worth the name.
            if (missing * 2 > total) return Offer(feed.id, 1, missing, total, portal.id, portal.name)
        }
        return null
    }

    /** Channels of [portal] (not hidden) without a programme for now → (missing, total). */
    private suspend fun portalCoverage(active: Long, portal: tv.own.owntv.core.database.entity.SourceEntity): Pair<Int, Int>? {
        val cust = customize.observe(active, MediaType.LIVE).first()
        val channels = db.channelDao().allForSources(listOf(portal.id), Int.MAX_VALUE)
            .filter { CustomizeKeys.channel(it) !in cust.hiddenItems }
        if (channels.isEmpty()) return null
        val keys = channels.map { epgKeyOf(it, cust) }
        val now = System.currentTimeMillis()
        val have = HashSet<String>()
        keys.filterNotNull().distinct().chunked(500).forEach { have.addAll(db.epgDao().keysWithGuide(it, now - 3 * 3_600_000L, now + 86_400_000L)) }
        return keys.count { it == null || it !in have } to channels.size
    }

    private fun prefs() = org.koin.core.context.GlobalContext.get().get<android.content.Context>()
        .getSharedPreferences("guide_offer", android.content.Context.MODE_PRIVATE)

    private fun evaluate(after: Int) {
        scope.launch {
            runCatching {
                if (declined || !store.builtinEnabled.first()) { _offer.value = null; return@launch }
                val feeds = store.getAll().filter { it.builtin }
                val next = feeds.getOrNull(after) ?: run { _offer.value = null; return@launch }
                val (missing, total) = coverage() ?: run { _offer.value = null; return@launch }
                _offer.value = if (missing > 0) Offer(next.id, after + 1, missing, total) else null
            }
        }
    }

    /** Channels of the active profile's playlists (not portals, not hidden) without a programme for now → (missing, total). */
    suspend fun coverage(): Pair<Int, Int>? {
        val active = db.profileDao().resolveExistingProfileId(settings.activeProfileId.first()) ?: return null
        val own = db.sourceDao().sourceIdsForProfile(active).toSet()
        val playlists = db.sourceDao().getAllOnce().filter { it.id in own && it.type != SourceType.STALKER && it.type != SourceType.LOCAL_BACKUP }
        if (playlists.isEmpty()) return null
        val cust = customize.observe(active, MediaType.LIVE).first()
        val channels = db.channelDao().allForSources(playlists.map { it.id }, Int.MAX_VALUE)
            .filter { CustomizeKeys.channel(it) !in cust.hiddenItems }
        if (channels.isEmpty()) return null
        val keys = channels.map { epgKeyOf(it, cust) }
        val now = System.currentTimeMillis()
        val have = HashSet<String>()
        keys.filterNotNull().distinct().chunked(500).forEach { have.addAll(db.epgDao().keysWithGuide(it, now - 3 * 3_600_000L, now + 86_400_000L)) }
        val missing = keys.count { it == null || it !in have }
        return missing to channels.size
    }

    companion object {
        private const val NEXT_OFFER_DELAY_MS = 4_000L
        private const val WEEKLY_DECLINED = "weekly_declined_day"
        fun fromKoin(): GuideOffer = org.koin.core.context.GlobalContext.get().get()
    }
}
