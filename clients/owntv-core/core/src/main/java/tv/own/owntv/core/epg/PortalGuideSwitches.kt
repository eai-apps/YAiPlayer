// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.epg

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * The two portal switches in Settings → Guide (owner 12.11), for every portal at once:
 *
 * - **Portal guide** — the portal's own guide (`importPortalEpg` on each portal). On by default.
 * - **Additional guide for the portal** — the reserve guide (the light feed) may fill portal channels the
 *   portal leaves without a programme. Off by default: the portal's guide is the one that is used.
 */
class PortalGuideSwitches(private val sourceDao: SourceDao, private val settings: SettingsRepository) {
    private val portals: Flow<List<tv.own.owntv.core.database.entity.SourceEntity>> =
        sourceDao.observeAll().map { all -> all.filter { it.type == SourceType.STALKER } }

    val portalGuide: Flow<Boolean> = portals.map { list -> list.isEmpty() || list.any { it.importPortalEpg } }

    val additionalGuide: Flow<Boolean> = combine(portals, settings.portalBuiltinEpgSources) { list, allowed ->
        list.any { it.id in allowed }
    }

    suspend fun setPortalGuide(on: Boolean) {
        sourceDao.getAllOnce().filter { it.type == SourceType.STALKER && it.importPortalEpg != on }
            .forEach { sourceDao.update(it.copy(importPortalEpg = on)) }
    }

    suspend fun setAdditionalGuide(on: Boolean) {
        sourceDao.getAllOnce().filter { it.type == SourceType.STALKER }
            .forEach { settings.setPortalBuiltinEpg(it.id, on) }
    }

    companion object {
        fun fromKoin(): PortalGuideSwitches =
            org.koin.core.context.GlobalContext.get().let { PortalGuideSwitches(it.get(), it.get()) }
    }
}
