package tv.own.owntv.mobile.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.settings.SettingsRepository

/** Who is watching: the list, the one in use, and the four things that can be done to a profile. */
class ProfilesViewModel(
    private val profileDao: ProfileDao,
    private val settings: SettingsRepository,
    private val manager: ProfileManager,
    private val context: android.content.Context,
) : ViewModel() {

    /** Whether [profileId] may download (the profile editor's Allow downloads). */
    fun downloadsAllowed(profileId: Long): kotlinx.coroutines.flow.Flow<Boolean> = settings.downloadsAllowed(profileId)

    fun setDownloadsAllowed(profileId: Long, allowed: Boolean) {
        viewModelScope.launch { settings.setDownloadsAllowed(profileId, allowed) }
    }

    /**
     * Null until Room has answered, never an empty list standing in for "not loaded yet". The gate
     * decides whether to show itself from this, and an empty answer arriving early would let the app
     * walk straight past a PIN.
     */
    val profiles: StateFlow<List<ProfileEntity>?> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The active profile, or null while the preference is still being read.
     *
     * Nullable on purpose, exactly as the television's is: `-1` already means "no profile chosen",
     * so a `-1` placeholder while loading makes those two states indistinguishable — and the shell
     * has to tell them apart, or the frame before the answer arrives looks like a fresh install and
     * flashes "Who is watching?" on the way past.
     */
    val activeProfileId: StateFlow<Long?> = settings.activeProfileId
        .map<Long, Long?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun verifyPin(profile: ProfileEntity, pin: String): Boolean = manager.verifyPin(profile, pin)

    /** The guest profile id, -1 until one has been made. */
    val guestProfileId: StateFlow<Long> = settings.guestProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)

    /** Enter as guest: made on first use (no sources of other profiles), then it is the active profile. */
    fun enterGuest(onEntered: (ProfileEntity) -> Unit) {
        viewModelScope.launch {
            val id = manager.ensureGuest(context.getString(tv.own.owntv.mobile.R.string.profiles_guest_name))
            val guest = profileDao.getById(id) ?: return@launch
            manager.switchTo(id)
            onEntered(guest)
        }
    }

    /** Make [profile] the one in use; [onSwitched] runs once the preference has actually committed. */
    fun switchTo(profile: ProfileEntity, onSwitched: () -> Unit = {}) {
        viewModelScope.launch {
            manager.switchTo(profile.id)
            onSwitched()
        }
    }

    /**
     * [picture], when the user chose one, is stored once the profile exists — before that there is
     * no id to store it under.
     */
    fun create(name: String, avatarId: Int, isKids: Boolean, pin: String?, fallbackName: String, picture: android.net.Uri? = null, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = manager.create(name, avatarId, isKids, pin, fallbackName)
            if (picture != null) setPictureNow(id, picture)
            onCreated(id)
        }
    }

    /**
     * [pin]: null keeps the existing PIN, "" removes it. Edits the row as it is now, not [profile]:
     * a picture chosen while the sheet was open has already been written, and the sheet's copy would
     * put the old one back.
     */
    fun edit(profile: ProfileEntity, name: String, avatarId: Int, isKids: Boolean, pin: String?) {
        viewModelScope.launch { manager.edit(profileDao.getById(profile.id) ?: profile, name, avatarId, isKids, pin) }
    }

    /**
     * Give [profile] the picture at [uri] — whatever the photo picker handed back. Core copies and
     * scales it; [onResult] says whether it was a readable image, so the sheet can tell the user
     * rather than appearing to have ignored the tap.
     */
    fun setPicture(profile: ProfileEntity, uri: android.net.Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(setPictureNow(profile.id, uri)) }
    }

    // Read fresh by id rather than taking a caller's entity: the picture is written as a copy of the
    // whole row, so a stale one would put back whatever the sheet has just changed.
    private suspend fun setPictureNow(profileId: Long, uri: android.net.Uri): Boolean = runCatching {
        val profile = profileDao.getById(profileId) ?: return@runCatching false
        context.contentResolver.openInputStream(uri)?.use { manager.setCustomAvatar(profile, it) } ?: false
    }.getOrDefault(false)

    /** Drop [profile]'s picture; the drawn tile it already had comes back. */
    fun clearPicture(profile: ProfileEntity) {
        viewModelScope.launch { manager.clearCustomAvatar(profile) }
    }

    fun delete(profile: ProfileEntity) {
        viewModelScope.launch { manager.delete(profile) }
    }

    val hiddenProfileIds: StateFlow<Set<Long>> = settings.hiddenProfileIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun hideProfile(id: Long) {
        viewModelScope.launch { settings.hideProfile(id) }
    }

    fun showProfile(id: Long) {
        viewModelScope.launch { settings.showProfile(id) }
    }

    fun setProfileHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch { settings.setProfileHidden(id, hidden) }
    }
}
