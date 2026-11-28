// MIP Player (operator edition of YAiPlayer). Original code.
package tv.own.owntv.core.mip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import java.io.IOException

/** A message from the operator (panel «Уведомления»), shown once until read. */
data class MipNotice(val id: Long, val kind: String, val title: String, val body: String)

/** Login failed: [network] = no connection, otherwise the panel refused ([message] is its Russian text). */
class MipException(val network: Boolean, message: String) : Exception(message) {
    /** The panel's own words (Russian), or blank — the screen then shows its translated generic text. */
    val panelText: String get() = if (network) "" else message.orEmpty()
}

/**
 * MIP Player has exactly one way in: the login and password the operator created in the panel (miptv). There are no
 * guest and no own playlists or portals; the playlist and the Stalker portal come from the panel and are written into
 * one local profile, so the rest of the app (channels, guide, VOD, radio, remote) works exactly as in YAiPlayer.
 */
class MipService(
    private val http: OkHttpClient,
    private val store: MipStore,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val sources: SourceRepository,
    private val profiles: ProfileManager,
    private val catalog: CatalogSyncScheduler,
    private val settings: SettingsRepository,
    /** Names shown to the user, already translated (the service has no Context). */
    private val profileName: String,
    private val playlistName: String,
    private val portalName: String,
) {
    val session = store.session
    val signedIn get() = store.session.value != null
    val expires get() = store.expires
    val paymentMessage get() = store.paymentMessage

    private val mutex = Mutex()
    private val json = "application/json".toMediaType()

    private suspend fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): Any? =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(MipConfig.BASE + path)
            if (token != null) rb.header("Authorization", "Bearer $token")
            rb.method(method, if (method == "GET") null else (body?.toString() ?: "{}").toRequestBody(json))
            val res = try { http.newCall(rb.build()).execute() } catch (e: IOException) {
                throw MipException(true, e.message ?: "network")
            }
            res.use { r ->
                val text = r.body.string()
                if (!r.isSuccessful) {
                    val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull().orEmpty()
                    throw MipException(false, detail.also { if (r.code == 401 && token != null) store.clear() })
                }
                when {
                    text.isBlank() -> null
                    text.trimStart().startsWith("[") -> JSONArray(text)
                    else -> JSONObject(text)
                }
            }
        }

    /** Signs in and builds the profile and its sources from the panel. Throws [MipException]. */
    suspend fun login(login: String, password: String, device: String) {
        val res = call("POST", "/api/app/login", JSONObject().put("login", login.trim()).put("password", password).put("device", device)) as? JSONObject
            ?: throw MipException(false, "")
        store.signIn(res.getString("token"), login.trim())
        refresh()
    }

    /** Pulls the panel's config (playlist, portal, expiry) and brings the local sources in line with it. */
    suspend fun refresh() = mutex.withLock {
        val token = store.session.value?.token ?: return@withLock
        val cfg = call("GET", "/api/app/config", null, token) as? JSONObject ?: return@withLock
        store.expires = cfg.optString("expires")
        store.paymentMessage = cfg.optString("payment_message")
        val pid = ensureProfile()
        val playlist = cfg.optString("playlist_url").takeIf { it.isNotBlank() && it != "null" }
        val portal = cfg.optString("portal_url").takeIf { it.isNotBlank() && it != "null" }
        val epg = cfg.optString("epg_url").takeIf { it.isNotBlank() && it != "null" }?.let { if (it.startsWith("/")) MipConfig.BASE + it else it }
        syncPlaylist(pid, playlist, epg)
        syncPortal(pid, portal, cfg)
        settings.setActiveProfile(pid)
    }

    private suspend fun ensureProfile(): Long {
        val known = store.profileId
        if (known >= 0 && profileDao.getById(known) != null) return known
        val id = profiles.create(profileName, avatarId = 0, isKids = false, pin = null, fallbackName = profileName)
        store.profileId = id
        return id
    }

    private suspend fun syncPlaylist(pid: Long, url: String?, epg: String?) {
        val old = store.playlistSourceId.takeIf { it >= 0 }?.let { sourceDao.getById(it) }
        if (url == null) { old?.let { sources.deleteSource(it) }; store.playlistSourceId = -1; return }
        if (old == null) {
            val s = sources.addM3uSource(pid, playlistName, url, epgUrl = epg)
            store.playlistSourceId = s.id
            runCatching { catalog.enqueueSync(s.id, reason = "mip") }
        } else if (old.url != url || old.epgUrl != epg) {
            sourceDao.update(old.copy(url = url, epgUrl = epg))
            runCatching { catalog.enqueueSync(old.id, reason = "mip") }
        }
    }

    private suspend fun syncPortal(pid: Long, url: String?, cfg: JSONObject) {
        val old = store.portalSourceId.takeIf { it >= 0 }?.let { sourceDao.getById(it) }
        val mac = cfg.optString("mac").takeIf { it.isNotBlank() && it != "null" }
        if (url == null || mac == null) { old?.let { sources.deleteSource(it) }; store.portalSourceId = -1; return }
        fun f(k: String) = cfg.optString(k).takeIf { it.isNotBlank() && it != "null" }
        if (old == null) {
            val s = sources.addStalkerSource(
                pid, portalName, url, mac,
                serialNumber = f("serial"), deviceId = f("device_id"), deviceId2 = f("device_id2"),
                login = f("portal_login"),
            )
            store.portalSourceId = s.id
            runCatching { catalog.enqueueSync(s.id, reason = "mip") }
        } else if (old.url != url || old.mac != mac || old.username != f("portal_login")) {
            sourceDao.update(old.copy(url = url, mac = mac, username = f("portal_login"),
                stalkerSerialNumber = f("serial"), stalkerDeviceId = f("device_id"), stalkerDeviceId2 = f("device_id2")))
            runCatching { catalog.enqueueSync(old.id, reason = "mip") }
        }
    }

    suspend fun notices(lang: String = "ru"): List<MipNotice> {
        val token = store.session.value?.token ?: return emptyList()
        val arr = call("GET", "/api/app/notices?lang=$lang", null, token) as? JSONArray ?: return emptyList()
        return (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { MipNotice(it.getLong("id"), it.optString("kind"), it.optString("title"), it.optString("body")) }
        }
    }

    suspend fun markRead(id: Long) { runCatching { call("POST", "/api/app/notices/$id/read", null, store.session.value?.token) } }

    /** Signs out: the panel session ends, and the profile with its sources is removed from this device. */
    suspend fun logout() = mutex.withLock {
        runCatching { call("POST", "/api/app/logout", null, store.session.value?.token) }
        for (sid in listOf(store.playlistSourceId, store.portalSourceId)) if (sid >= 0) sourceDao.getById(sid)?.let { runCatching { sources.deleteSource(it) } }
        if (store.profileId >= 0) profileDao.getById(store.profileId)?.let { runCatching { profiles.delete(it) } }
        store.clear()
    }
}
