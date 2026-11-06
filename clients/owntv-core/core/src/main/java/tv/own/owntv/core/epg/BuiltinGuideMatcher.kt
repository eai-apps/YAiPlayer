package tv.own.owntv.core.epg

import android.util.Log
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.EpgRepository

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

    suspend fun run(): Result {
        runCatching { syncProfiles() }
        val sourceIds = db.sourceDao().getAllOnce().map { it.id }
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
