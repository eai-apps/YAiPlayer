// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.backup.UserDataResolver
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.UserDataExportRow
import tv.own.owntv.core.model.MediaType
import java.security.MessageDigest

/**
 * Favorites of the account's profiles travel between devices (owner 13.11): one record `kind:"favorite"` per
 * profile + source, holding the items by their stable keys (provider remote id, falling back to the name — the
 * same keys backups use), never by local row ids. Arrival goes through [UserDataResolver], so a favorite whose
 * channel is not loaded yet waits and heals after the source syncs; a favorite removed elsewhere is removed here
 * as a deletion marker that only wins over older rows.
 */
class AccountFavorites(
    context: Context,
    private val favoriteDao: FavoriteDao,
    private val resolver: UserDataResolver,
) {
    private val prefs = context.getSharedPreferences("yai_account_fav_v1", Context.MODE_PRIVATE)

    /** What was last agreed for one record: server rev, the local list's fingerprint, the item keys. */
    private data class State(val rev: Int, val localFp: String, val keys: Set<String>)

    private fun state(id: String): State? = prefs.getString(id, null)?.let { s ->
        runCatching {
            val o = JSONObject(s)
            val k = o.optJSONArray("k") ?: JSONArray()
            State(o.optInt("rev"), o.optString("fp"), (0 until k.length()).map { k.getString(it) }.toSet())
        }.getOrNull()
    }

    private fun save(id: String, st: State) {
        prefs.edit().putString(id, JSONObject().put("rev", st.rev).put("fp", st.localFp).put("k", JSONArray(st.keys.toList())).toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun recordId(profileSid: String, sourceRid: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest("$profileSid|$sourceRid".toByteArray())
        return "fav_" + h.take(16).joinToString("") { "%02x".format(it) }
    }

    /** An item as it travels: no local ids, no profile — only what identifies the content. */
    private fun item(r: UserDataExportRow): JSONObject? = when (r.mediaType) {
        MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES -> r.name?.let {
            JSONObject().put("t", r.mediaType.name).putOpt("rid", r.remoteId).put("name", it).put("at", r.at)
        }
        MediaType.EPISODE -> r.seriesName?.let {
            JSONObject().put("t", r.mediaType.name).putOpt("srid", r.seriesRemoteId).put("sname", it)
                .putOpt("rid", r.remoteId).put("season", r.seasonNumber ?: 0).put("ep", r.episodeNumber ?: 0).put("at", r.at)
        }
    }

    private fun key(i: JSONObject): String = listOf("t", "rid", "name", "srid", "sname", "season", "ep")
        .joinToString("\u0001") { i.opt(it)?.toString() ?: "" }

    private suspend fun localItems(profileLid: Long, sourceLid: Long): List<JSONObject> =
        favoriteDao.exportRowsForSource(sourceLid).filter { it.profileId == profileLid }.mapNotNull { item(it) }
            .sortedByDescending { it.optLong("at") }.take(MAX_ITEMS)

    private fun fingerprint(items: List<JSONObject>) = items.map { key(it) }.sorted().joinToString("\u0002").hashCode().toString(16)

    /** Server → device for one favorite record. */
    suspend fun apply(record: JSONObject, profileLid: Long, sourceLid: Long) {
        val id = record.getString("id")
        val rev = record.optInt("rev")
        val old = state(id)
        if (old != null && old.rev == rev) return
        val items = record.optJSONObject("data")?.optJSONArray("items") ?: JSONArray()
        val incoming = (0 until items.length()).map { items.getJSONObject(it) }
        val keys = incoming.map { key(it) }.toSet()
        // additions: through the resolver (idempotent; unresolved ones wait for the source's content)
        val add = JSONArray()
        incoming.filter { old == null || key(it) !in old.keys }.forEach {
            add.put(JSONObject(it.toString()).put("src", sourceLid).put("p", profileLid).put("kind", "fav"))
        }
        if (add.length() > 0) resolver.importAll(add)
        // removals: what the other device took away since we last agreed
        val removed = old?.keys?.minus(keys).orEmpty()
        if (removed.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val gone = JSONArray()
            for (k in removed) {
                val p = k.split('\u0001')
                gone.put(JSONObject().put("t", p[0]).put("src", sourceLid).put("p", profileLid).put("kind", "fav").put("at", now).apply {
                    p.getOrNull(1)?.ifBlank { null }?.let { put("rid", it) }
                    p.getOrNull(2)?.ifBlank { null }?.let { put("name", it) }
                    p.getOrNull(3)?.ifBlank { null }?.let { put("srid", it) }
                    p.getOrNull(4)?.ifBlank { null }?.let { put("sname", it) }
                    p.getOrNull(5)?.toIntOrNull()?.let { put("season", it) }
                    p.getOrNull(6)?.toIntOrNull()?.let { put("ep", it) }
                })
            }
            resolver.applyTombstones(gone)
        }
        save(id, State(rev, fingerprint(localItems(profileLid, sourceLid)), keys))
    }

    /** Device → server: a change for one profile + source when the local list moved since we last agreed. */
    suspend fun change(profileSid: String, profileLid: Long, sourceRid: String, sourceLid: Long): Pair<String, JSONObject>? {
        val id = recordId(profileSid, sourceRid)
        val items = localItems(profileLid, sourceLid)
        val fp = fingerprint(items)
        val old = state(id)
        if (old != null && old.localFp == fp) return null
        if (old == null && items.isEmpty()) return null
        val c = JSONObject().put("id", id).put("profileId", profileSid).put("kind", "favorite").put("baseRev", old?.rev ?: 0)
            .put("data", JSONObject().put("source", sourceRid).put("items", JSONArray(items)))
        return id to c
    }

    /** After the server accepted [change]: remember the new agreement. */
    fun accepted(id: String, rev: Int, change: JSONObject) {
        val items = change.getJSONObject("data").getJSONArray("items")
        val list = (0 until items.length()).map { items.getJSONObject(it) }
        save(id, State(rev, fingerprint(list), list.map { key(it) }.toSet()))
    }

    private companion object { const val MAX_ITEMS = 400 }
}
