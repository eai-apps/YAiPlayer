package tv.own.owntv.core.epg

import android.util.Log
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.EpgRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Links playlist channels to the built-in guide by name, by itself, right after a built-in feed synced.
 *
 * The built-in feeds are filtered at download to the channels a playlist already points at by id. A
 * playlist with no guide ids (every Stalker portal, many M3U lists) therefore got nothing from them
 * until someone pressed "Match EPG" by hand. This does that press: confident name hits are written
 * into every profile's matches, then the matched channels' programmes are read from the feed's cached
 * download (no second download). The rest stay for the manual review, as before.
 */
class BuiltinGuideMatcher(
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val customize: CustomizationStore,
    private val db: OwnTVDatabase,
    private val epgRepository: EpgRepository,
    private val settings: tv.own.owntv.core.settings.SettingsRepository? = null,
) {
    data class Result(val applied: Int, val stillWithoutGuide: Int)

    private val matcher = EpgAutoMatcher(channelDao, GuideCandidates(epgDao))

    /**
     * Guide links are kept per profile, so a profile made after the built-in guide matched its channels
     * has none: no programme, no guide logo. Give every profile the matches the others already have
     * (never overriding one it has set itself).
     */
    suspend fun syncProfiles() {
        val profiles = db.profileDao().getAllOnce()
        if (profiles.size < 2) return
        val all = profiles.associate { it.id to customize.observe(it.id, MediaType.LIVE).first().epgMatches }
        val union = LinkedHashMap<String, String>()
        all.values.forEach { m -> m.forEach { (k, v) -> union.putIfAbsent(k, v) } }
        profiles.forEach { p ->
            val have = all[p.id].orEmpty()
            if (union.keys.any { it !in have }) customize.update(p.id, MediaType.LIVE) { it.copy(epgMatches = union + it.epgMatches) }
        }
    }

    /**
     * A Stalker portal brings its own guide; the built-in one is not for it. An earlier version linked those
     * channels to the built-in feeds too, which shadowed the portal's programme — drop those links again
     * (only ones that point INTO the built-in feeds, so a match the user made by hand elsewhere stays).
     */
    private suspend fun dropPortalLinks(portalIds: List<Long>, profiles: List<tv.own.owntv.core.database.entity.ProfileEntity>) {
        if (portalIds.isEmpty()) return
        val builtin = org.koin.core.context.GlobalContext.get().get<EpgSourceStore>().getAll().filter { it.builtin }
        val builtinIds = builtin.flatMapTo(HashSet()) { epgDao.epgChannelIdsForSource(it.id).map { id -> id.trim().lowercase() } }
        if (builtinIds.isEmpty()) return
        val keys = channelDao.allForSources(portalIds, 20_000).mapTo(HashSet()) { tv.own.owntv.core.customize.CustomizeKeys.channel(it) }
        profiles.forEach { p ->
            val have = customize.observe(p.id, MediaType.LIVE).first().epgMatches
            val drop = have.filter { (k, v) -> k in keys && v.trim().lowercase() in builtinIds }.keys
            if (drop.isNotEmpty()) customize.update(p.id, MediaType.LIVE) { it.copy(epgMatches = it.epgMatches - drop) }
        }
    }

    suspend fun run(): Result {
        val all = db.sourceDao().getAllOnce()
        val allowedPortalIds = runCatching { settings?.portalBuiltinEpgSources?.firstOrNull() }.getOrNull().orEmpty()
        val portalIds = all.filter { it.type == tv.own.owntv.core.model.SourceType.STALKER && it.id !in allowedPortalIds }.map { it.id }
        runCatching { dropPortalLinks(portalIds, db.profileDao().getAllOnce()) }
        runCatching { syncProfiles() }
        val sourceIds = all.filter { it.id !in portalIds }.map { it.id }
        val profiles = db.profileDao().getAllOnce()
        if (sourceIds.isEmpty() || profiles.isEmpty()) return Result(0, 0)
        val cust = customize.observe(profiles.first().id, MediaType.LIVE).first()
        val outcome = matcher.run(cust, sourceIds, applyEmptyGuides = true)
        if (!outcome.hadCandidates) return Result(0, outcome.needingMatch)
        if (outcome.applied.isNotEmpty()) {
            val clean = outcome.applied.associate { it.first to it.second.trim().lowercase() }.filterValues { it.isNotEmpty() }
            // Every profile shares the playlists, so a match found once is a match for all of them.
            profiles.forEach { p -> customize.update(p.id, MediaType.LIVE) { it.copy(epgMatches = it.epgMatches + clean) } }
            runCatching { epgRepository.storeProgrammesForIdsFromCache(clean.values.toSet()) }
                .onFailure { Log.w(TAG, "cache fill after auto-match failed: ${it.message}") }
        }
        Log.i(TAG, "built-in auto-match applied=${outcome.applied.size} review=${outcome.review.size} needing=${outcome.needingMatch}")
        return Result(outcome.applied.size, (outcome.needingMatch - outcome.applied.size).coerceAtLeast(0))
    }

    private companion object { const val TAG = "BuiltinGuideMatcher" }
}
