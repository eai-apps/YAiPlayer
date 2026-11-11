package tv.own.owntv.core.epg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.EpgMatchResolver
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * "Use this guide's channel logos" (Settings → EPG Sources → add/edit a feed): channel logos taken from
 * an XMLTV feed's `<channel><icon src>` instead of the ones the playlist carries. Enabled **per EPG
 * source**, so one feed can supply logos while another only supplies programmes.
 *
 * Kept as a process-wide display override rather than being written into `channels.logoUrl` on purpose:
 * the provider logo stays untouched in the database, so turning a feed's toggle off restores it instantly
 * and a catalog re-sync (which REPLACE-upserts every channel row) can't wipe the user's choice.
 *
 * A channel the user matched in Match EPG takes the logo of the guide channel it is matched **to**, not
 * of its own `tvg-id` — the same channel the guide shows for it. The active profile's Live TV matches are
 * followed for that.
 *
 * The map is Compose state, so flipping a toggle or finishing an EPG sync recomposes every logo on
 * screen. Nothing is loaded while no feed has it on — cold start stays free of EPG queries.
 */
object EpgLogoStore {
    /** Normalized names that exist both as a national and a regional channel in the feeds. */
    private val AMBIGUOUS_NAMES = setOf("5 канал")


    /** Normalized EPG channel id → feed icon URL. Empty when no EPG source has logos enabled. */
    var icons by mutableStateOf<Map<String, String>>(emptyMap())
        internal set

    /** Normalized guide channel NAME (every alias) → icon: logos for channels whose playlist has none. */
    private var nameIcons by mutableStateOf<Map<String, String>>(emptyMap())

    /**
     * Automatic fallback for channels that come with NO logo at all (they showed only their name): any stored
     * guide's icon, by id or exact name. Built off the main thread from epg_channels only (written once per guide
     * sync), so it costs nothing while browsing. A provider logo, or a feed the user opted into, always wins.
     */
    private var fallbackIds by mutableStateOf<Map<String, String>>(emptyMap())
    private var fallbackNames by mutableStateOf<Map<String, String>>(emptyMap())

    /** Playlists the user switched "Logos from the guide" on for. */
    private var guideLogoSources by mutableStateOf<Set<Long>>(emptySet())

    /** The active profile's Live TV matches; empty until [start] has read them. */
    internal var matches by mutableStateOf(EpgMatchResolver(emptyMap()))

