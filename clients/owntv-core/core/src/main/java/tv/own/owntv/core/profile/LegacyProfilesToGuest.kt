// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.profile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.R
import tv.own.owntv.core.account.AccountService
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.ensureGuest
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Owner 17.11 (v4.0.1): after the update to 4.0 a friend's box still showed its old local profile «успех» on
 * «Кто смотрит?» — it could only be hidden, not deleted (deleting the only profile is refused) — and it kept
 * playing in the background after the account sign-in. Since 4.0 the chooser has the account, Guest and Radio:
 * profiles made on this device before 4.0 are folded into Guest ONCE — their playlists / portals, favourites,
 * history and watch progress move to Guest, then the old profile row is removed. Account profiles and Guest
 * itself are never touched. Runs once per install; nothing is deleted from the provider side.
 */
class LegacyProfilesToGuest(
    private val context: Context,
    private val db: OwnTVDatabase,
    private val profileDao: ProfileDao,
    private val settings: SettingsRepository,
    private val manager: ProfileManager,
) {
    suspend fun runOnce() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return@withContext
        val account = runCatching {
            org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<AccountService>()?.accountProfileIds()
        }.getOrNull() ?: emptySet()
        val savedGuest = settings.guestProfileId.first()
        val legacy = profileDao.getAllOnce().filter { it.id != savedGuest && it.id !in account }
        if (legacy.isEmpty()) {
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return@withContext
        }
        val guestId = profileDao.ensureGuest(settings, context.getString(R.string.profiles_guest_name))
        val active = settings.activeProfileId.first()
        val sql = db.openHelper.writableDatabase
        db.runInTransaction {
            legacy.forEach { p ->
                // OR IGNORE: a row Guest already has (same playlist / same favourite) stays as it is;
                // the old profile's duplicate goes away with the profile itself.
                MOVED_TABLES.forEach { table ->
                    sql.execSQL("UPDATE OR IGNORE $table SET profileId = ? WHERE profileId = ?", arrayOf<Any>(guestId, p.id))
                }
            }
        }
        legacy.forEach { p -> runCatching { manager.delete(p) } }
        if (active in legacy.map { it.id }) settings.setActiveProfile(guestId)
        android.util.Log.i(TAG, "${legacy.size} pre-4.0 local profile(s) moved into Guest ($guestId)")
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    private companion object {
        const val TAG = "LegacyProfiles"
        const val PREFS = "yai_migrations"
        const val KEY_DONE = "legacy_profiles_to_guest_v1"
        // Per-profile tables whose rows are worth keeping. Everything else per-profile (ordering, prefs,
        // tombstones…) is rebuilt on its own and goes away with the old profile.
        val MOVED_TABLES = listOf("profile_source", "favorites", "watch_history", "playback_progress")
    }
}
