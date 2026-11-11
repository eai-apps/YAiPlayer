// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the station list shows: a genre, the site's collections, the favourites, or a search. */
sealed interface RadioMode {
    data class Genre(val genre: RadioGenre) : RadioMode
    data object Collections : RadioMode
    data object Favorites : RadioMode
    data class Search(val query: String) : RadioMode
    /** Sign-in to the YAiPlay Radio account: favourites shared with the site and the user's other devices. */
    data object Account : RadioMode
}

/** The radio screen's state, shared by the TV and the phone. */
class RadioViewModel(app: Application) : AndroidViewModel(app) {
    val repository = RadioRepository(app)
    val player = RadioPlayer(app)

    private val _mode = MutableStateFlow<RadioMode>(RadioMode.Genre(RadioGenres.all.first()))
    val mode: StateFlow<RadioMode> = _mode.asStateFlow()
    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations: StateFlow<List<RadioStation>> = _stations.asStateFlow()
    private val _collections = MutableStateFlow<List<RadioCollection>>(emptyList())
    val collections: StateFlow<List<RadioCollection>> = _collections.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()
    private var job: Job? = null
    private var syncJob: Job? = null

    /** Signed-in login, or null. */
    val account: StateFlow<String?> get() = repository.account
    private val _busy = MutableStateFlow(false)
    /** A sign-in or a manual sync is running. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _failure = MutableStateFlow<RadioSync.Failure?>(null)
    /** Why the last sign-in or manual sync failed; null = it did not. */
    val failure: StateFlow<RadioSync.Failure?> = _failure.asStateFlow()

    init {
        load(_mode.value)
        // Signed in on this device: take what changed on the others.
        if (repository.account.value != null) viewModelScope.launch { repository.syncNow() }
        // A sync may change the favourites while they are on screen.
        viewModelScope.launch {
            repository.favorites.collect { if (_mode.value == RadioMode.Favorites) _stations.value = it }
        }
    }

    fun signIn(login: String, password: String, create: Boolean) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _failure.value = repository.signIn(login, password, create)
            _busy.value = false
        }
    }

    fun signOut() {
        _failure.value = null
        repository.signOut()
    }

    fun syncNow() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _failure.value = if (repository.syncNow()) null else RadioSync.Failure.NETWORK
            _busy.value = false
        }
    }

    fun select(mode: RadioMode) {
        _mode.value = mode
        load(mode)
    }

    fun search(query: String) {
        job?.cancel()
        if (query.isBlank()) {
            select(RadioMode.Genre(RadioGenres.all.first()))
            return
        }
        job = viewModelScope.launch {
            delay(400) // typing: ask once the user pauses
            _mode.value = RadioMode.Search(query.trim())
            fetch { repository.search(null, query.trim()) }
        }
    }

    fun retry() = load(_mode.value)

    private fun load(mode: RadioMode) {
        job?.cancel()
        job = viewModelScope.launch {
            when (mode) {
                is RadioMode.Genre -> fetch { repository.search(mode.genre.tag) }
                is RadioMode.Search -> fetch { repository.search(null, mode.query) }
                RadioMode.Account -> {
                    _loading.value = false
                    _failed.value = false
                    _failure.value = null
                }
                RadioMode.Favorites -> {
                    _failed.value = false
                    _stations.value = repository.favorites.value
                }
                RadioMode.Collections -> {
                    _loading.value = true
                    _failed.value = false
                    val c = repository.collections()
                    _collections.value = c
                    _stations.value = c.flatMap { it.stations }.distinctBy { it.id }
                    _failed.value = c.isEmpty()
                    _loading.value = false
                }
            }
        }
    }

    private suspend fun fetch(block: suspend () -> List<RadioStation>) {
        _loading.value = true
        _failed.value = false
        val result = runCatching { block() }
        _stations.value = result.getOrDefault(emptyList())
        _failed.value = result.isFailure
        _loading.value = false
    }

    fun toggleFavorite(s: RadioStation) {
        repository.toggleFavorite(s)
        if (_mode.value == RadioMode.Favorites) _stations.value = repository.favorites.value
        // Like the site: send changes after a short pause, so several taps go as one.
        if (repository.account.value != null) {
            syncJob?.cancel()
            syncJob = viewModelScope.launch {
                delay(2_000)
                repository.syncNow()
            }
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
