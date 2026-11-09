// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.favoriteshub

import androidx.compose.runtime.Immutable
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/**
 * Filter mode for Favorites Hub:
 * - [ALL_PLAYLISTS]: Aggregated favorites from all playlists and portals belonging to the profile (default).
 * - [CURRENT_ONLY]: Favorites belonging only to the currently active playlist/portal.
 */
enum class FavoritesHubFilter {
    ALL_PLAYLISTS,
    CURRENT_ONLY,
}

/**
 * One favorite channel item in the Favorites Hub витрина.
 */
@Immutable
data class FavoriteChannelItem(
    val channel: ChannelEntity,
    val categoryName: String,
    val sourceName: String? = null,
    val hasMultipleSources: Boolean = false,
    val programme: EpgProgrammeEntity? = null,
    val progress: Float = 0f,
    val timeLeftMinutes: Long = 0L,
    val hasCatchup: Boolean = channel.catchup,
    val catchupDays: Int = channel.catchupDays,
)

/**
 * A horizontal row of favorite channels under a single category name.
 */
@Immutable
data class FavoritesHubCategoryRow(
    val categoryName: String,
    val channels: List<FavoriteChannelItem>,
)

/**
 * Complete UI state of the Favorites Hub.
 */
@Immutable
data class FavoritesHubState(
    val filter: FavoritesHubFilter = FavoritesHubFilter.ALL_PLAYLISTS,
    val allFavorites: List<FavoriteChannelItem> = emptyList(),
    val categoryRows: List<FavoritesHubCategoryRow> = emptyList(),
    val isLoading: Boolean = false,
) {
    val isEmpty: Boolean get() = allFavorites.isEmpty() && !isLoading
}
