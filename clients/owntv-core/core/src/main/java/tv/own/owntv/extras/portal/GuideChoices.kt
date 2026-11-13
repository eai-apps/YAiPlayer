// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.portal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.R
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.BuiltinGuide

/**
 * «Только телепрограмму» → which guide (owner 13.11): the playlist's own / the provider's (portal), additional
 * source 1, 2 or 3, or all at once. Shared by the TV popup and the phone sheet.
 */
enum class GuideChoice(val labelRes: Int, val feed: Int) {
    OWN(R.string.guide_choice_own, 0),
    FEED_1(R.string.guide_choice_feed, 1),
    FEED_2(R.string.guide_choice_feed, 2),
    FEED_3(R.string.guide_choice_feed, 3),
    ALL(R.string.guide_choice_all, 0);

    /** The portal says «from the provider», a playlist «from the playlist». */
    fun ownLabel(source: SourceEntity?) = if (source?.type == SourceType.STALKER) R.string.guide_choice_provider else R.string.guide_choice_own

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Runs an additional source. A portal takes the reserve guide only with its «additional guide» switch on,
         * so choosing a source here turns that switch on for this portal. Sources 2 and 3 are switched on first.
         */
        fun runFeeds(source: SourceEntity?, numbers: List<Int>) {
            scope.launch {
                val koin = org.koin.core.context.GlobalContext.get()
                if (source?.type == SourceType.STALKER) runCatching { koin.get<SettingsRepository>().setPortalBuiltinEpg(source.id, true) }
                val guide = BuiltinGuide.fromKoin()
                runCatching { guide.ensure() }
                val feeds = guide.feeds().first()
                for (n in numbers) {
                    val feed = feeds.firstOrNull { it.number == n } ?: continue
                    if (n == 1) guide.refreshFeed(feed.id) else guide.setFeedEnabled(feed, true)
                }
            }
        }
    }
}
