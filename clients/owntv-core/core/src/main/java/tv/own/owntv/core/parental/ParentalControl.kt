// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.parental

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.util.Pin

private val Context.parentalStore by preferencesDataStore(name = "parental_control")

/**
 * «Защита от детей» (owner 15.11–16.11): per playlist and per Stalker portal, the adult categories are shown,
 * hidden, or kept behind a PIN. Built on what Customize already has — hidden categories and PIN-locked
 * categories — so it works in every section (Live TV, films, series), in the guide and in search, on the TV
 * and on the phone alike, and a re-sync keeps it (category keys survive a re-sync).
 *
 * The PIN is the profile's Customize PIN: one PIN guards both. Until one is set, «1111» opens this screen and
 * is the PIN a lock starts with; «Сменить PIN» replaces it.
 *
 * Which categories are «adult» is [AdultCategoryClassifier] (names like «Для взрослых», «XXX», «18+»…).
 * Categories that arrive with a later sync are covered by [reapply], run when a profile is entered.
 */
class ParentalControl(
    private val context: Context,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val categoryDao: CategoryDao,
    private val sourceDao: SourceDao,
) {
    enum class Mode { SHOW, HIDE, LOCK }

    private fun key(profileId: Long, sourceId: Long) = stringPreferencesKey("mode_${profileId}_$sourceId")

    fun mode(profileId: Long, sourceId: Long): Flow<Mode> =
        context.parentalStore.data
            .map { prefs -> prefs[key(profileId, sourceId)]?.let { v -> Mode.entries.firstOrNull { it.name == v } } ?: Mode.SHOW }
            .distinctUntilChanged()

    /** Whether the profile already has its own PIN (otherwise [DEFAULT_PIN] opens the screen). */
    suspend fun hasOwnPin(profileId: Long): Boolean = settings.customizePin(profileId).first() != null

    /** The PIN check for the «Защита от детей» screen: the profile's PIN, or [DEFAULT_PIN] while none is set. */
    suspend fun verify(profileId: Long, pin: String): Boolean {
        val stored = settings.customizePin(profileId).first() ?: return pin.trim() == DEFAULT_PIN
        return Pin.verifyCustomize(pin.trim(), stored)
    }

    suspend fun changePin(profileId: Long, newPin: String) {
        require(newPin.trim().length >= MIN_PIN)
        settings.setCustomizePin(profileId, newPin.trim())
    }

    /** How many categories of [sourceId] count as adult. */
    suspend fun adultCount(sourceId: Long): Int =
        categoryDao.forSource(sourceId).count { AdultCategoryClassifier.isAdult(it.name) }

    suspend fun setMode(profileId: Long, sourceId: Long, mode: Mode) {
        context.parentalStore.edit { it[key(profileId, sourceId)] = mode.name }
        apply(profileId, sourceId, mode)
    }

    /** Applies every stored choice of [profileId] again — picks up adult categories a sync added since. */
    suspend fun reapply(profileId: Long) {
        if (profileId < 0) return
        val sources = sourceDao.observeForProfile(profileId).first()
        sources.forEach { src ->
            val mode = mode(profileId, src.id).first()
            if (mode != Mode.SHOW) apply(profileId, src.id, mode)
        }
    }

    /** [reapply] for every profile entered from now on; started once from the Application. */
    fun startAutoApply(scope: CoroutineScope) {
        scope.launch {
            settings.activeProfileId.distinctUntilChanged().collect { pid -> runCatching { reapply(pid) } }
        }
    }

    private suspend fun apply(profileId: Long, sourceId: Long, mode: Mode) {
        val adult = categoryDao.forSource(sourceId).filter { AdultCategoryClassifier.isAdult(it.name) }
        if (adult.isEmpty()) return
        // A lock means nothing without a PIN: the first lock starts with the default one.
        if (mode == Mode.LOCK && !hasOwnPin(profileId)) settings.setCustomizePin(profileId, DEFAULT_PIN)
        adult.groupBy { it.mediaType }.forEach { (type, cats) ->
            val keys = cats.map(CustomizeKeys::category).toSet()
            customize.update(profileId, type) { c ->
                c.copy(
                    hiddenCategories = if (mode == Mode.HIDE) c.hiddenCategories + keys else c.hiddenCategories - keys,
                    lockedCategories = if (mode == Mode.LOCK) c.lockedCategories + keys else c.lockedCategories - keys,
                )
            }
        }
    }

    companion object {
        const val DEFAULT_PIN = "1111"
        const val MIN_PIN = 4
    }
}
