package tv.own.owntv.core.sync

import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.EpgSyncScheduler

/**
 * The app's own hidden guides: three XMLTV feeds, tried in order per channel, with
 * channel logos. It is registered once after a playlist syncs, sits LAST in the guide priority (so a
 * guide the user adds, or a portal's own, wins), is hidden from the Settings → EPG list and can be
 * switched off. Stalker portals get it too, as a fallback for channels their own guide lacks.
 */
class BuiltinGuide(
    private val store: EpgSourceStore,
    private val settings: SettingsRepository,
    private val scheduler: EpgSyncScheduler,
) {
    suspend fun ensure() {
        FEEDS.forEachIndexed { i, url ->
            val created = store.ensureBuiltin("$NAME ${i + 1}", url) ?: return@forEachIndexed
            settings.setEpgUseLogos(created.id, true)
            settings.setEpgAutoRefresh(created.id, EpgRefresh(EpgAutoRefresh.HOURS_24))
            scheduler.enqueueSync(created.id, "builtin")
        }
    }

    companion object {
        const val NAME = "Built-in guide"

        /** In fallback order: a channel missing from one feed is looked up in the next. */
        val FEEDS = listOf(
            "https://iptvx.one/EPG_LITE",
            "http://epg.one/epg.xml.gz",
            "http://miptv.in.ua/xmlt/mlist_epg.xml.gz",
        )
    }
}
