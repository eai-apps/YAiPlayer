package tv.own.owntv.core.database.dao

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.settings.SettingsRepository

/**
 * The guest profile: "no registration, no profile to make" — playlists, portals and guides added to it
 * stay on this device, and leaving it deletes nothing. Made on first use and found again afterwards;
 * unlike a profile made in the picker it does NOT inherit the other profiles' sources.
 */
suspend fun ProfileDao.ensureGuest(settings: SettingsRepository, name: String): Long {
    val saved = settings.guestProfileId.first()
    if (saved >= 0 && getById(saved) != null) return saved
    val id = insert(ProfileEntity(name = name, avatarColor = 0, avatarId = 0))
    settings.setGuestProfileId(id)
    return id
}

/**
 * Returns the current profile id when it still exists, otherwise falls back to the first profile
 * row in the database.
 *
 * This keeps profile-scoped writes from crashing when the stored active profile id is stale or was
 * deleted before the caller ran.
 */
suspend fun ProfileDao.resolveExistingProfileId(preferredId: Long): Long? {
    if (preferredId < 0) return null
    getById(preferredId)?.let { return it.id }
    return getAllOnce().firstOrNull()?.id
}
