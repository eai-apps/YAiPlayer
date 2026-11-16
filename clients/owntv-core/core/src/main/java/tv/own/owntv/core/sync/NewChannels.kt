// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.CustomCategoryMemberEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import java.io.File

/**
 * «Новые каналы» (owner 16.11): the playlist or portal is re-read quietly every morning; channels the provider
 * added since the last read are announced once («Провайдер добавил N новых каналов») and kept for a week in a
 * category «Новые каналы» of Live TV. Built on custom categories, so the category shows, counts, sorts and
 * plays like any other, on the TV and on the phone.
 *
 * A channel is known by its customization key, which survives a re-sync. The first read of a source only
 * learns its channels; a read where most channels look new (another playlist behind the same entry) announces
 * nothing.
 */
class NewChannels(
    private val context: Context,
    private val db: OwnTVDatabase,
    private val customize: CustomizationStore,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val _notice = MutableStateFlow<Int?>(null)

    /** New channels not yet announced, or null. */
    val notice: StateFlow<Int?> = _notice

    fun dismiss() { _notice.value = null }

    /** The «Новые каналы» category of [profileId], when there is one. */
    fun categoryId(profileId: Long): String? = prefs.getString(catKey(profileId), null)

    /** After [source] synced: learn its channels, record the ones that are new, rebuild the category. */
    suspend fun afterSync(source: SourceEntity) {
        val now = System.currentTimeMillis()
        val channels = db.channelDao().allForSources(listOf(source.id), Int.MAX_VALUE)
        if (channels.isEmpty()) return
        val keys = channels.mapTo(LinkedHashSet()) { CustomizeKeys.channel(it) }
        val knownFile = File(dir(), "known_${source.id}.txt")
        val known = if (knownFile.exists()) knownFile.readLines().toHashSet() else null
        knownFile.writeText(keys.joinToString("\n"))

        val added = loadAdded(source.id).filter { (k, at) -> k in keys && now - at < KEEP_MS }.toMutableMap()
        var fresh = 0
        if (known != null && known.isNotEmpty()) {
            val newKeys = keys.filter { it !in known }
            // Most of the list «new» means another playlist behind the same entry, not new channels.
            if (newKeys.size <= keys.size / 2) {
                newKeys.forEach { if (added.putIfAbsent(it, now) == null) fresh++ }
            }
        }
        saveAdded(source.id, added)

        for (pid in db.sourceDao().profileIdsForSource(source.id)) rebuild(pid)
        if (fresh > 0) _notice.value = (_notice.value ?: 0) + fresh
    }

    /** The category of [profileId] = this week's new channels of its sources; removed when there are none. */
    private suspend fun rebuild(profileId: Long) {
        val sources = db.sourceDao().sourceIdsForProfile(profileId)
        val keys = sources.flatMap { loadAdded(it).keys }.toSet()
        val ids = if (keys.isEmpty()) emptyList()
        else db.channelDao().allForSources(sources, Int.MAX_VALUE).filter { CustomizeKeys.channel(it) in keys }.map { it.id }
        val dao = db.customCategoryDao()
        val existing = categoryId(profileId)?.takeIf { id ->
            customize.observe(profileId, MediaType.LIVE).first().customCategories.any { it.id == id }
        }
        if (ids.isEmpty()) {
            if (existing != null) {
                dao.clearContext(profileId, MediaType.LIVE, existing)
                customize.deleteCustomCategory(profileId, MediaType.LIVE, existing)
            }
            prefs.edit().remove(catKey(profileId)).apply()
            return
        }
        val id = existing ?: customize.createCustomCategory(
            profileId, MediaType.LIVE, context.getString(tv.own.owntv.core.R.string.new_channels_category),
        ).id.also { created ->
            prefs.edit().putString(catKey(profileId), created).apply()
            // First among the categories, right under the built-in rows (owner: «рядом с Архивом»).
            runCatching {
                customize.update(profileId, MediaType.LIVE) { c -> c.copy(categoryPositions = c.categoryPositions + (created to 0)) }
            }
        }
        dao.replaceContext(
            profileId, MediaType.LIVE, id,
            ids.mapIndexed { i, itemId ->
                CustomCategoryMemberEntity(profileId = profileId, mediaType = MediaType.LIVE, contextKey = id, itemId = itemId, position = i)
            },
        )
    }

    private fun dir() = File(context.filesDir, "new_channels").apply { mkdirs() }

    private fun loadAdded(sourceId: Long): Map<String, Long> {
        val raw = prefs.getString("added_$sourceId", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    private fun saveAdded(sourceId: Long, added: Map<String, Long>) {
        val o = JSONObject()
        added.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("added_$sourceId", o.toString()).apply()
    }

    private fun catKey(profileId: Long) = "cat_$profileId"

    companion object {
        private const val PREFS = "new_channels"
        private const val KEEP_MS = 7L * 24 * 3_600_000L
        fun fromKoin(): NewChannels = org.koin.core.context.GlobalContext.get().get()
    }
}