    /** Follow the enabled EPG sources; while any is enabled, follow their stored feed icons and the matches. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope, settings: SettingsRepository, epgDao: EpgDao, customize: CustomizationStore) {
        scope.launch {
            settings.epgUseLogos.collectLatest { sourceIds ->
                if (sourceIds.isEmpty()) {
                    icons = emptyMap()
                    return@collectLatest
                }
                epgDao.observeChannelIcons(sourceIds.toList()).collectLatest { rows ->
                    // Several enabled feeds can carry the same channel: first one wins, which is stable
                    // because the DAO returns rows in source order.
                    icons = rows.associate { it.epgChannelId to it.iconUrl }
                }
            }
        }
        scope.launch {
            // Only built while a playlist asked for it; one pass over the stored guide channels, then memory.
            settings.guideLogoSources.collectLatest { sources ->
                guideLogoSources = sources
                if (sources.isEmpty()) {
                    nameIcons = emptyMap()
                    return@collectLatest
                }
                settings.epgUseLogos.collectLatest { feedIds ->
                    if (feedIds.isEmpty()) {
                        nameIcons = emptyMap()
                        return@collectLatest
                    }
                    epgDao.observeChannelNameIcons(feedIds.toList()).collectLatest { rows ->
                        nameIcons = withContext(Dispatchers.Default) { buildNameIcons(rows) }
                    }
                }
            }
        }
        scope.launch {
            epgDao.observeAllChannelIcons().collectLatest { rows ->
                fallbackIds = withContext(Dispatchers.Default) {
                    val out = HashMap<String, String>(rows.size)
                    rows.forEach { out.putIfAbsent(it.epgChannelId.trim().lowercase(), it.iconUrl) }
                    out
                }
            }
        }
        scope.launch {
            epgDao.observeAllChannelNameIcons().collectLatest { rows ->
                fallbackNames = withContext(Dispatchers.Default) { buildNameIcons(rows) }
            }
        }
        scope.launch {
            settings.activeProfileId
                .flatMapLatest { profileId -> customize.observe(profileId, MediaType.LIVE) }
                .collectLatest { matches = it.epgMatchResolver }
        }
    }

    /**
     * Match EPG's "Include guide logos": turn "Use this guide's logos" on for every EPG source that has an
     * icon for one of [epgChannelIds], so each of those matched channels shows its guide logo. Never turns
     * anything off.
     */
    suspend fun includeLogosFor(settings: SettingsRepository, epgDao: EpgDao, epgChannelIds: Collection<String>) {
        val ids = epgChannelIds.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }.distinct()
        if (ids.isEmpty()) return
        // SQLite caps bound parameters; a bulk run can match thousands of channels.
        val sources = ids.chunked(500).flatMap { epgDao.sourcesWithIconsFor(it) }.toSet()
        sources.forEach { settings.setEpgUseLogos(it, true) }
    }

    private fun buildNameIcons(rows: List<tv.own.owntv.core.database.entity.EpgChannelNameIcon>): Map<String, String> {
        val out = HashMap<String, String>(rows.size * 2)
        for (r in rows) {
            for (alias in r.displayName.split(tv.own.owntv.core.parser.XmltvParser.NAME_JOIN)) {
                val key = EpgMatcher.normalizeForEpg(alias)
                // Short or numeric-only names are too ambiguous to trust ("1", "tv").
                if (key.length < 3 || key.none { it.isLetter() }) continue
                out.putIfAbsent(key, r.iconUrl)
            }
        }
        // Names that the feed shares between a national channel and a regional one: no icon beats the wrong one.
        AMBIGUOUS_NAMES.forEach(out::remove)
        return out
    }

    /** The feed icon for [channel], or null when there is none / no feed has logos enabled. */
    fun iconFor(channel: ChannelEntity): String? {
        if (icons.isEmpty() && nameIcons.isEmpty()) return null
        matches.epgIdFor(channel)?.let { id -> icons[id.trim().lowercase()]?.let { return it } }
        val own = channel.epgChannelId
        if (!own.isNullOrBlank()) icons[own.trim().lowercase()]?.let { return it }
        return byNameIcon(channel)
    }

    /** A guide icon for a channel whose playlist gave it no logo; null when no guide has one. */
    fun fallbackFor(channel: ChannelEntity): String? {
        if (!channel.logoUrl.isNullOrBlank()) return null
        if (fallbackIds.isEmpty() && fallbackNames.isEmpty()) return null
        matches.epgIdFor(channel)?.let { id -> fallbackIds[id.trim().lowercase()]?.let { return it } }
        channel.epgChannelId?.takeIf { it.isNotBlank() }?.let { id -> fallbackIds[id.trim().lowercase()]?.let { return it } }
        return fallbackNames[EpgMatcher.normalizeForEpg(channel.name)]
    }

    /** Exact-name logo for a channel of an opted-in playlist that carries none itself. */
    private fun byNameIcon(channel: ChannelEntity): String? {
        if (nameIcons.isEmpty() || channel.sourceId !in guideLogoSources) return null
        if (!channel.logoUrl.isNullOrBlank()) return null
        return nameIcons[EpgMatcher.normalizeForEpg(channel.name)]
    }
}

/**
 * The logo to show for this channel: the EPG feed's icon when "Use this guide's channel logos" is on and the feed has
 * one — for the guide channel it is matched to, else its own id — otherwise the provider's own logo. Use
 * this everywhere a channel logo is displayed or handed to the player — `logoUrl` alone always means
 * "what the playlist said".
 */
val ChannelEntity.displayLogoUrl: String?
    get() = EpgLogoStore.iconFor(this) ?: logoUrl?.takeIf { it.isNotBlank() } ?: EpgLogoStore.fallbackFor(this)
