// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import org.json.JSONObject
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

/**
 * A local source ↔ the account record `kind:"source"` (docs/SYNC_API.md §13): `data` is open, `secret` is encrypted
 * on the device. The M3U address is a secret too (it often carries a token). Same shape as the site cabinet.
 */
object SourceRecords {
    data class Record(val data: JSONObject, val secret: JSONObject)

    fun typeOf(source: SourceEntity): String? = when (source.type) {
        SourceType.M3U -> "m3u"
        SourceType.XTREAM -> "xtream"
        SourceType.STALKER -> "stalker"
        SourceType.LOCAL_BACKUP -> null // a file on this device: nothing to sync
    }

    fun toRecord(s: SourceEntity): Record? {
        val type = typeOf(s) ?: return null
        val data = JSONObject().put("type", type).put("name", s.name)
        val secret = JSONObject()
        s.epgUrl?.takeIf { it.isNotBlank() }?.let { data.put("epgUrl", it) }
        when (type) {
            "m3u" -> secret.put("url", s.url)
            "xtream" -> {
                data.put("url", s.url)
                s.username?.let { secret.put("username", it) }
                s.password?.let { secret.put("password", it) }
            }
            "stalker" -> {
                data.put("url", s.url)
                s.mac?.let { secret.put("mac", it.uppercase()) }
                s.stalkerSerialNumber?.let { secret.put("serial", it) }
                s.stalkerDeviceId?.let { secret.put("deviceId", it) }
                s.username?.let { secret.put("username", it) }
                s.password?.let { secret.put("password", it) }
            }
        }
        return Record(data, secret)
    }

    /** Fields that, when changed on either side, mean the record has to travel. */
    fun fingerprint(s: SourceEntity): String =
        listOf(s.type.name, s.name, s.url, s.username, s.password, s.mac, s.stalkerSerialNumber, s.stalkerDeviceId, s.epgUrl)
            .joinToString("\u0001") { it ?: "" }.hashCode().toString(16)

    /** A new local row, or [base] updated with what came from the account. Null when the record cannot be used here. */
    fun toEntity(data: JSONObject, secret: JSONObject?, base: SourceEntity? = null): SourceEntity? {
        val type = when (data.optString("type")) {
            "m3u" -> SourceType.M3U
            "xtream" -> SourceType.XTREAM
            "stalker" -> SourceType.STALKER
            else -> return null // "epg" records: a guide of its own, handled by the guide settings later
        }
        val s = secret ?: JSONObject()
        fun opt(o: JSONObject, k: String) = o.optString(k, "").ifBlank { null }
        val url = (if (type == SourceType.M3U) opt(s, "url") else opt(data, "url"))
            ?: base?.url ?: return null // secrets still locked and nothing local to keep
        val name = opt(data, "name") ?: url
        val start = base ?: SourceEntity(name = name, type = type, url = url)
        return start.copy(
            name = name,
            url = url,
            epgUrl = opt(data, "epgUrl"),
            username = if (secret != null) opt(s, "username") else start.username,
            password = if (secret != null) opt(s, "password") else start.password,
            mac = if (type == SourceType.STALKER && secret != null) opt(s, "mac")?.uppercase() else start.mac,
            stalkerSerialNumber = if (secret != null) opt(s, "serial") else start.stalkerSerialNumber,
            stalkerDeviceId = if (secret != null) opt(s, "deviceId") else start.stalkerDeviceId,
        )
    }
}
