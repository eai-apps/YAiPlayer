// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.favoriteshub

import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

object FavoritesHubGrouping {

    /**
     * Filters, sorts, and groups favorite channels into:
     * 1. Top row ("Все избранные"): all favorite channels in manual order or by channel number/name.
     * 2. Category rows: distinct category rows with same-named categories merged across playlists.
     */
    fun process(
        channels: List<ChannelEntity>,
        categoriesById: Map<Long, String>,
        sourceNamesById: Map<Long, String>,
        hiddenCategoryIds: Set<Long> = emptySet(),
        hiddenChannelIds: Set<Long> = emptySet(),
        sealedCategoryIds: Set<Long> = emptySet(),
        manualPositions: Map<Long, Int> = emptyMap(),
        categoryOrder: List<String> = emptyList(),
        uncategorizedLabel: String = "",
        programmesByChannelId: Map<Long, EpgProgrammeEntity> = emptyMap(),
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<List<FavoriteChannelItem>, List<FavoritesHubCategoryRow>> {
        // Exclude hidden channels and channels in hidden or parental-locked categories
        val visibleChannels = channels.filter { ch ->
            if (ch.id in hiddenChannelIds) return@filter false
            val catId = ch.categoryId
            if (catId != null && (catId in hiddenCategoryIds || catId in sealedCategoryIds)) {
                return@filter false
            }
            true
        }

        if (visibleChannels.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }

        // Determine if favorites span multiple sources/playlists
        val distinctSources = visibleChannels.map { it.sourceId }.distinct()
        val hasMultipleSources = distinctSources.size > 1

        val comparator = Comparator<ChannelEntity> { a, b ->
            val posA = manualPositions[a.id]
            val posB = manualPositions[b.id]
            if (posA != null && posB != null) return@Comparator posA.compareTo(posB)
            if (posA != null) return@Comparator -1
            if (posB != null) return@Comparator 1

            val numA = a.number
            val numB = b.number
            if (numA != null && numB != null && numA != numB) return@Comparator numA.compareTo(numB)
            if (numA != null && numB == null) return@Comparator -1
            if (numA == null && numB != null) return@Comparator 1

            if (a.sortOrder != b.sortOrder) return@Comparator a.sortOrder.compareTo(b.sortOrder)
            a.name.compareTo(b.name, ignoreCase = true)
        }

        // Top row: all visible favorite channels
        val sortedAll = visibleChannels.sortedWith(comparator)

        fun createItem(ch: ChannelEntity): FavoriteChannelItem {
            val catName = ch.categoryId?.let { categoriesById[it] }?.takeIf { it.isNotBlank() } ?: uncategorizedLabel
            val srcName = sourceNamesById[ch.sourceId]
            val prog = programmesByChannelId[ch.id]
            val progress = if (prog != null && prog.stopMs > prog.startMs) {
                ((nowMs - prog.startMs).toFloat() / (prog.stopMs - prog.startMs)).coerceIn(0f, 1f)
            } else 0f
            val leftMin = if (prog != null && prog.stopMs > nowMs) {
                ((prog.stopMs - nowMs) + 59_999L) / 60_000L
            } else 0L

            return FavoriteChannelItem(
                channel = ch,
                categoryName = catName,
                sourceName = srcName,
                hasMultipleSources = hasMultipleSources,
                programme = prog,
                progress = progress,
                timeLeftMinutes = leftMin,
                hasCatchup = ch.catchup,
                catchupDays = ch.catchupDays,
            )
        }

        val allItems = sortedAll.map(::createItem)

        // Group into category rows, unifying same-named categories (case-insensitively)
        val categoryOrderNormalized = categoryOrder.map { it.trim().lowercase() }
        val groupedByNormalized = LinkedHashMap<String, MutableList<FavoriteChannelItem>>()
        val displayCategoryNames = LinkedHashMap<String, String>()

        for (item in allItems) {
            val key = item.categoryName.trim().lowercase()
            displayCategoryNames.putIfAbsent(key, item.categoryName.trim())
            groupedByNormalized.getOrPut(key) { mutableListOf() }.add(item)
        }

        val categoryRows = groupedByNormalized.entries
            .map { (key, items) ->
                val orderIndex = categoryOrderNormalized.indexOf(key)
                val rank = if (orderIndex >= 0) orderIndex else Int.MAX_VALUE - 1
                val isUncategorized = key == uncategorizedLabel.trim().lowercase()
                Triple(if (isUncategorized) Int.MAX_VALUE else rank, displayCategoryNames[key] ?: uncategorizedLabel, items)
            }
            .sortedWith(compareBy({ it.first }, { it.second.lowercase() }))
            .map { (_, displayName, items) ->
                FavoritesHubCategoryRow(
                    categoryName = displayName,
                    channels = items,
                )
            }

        return Pair(allItems, categoryRows)
    }
}
