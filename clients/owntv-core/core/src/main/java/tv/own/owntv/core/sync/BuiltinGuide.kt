package tv.own.owntv.core.sync

import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.EpgSyncScheduler

/**
 * The app's own hidden guide for M3U/Xtream playlists: a lightweight XMLTV feed (no descriptions) with
 * channel logos. It is registered once after a playlist syncs, sits LAST in the guide priority (so a
 * guide the user adds, or a portal's own, wins), is hidden from the Settings → EPG list and can be
 * switched off. Stalker portals are never given it: they carry their own guide.
 */
class BuiltinGuide(
    private val store: EpgSourceStore,
    private val settings: SettingsRepository,
    private val scheduler: EpgSyncScheduler,
) {
    suspend fun ensure() {
        val created = store.ensureBuiltin(NAME, URL) ?: return
        settings.setEpgUseLogos(created.id, true)
        settings.setEpgAutoRefresh(created.id, EpgRefresh(EpgAutoRefresh.HOURS_24))
        scheduler.enqueueSync(created.id, "builtin")
    }

    companion object {
        const val NAME = "Built-in guide"
        const val URL = "https://iptvx.one/EPG_LITE"
    }
}
