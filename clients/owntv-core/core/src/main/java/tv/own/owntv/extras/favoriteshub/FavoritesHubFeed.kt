// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.favoriteshub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.EpgShift
import tv.own.owntv.core.live.epgKeyOf
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository

class FavoritesHubFeed(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val sourceDao: SourceDao,
    private val epgDao: EpgDao,
    private val customize: CustomizationStore,
    private val profileDao: ProfileDao,
    private val settings: SettingsRepository,
) {

    suspend fun loadState(
        profileId: Long,
        currentSourceId: Long?,
        filter: FavoritesHubFilter = FavoritesHubFilter.ALL_PLAYLISTS,
        uncategorizedLabel: String = "",
        nowMs: Long = System.currentTimeMillis(),
    ): FavoritesHubState = withContext(Dispatchers.IO) {
        val allProfileSources = sourceDao.sourceIdsForProfile(profileId).toList()
        if (allProfileSources.isEmpty()) {
            return@withContext FavoritesHubState(filter = filter, isLoading = false)
        }

        val relevantSourceIds = when {
            filter == FavoritesHubFilter.CURRENT_ONLY && currentSourceId != null -> listOf(currentSourceId)
            filter == FavoritesHubFilter.CURRENT_ONLY -> {
                val active = activeSourceIds(settings, sourceDao, profileId, MediaType.LIVE)
                if (active.isNotEmpty()) active else allProfileSources.take(1)
            }
            else -> allProfileSources
        }

        coroutineScope {
            val channelsAsync = async {
                channelDao.snapshotFavoritesManual(
                    profileId = profileId,
                    contextKey = ContentOrderEntity.FAV_CONTEXT,
                    sourceIds = relevantSourceIds,
                    limit = 5000,
                )
            }
            val categoriesAsync = async {
                categoryDao.observe(allProfileSources, MediaType.LIVE).first()
            }
            val sourcesAsync = async {
                sourceDao.getAllOnce()
            }
            val liveCustomAsync = async {
                customize.observeVisible(profileId, MediaType.LIVE).first()
            }
            val isKidsProfile = profileDao.getById(profileId)?.isKids == true

            val channels = channelsAsync.await()
            if (channels.isEmpty()) {
                return@coroutineScope FavoritesHubState(filter = filter, isLoading = false)
            }

            val allCategories = categoriesAsync.await()
            val categoriesById = allCategories.associate { it.id to it.name }
            val categoryOrder = allCategories.map { it.name }.distinct()

            val sources = sourcesAsync.await()
            val sourceNamesById = sources.associate { it.id to it.name }

            val liveCustom = liveCustomAsync.await()
            val hiddenCatIds = if (liveCustom.hiddenCategories.isEmpty() && !isKidsProfile) {
                emptySet()
            } else {
                AdultCategoryClassifier.hiddenCategoryIds(
                    allCategories,
                    liveCustom.hiddenCategories,
                    isKidsProfile,
                )
            }

            // EPG on-air programmes
            val globalShiftMinutes = settings.epgOffsetMinutes.first()
            val keyed = channels.mapNotNull { ch ->
                epgKeyOf(ch, liveCustom)?.let { key -> Triple(ch, key, EpgShift.minutesFor(liveCustom, ch, globalShiftMinutes)) }
            }

            val programmesByChannelId = HashMap<Long, EpgProgrammeEntity>()
            for ((shift, group) in keyed.groupBy { it.third }) {
                val shiftedNow = EpgShift.toStored(nowMs, shift)
                val channelByEpgKey = group.groupBy({ it.second }, { it.first })
                val keys = channelByEpgKey.keys.toList()
                for (chunk in keys.chunked(200)) {
                    val programmes = epgDao.programmesOnAirForChannels(chunk, shiftedNow)
                    val shifted = if (shift == 0) programmes else programmes.map { EpgShift.apply(it, shift) }
                    for (p in shifted) {
                        channelByEpgKey[p.epgChannelId]?.forEach { ch ->
                            programmesByChannelId[ch.id] = p
                        }
                    }
                }
            }

            val (allFavorites, categoryRows) = FavoritesHubGrouping.process(
                channels = channels,
                categoriesById = categoriesById,
                sourceNamesById = sourceNamesById,
                hiddenCategoryIds = hiddenCatIds,
                hiddenChannelIds = channels.filter { CustomizeKeys.channel(it) in liveCustom.hiddenItems }.mapTo(HashSet()) { it.id },
                sealedCategoryIds = liveCustom.sealedCategoryIds,
                manualPositions = emptyMap(), // channels from snapshotFavoritesManual already in manual order
                categoryOrder = categoryOrder,
                uncategorizedLabel = uncategorizedLabel,
                programmesByChannelId = programmesByChannelId,
                nowMs = nowMs,
            )

            FavoritesHubState(
                filter = filter,
                allFavorites = allFavorites,
                categoryRows = categoryRows,
                isLoading = false,
            )
        }
    }
}
