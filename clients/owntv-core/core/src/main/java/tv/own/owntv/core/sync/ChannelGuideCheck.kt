// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import android.content.Context
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.core.R
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.EpgSyncScheduler
import tv.own.owntv.core.sync.work.EpgSyncState

/**
 * «Проверить телепрограмму» in a channel's long-press menu (owner 13.11): says whether this channel has a
 * guide and under which id; when it has none, reloads the guide the channel's own playlist names (never the
 * additional sources) and says what came of it. A whole XMLTV file is the smallest unit a playlist guide
 * can be loaded in, so «only this channel» is the answer, not the download.
 */
class ChannelGuideCheck(
    private val context: Context,
    private val epgDao: EpgDao,
    private val sourceDao: SourceDao,
    private val epgSourceStore: EpgSourceStore,
    private val epgRepository: EpgRepository,
    private val epgSyncScheduler: EpgSyncScheduler,
    private val customizations: CustomizationStore,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun check(channel: ChannelEntity) {
        scope.launch {
            runCatching { run(channel) }.onFailure {
                android.util.Log.w(TAG, "guide check failed for ${channel.id}", it)
                say(context.getString(R.string.guide_check_failed))
            }
        }
    }

    private suspend fun run(channel: ChannelEntity) {
        val key = keyOf(channel)
        if (key != null) {
            val count = epgDao.countUpcomingForChannel(key, System.currentTimeMillis())
            if (count > 0) {
                say(context.resources.getQuantityString(R.plurals.guide_check_found, count, channel.name, count, key))
                return
            }
        }
        val source = sourceDao.getById(channel.sourceId)
        val urls = source?.let { epgRepository.guideUrls(it).toSet() }.orEmpty()
        val stalkerUrl = source?.takeIf { it.type == SourceType.STALKER }?.let { EpgRepository.stalkerGuideUrl(it.id) }
        val own = epgSourceStore.getAll().filter { !it.builtin && (it.url in urls || it.url == stalkerUrl) }
        if (own.isEmpty()) {
            say(context.getString(if (key == null) R.string.guide_check_no_id else R.string.guide_check_no_playlist_guide, channel.name))
            return
        }
        say(context.getString(R.string.guide_check_loading, channel.name))
        own.forEach { src ->
            epgSyncScheduler.enqueueSync(src.id, "channel_check", epgDao.countForSources(listOf(src.id)), ExistingWorkPolicy.KEEP)
        }
        // Wait for the loads to finish (a big XMLTV takes minutes on a box), then look again.
        withTimeoutOrNull(WAIT_MS) {
            delay(START_GRACE_MS)
            own.forEach { src -> epgSyncScheduler.observeSync(src.id).first { it is EpgSyncState.Idle } }
        } ?: return say(context.getString(R.string.guide_check_still_loading, channel.name))
        val after = keyOf(channel)?.let { epgDao.countUpcomingForChannel(it, System.currentTimeMillis()) } ?: 0
        say(
            if (after > 0) context.resources.getQuantityString(R.plurals.guide_check_found, after, channel.name, after, keyOf(channel).orEmpty())
            else context.getString(if (keyOf(channel) == null) R.string.guide_check_no_id else R.string.guide_check_none_after, channel.name),
        )
    }

    /** The id the guide is read under: a manual match wins over the playlist's own (as the guide reads it). */
    private suspend fun keyOf(channel: ChannelEntity): String? {
        val profile = settings.activeProfileId.first()
        val manual = if (profile < 0) null else runCatching {
            customizations.observe(profile, MediaType.LIVE).first().epgMatchResolver.epgIdFor(channel)
        }.getOrNull()
        return (manual ?: channel.epgChannelId)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun say(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG = "ChannelGuideCheck"
        const val WAIT_MS = 6 * 60_000L
        const val START_GRACE_MS = 1_500L
    }
}
