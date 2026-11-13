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
    /** What the chooser shows: [number] of the source to load (1..3), channels [missing] of [total]. */
    data class Offer(val feedId: Long, val number: Int, val missing: Int, val total: Int)

    private val _offer = MutableStateFlow<Offer?>(null)
    val offer: StateFlow<Offer?> = _offer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var declined = false

    /** A playlist's own guide finished: start over from source 1. */
    fun afterPlaylistGuide() { declined = false; evaluate(after = 0) }

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
            evaluate(after = n + 1)
        }
    }

    fun decline() { declined = true; _offer.value = null }

    /** «Load»: source 1 directly; sources 2 and 3 are switched on first (they are off by default). */
    fun accept(o: Offer) {
        _offer.value = null
        scope.launch {
            val guide = BuiltinGuide.fromKoin()
            val feed = guide.feeds().first().firstOrNull { it.id == o.feedId } ?: return@launch
            if (feed.number == 1) guide.refreshFeed(feed.id) else guide.setFeedEnabled(feed, true)
        }
    }

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
        fun fromKoin(): GuideOffer = org.koin.core.context.GlobalContext.get().get()
    }
}
