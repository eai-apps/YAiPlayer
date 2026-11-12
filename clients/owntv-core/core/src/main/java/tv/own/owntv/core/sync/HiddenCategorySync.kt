// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import kotlinx.coroutines.flow.first
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * "Update hidden categories" (Settings → sources, owner 12.11). Off = a category the user hid is left as it is on
 * a refresh: its films and series pages are not walked again and its channels get no new guide. Its old content
 * stays, so un-hiding it shows it at once and the next refresh brings it up to date. Portal and playlist alike.
 *
 * A source can sit in several profiles: a category is frozen only when EVERY profile that uses the source hides it
 * — another profile still showing it needs it fresh.
 */
object HiddenCategorySync {
    /** The provider category ids (the key's tail) hidden in all of [hiddenPerProfile] for [sourceId]. */
    fun frozenTails(sourceId: Long, hiddenPerProfile: List<Set<String>>): Set<String> {
        if (hiddenPerProfile.isEmpty()) return emptySet()
        val prefix = "$sourceId:"
        return hiddenPerProfile
            .map { keys -> keys.filter { it.startsWith(prefix) }.map(CustomizeKeys::tailOf).toSet() }
            .reduce { acc, next -> acc intersect next }
    }

    /** Of [categories], the ids of those whose key tail is in [tails]. */
    fun frozenIds(categories: List<CategoryEntity>, tails: Set<String>): Set<Long> =
        if (tails.isEmpty()) emptySet()
        else categories.filter { (it.remoteId ?: it.name) in tails }.map { it.id }.toSet()

    /** The frozen provider category ids for [sourceId] and [type]; empty when the setting says update them. */
    suspend fun frozenRemoteIds(
        settings: SettingsRepository,
        sourceDao: SourceDao,
        customize: CustomizationStore,
        sourceId: Long,
        type: MediaType,
    ): Set<String> {
        if (settings.syncHiddenCategories.first()) return emptySet()
        val profiles = sourceDao.profileIdsForSource(sourceId)
        return frozenTails(sourceId, profiles.map { customize.observe(it, type).first().hiddenCategories })
    }

    /** The same as category row ids (what channels and films point at). */
    suspend fun frozenCategoryIds(
        settings: SettingsRepository,
        sourceDao: SourceDao,
        categoryDao: CategoryDao,
        customize: CustomizationStore,
        sourceId: Long,
        type: MediaType,
    ): Set<Long> {
        val tails = frozenRemoteIds(settings, sourceDao, customize, sourceId, type)
        if (tails.isEmpty()) return emptySet()
        return frozenIds(categoryDao.forSource(sourceId).filter { it.mediaType == type }, tails)
    }
}
