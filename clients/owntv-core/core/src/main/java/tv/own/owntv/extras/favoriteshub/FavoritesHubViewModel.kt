// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.favoriteshub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.SettingsRepository

class FavoritesHubViewModel(
    private val feed: FavoritesHubFeed,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesHubState(isLoading = true))
    val state: StateFlow<FavoritesHubState> = _state.asStateFlow()

    private var currentFilter = FavoritesHubFilter.ALL_PLAYLISTS
    private var activeProfileId: Long = -1L
    private var currentSourceId: Long? = null

    init {
        viewModelScope.launch {
            activeProfileId = settings.activeProfileId.first()
            currentSourceId = settings.activePlaylistId.first().takeIf { it > 0 }
            val savedFilterAll = settings.favoritesHubAllPlaylists.first()
            currentFilter = if (savedFilterAll) FavoritesHubFilter.ALL_PLAYLISTS else FavoritesHubFilter.CURRENT_ONLY
            refresh()
        }
    }

    fun setFilter(filter: FavoritesHubFilter, uncategorizedLabel: String = "") {
        if (currentFilter == filter) return
        currentFilter = filter
        viewModelScope.launch {
            settings.setFavoritesHubAllPlaylists(filter == FavoritesHubFilter.ALL_PLAYLISTS)
            refresh(uncategorizedLabel)
        }
    }

    fun refresh(uncategorizedLabel: String = "") {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            activeProfileId = settings.activeProfileId.first()
            currentSourceId = settings.activePlaylistId.first().takeIf { it > 0 }
            val newState = feed.loadState(
                profileId = activeProfileId,
                currentSourceId = currentSourceId,
                filter = currentFilter,
                uncategorizedLabel = uncategorizedLabel,
            )
            _state.value = newState
        }
    }
}
