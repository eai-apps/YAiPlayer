// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileSourceCrossRef
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import java.security.KeyPair
import java.util.UUID

/**
 * The main account on this device (owner 13.11): sign in / sign up with a login and password on the TV box or phone,
 * on the TV also by a code or QR confirmed on the phone/site; the account's profiles live INSIDE the account tile on the
 * chooser, next to Guest (no server), Radio and — on the phone — Remote. Profiles and sources sync both ways with the
 * account server (docs/SYNC_API.md §5, §13). Source secrets are encrypted here before they leave the device.
 */
class AccountService(
    private val api: AccountApi,
    private val store: AccountStore,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val sources: SourceRepository,
    private val profiles: ProfileManager,
    private val catalog: CatalogSyncScheduler,
    private val favorites: AccountFavorites,
    private val favoriteDao: tv.own.owntv.core.database.dao.FavoriteDao,
) {
    val session = store.session
    private val mutex = Mutex()

    val signedIn get() = store.session.value != null
    val unlocked get() = store.dataKey != null
    fun accountProfileIds(): Set<Long> = store.profileLinks.values.toSet()

    /**
     * Local profiles (not the account's, not [guestId]) with no source at all: leftovers such as an empty
     * «Профиль» from an earlier first run. With the account on they are not shown on the chooser (owner 13.11);
     * a local profile that has playlists stays, so nobody loses what they set up before the update.
     */
    suspend fun emptyLocalProfileIds(profileIds: Collection<Long>, guestId: Long?): Set<Long> {
        val withSources = sourceDao.allLinks().map { it.profileId }.toSet()
        val account = accountProfileIds()
        return profileIds.filter { it != guestId && it !in account && it !in withSources }.toSet()
    }

    private fun token() = store.session.value?.token ?: throw AccountApiException(401, "unauthorized")

    // ---------- sign up / sign in ----------
    /** Returns the recovery code the person must write down (shown once). */
    suspend fun register(login: String, password: String, deviceName: String, deviceKind: String): String {
        val code = AccountCrypto.newRecoveryCode()
        val k = AccountCrypto.newAccountKeys(password, code)
        val keys = JSONObject().put("keySalt", k.keySalt).put("wrappedByPassword", k.wrappedByPassword)
            .put("wrappedByRecovery", k.wrappedByRecovery).put("publicKey", k.publicKey).put("wrappedPrivateKey", k.wrappedPrivateKey)
        val r = api.obj("POST", "/v1/auth/register", JSONObject()
            .put("login", login.trim().lowercase()).put("password", password).put("recoveryCode", code)
            .put("keys", keys).put("device", api.device(deviceName, deviceKind)).put("profileName", login.trim()))
        val acc = r.getJSONObject("account")
        store.signIn(r.getString("token"), acc.getString("login"), acc.getString("id"))
        store.dataKey = k.dataKey
        runCatching { sync() }
        return code
    }

    suspend fun login(login: String, password: String, deviceName: String, deviceKind: String) {
        val r = api.obj("POST", "/v1/auth/login", JSONObject()
            .put("login", login.trim().lowercase()).put("password", password).put("device", api.device(deviceName, deviceKind)))
        val acc = r.getJSONObject("account")
        store.signIn(r.getString("token"), acc.getString("login"), acc.getString("id"))
        store.dataKey = r.optJSONObject("keys")?.let { k ->
            runCatching { AccountCrypto.unlockWithPassword(k.getString("keySalt"), k.getString("wrappedByPassword"), password) }.getOrNull()
        }
        runCatching { sync() }
    }

    suspend fun recover(login: String, code: String, newPassword: String, deviceName: String, deviceKind: String) {
        val r = api.obj("POST", "/v1/auth/recover", JSONObject().put("login", login.trim().lowercase()).put("recoveryCode", code)
            .put("newPassword", newPassword).put("device", api.device(deviceName, deviceKind)))
        val acc = r.getJSONObject("account")
        store.signIn(r.getString("token"), acc.getString("login"), acc.getString("id"))
        val keys = r.optJSONObject("keys")
        val dk = keys?.let { runCatching { AccountCrypto.unlockWithRecovery(it.getString("wrappedByRecovery"), code) }.getOrNull() }
        if (dk != null) {
            val w = AccountCrypto.wrapByPassword(dk, newPassword)
            runCatching { api.obj("PUT", "/v1/keys", JSONObject().put("keySalt", w.keySalt).put("wrappedByPassword", w.wrappedByPassword), token()) }
        }
        store.dataKey = dk
        runCatching { sync() }
    }

    /** Opens the source passwords on this device with the account password or the recovery code. */
    suspend fun unlock(passwordOrCode: String, isRecoveryCode: Boolean): Boolean {
        val k = api.obj("GET", "/v1/keys", null, token())
        val dk = runCatching {
            if (isRecoveryCode) AccountCrypto.unlockWithRecovery(k.getString("wrappedByRecovery"), passwordOrCode)
            else AccountCrypto.unlockWithPassword(k.getString("keySalt"), k.getString("wrappedByPassword"), passwordOrCode)
        }.getOrNull() ?: return false
        store.dataKey = dk
        runCatching { sync() }
        return true
    }

    // ---------- TV: sign in by code / QR ----------
    class Pairing(val code: String, val url: String, val expiresInSec: Int, internal val pollToken: String, internal val keyPair: KeyPair)

    suspend fun startPairing(deviceName: String): Pairing {
        val kp = AccountCrypto.newDeviceKeyPair()
        val r = api.obj("POST", "/v1/pair/start", JSONObject().put("device", api.device(deviceName, "tv")).put("devicePub", AccountCrypto.publicRaw(kp.public)))
        return Pairing(r.getString("code"), r.getString("url"), r.optInt("expiresIn", 600), r.getString("pollToken"), kp)
    }

    /** "waiting", "expired" or "ok" (signed in, key handed over, first sync done). */
    suspend fun pollPairing(p: Pairing): String {
        val r = api.obj("POST", "/v1/pair/poll", JSONObject().put("pollToken", p.pollToken))
        val status = r.optString("status")
        if (status != "ok") return status
        val acc = r.getJSONObject("account")
        store.signIn(r.getString("token"), acc.optString("login"), acc.getString("id"))
        store.dataKey = r.optJSONObject("keys")?.optString("wrappedForDevice")?.ifBlank { null }?.let {
            runCatching { AccountCrypto.unwrapOnDevice(p.keyPair.private, it) }.getOrNull()
        }
        runCatching { sync() }
        return "ok"
    }

    /** On the phone: confirm a TV's code (or QR) and hand it the data key, so the TV opens the source passwords itself. */
    suspend fun confirmPairing(code: String, localProfileId: Long?) {
        val clean = code.uppercase().filter { it.isLetterOrDigit() }
        val info = api.obj("GET", "/v1/pair/$clean", null, token())
        val body = JSONObject().put("code", info.getString("code"))
        localProfileId?.let { lid -> store.profileLinks.entries.firstOrNull { it.value == lid }?.key }?.let { body.put("profileId", it) }
        val pub = info.optString("devicePub").ifBlank { null }
        val dk = store.dataKey
        if (pub != null && dk != null) body.put("wrappedForDevice", AccountCrypto.wrapForDevice(dk, pub))
        api.obj("POST", "/v1/pair/confirm", body, token())
    }

    // ---------- sign out ----------
    /** Signs out; the account's profiles and their sources leave this device (they stay in the account). */
    suspend fun signOut() = mutex.withLock {
        runCatching { api.obj("POST", "/v1/auth/logout", null, token()) }
        removeLocalAccountData()
        store.clear()
        favorites.clear()
    }

    private suspend fun removeLocalAccountData() {
        for (link in store.sourceLinks.values) sourceDao.getById(link.localId)?.let { runCatching { sources.deleteSource(it) } }
        for (id in store.profileLinks.values) profileDao.getById(id)?.let { runCatching { profiles.delete(it) } }
    }

    suspend fun createProfile(name: String) {
        api.obj("POST", "/v1/profiles", JSONObject().put("name", name.trim().take(40)), token())
        sync()
    }

    // ---------- automatic sync (owner 13.11: a playlist added on the phone must reach the TV by itself) ----------
    private var auto: Job? = null

    /**
     * Started once from the Application: a local change to sources goes up a few seconds later, and the account
     * is pulled at start and every [PULL_EVERY_MS] while the app runs (and whenever the chooser opens).
     */
    @OptIn(FlowPreview::class)
    fun startAuto(scope: CoroutineScope) {
        if (auto != null) return
        auto = scope.launch {
            launch {
                delay(START_DELAY_MS)
                while (true) { syncQuietly(); delay(PULL_EVERY_MS) }
            }
            launch {
                favoriteDao.observeChangeMarker().distinctUntilChanged().drop(1).debounce(PUSH_DEBOUNCE_MS).collect { syncQuietly() }
            }
            sourceDao.observeAll()
                .map { list -> list.joinToString("|") { "${it.id}:${SourceRecords.fingerprint(it)}" } }
                .distinctUntilChanged()
                .drop(1)
                .debounce(PUSH_DEBOUNCE_MS)
                .collect { syncQuietly() }
        }
    }

    /** A sync whose failure (offline, signed out) only goes to the log. */
    suspend fun syncQuietly() {
        if (!signedIn) return
        runCatching { sync() }.onFailure { Log.w(TAG, "auto sync: ${it.message}") }
    }

    // ---------- sync ----------
    data class Report(val profiles: Int, val added: Int, val updated: Int, val removed: Int, val pushed: Int, val locked: Int)

    suspend fun sync(): Report = mutex.withLock {
        val t = token()
        val dk = store.dataKey
        try {
            val serverProfiles = api.arr("/v1/profiles", t)
            val records = pullRecords(t)
            val pulled = applyProfiles(serverProfiles)
            val (added, updated, removed) = applySources(records, dk)
            applySharing(serverProfiles)
            val (pushed, locked) = push(t, dk)
            val favPushed = runCatching { syncFavorites(t, records) }.onFailure { Log.w(TAG, "favorites: ${it.message}") }.getOrDefault(0)
            Report(pulled, added, updated, removed, pushed + favPushed, locked).also { Log.i(TAG, "sync $it") }
        } catch (e: AccountApiException) {
            if (e.status == 401 || e.code == "account_blocked") { removeLocalAccountData(); store.clear(); favorites.clear() }
            throw e
        }
    }

    private suspend fun pullRecords(t: String): Map<String, JSONObject> {
        val out = LinkedHashMap<String, JSONObject>()
        var cursor = 0L
        repeat(100) {
            val r = api.obj("GET", "/v1/sync?cursor=$cursor&limit=500", null, t)
            val arr = r.optJSONArray("records") ?: JSONArray()
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { out[it.getString("id")] = it }
            cursor = r.optLong("cursor", cursor)
            if (!r.optBoolean("more")) return out
        }
        return out
    }

    private suspend fun applyProfiles(server: JSONArray): Int {
        val links = store.profileLinks.toMutableMap()
        val seen = HashSet<String>()
        for (i in 0 until server.length()) {
            val p = server.getJSONObject(i)
            val sid = p.getString("id")
            seen += sid
            val name = p.optString("name").ifBlank { "Профиль" }
            val local = links[sid]?.let { profileDao.getById(it) }
            if (local == null) {
                links[sid] = profiles.create(name, avatarId = i % 10, isKids = false, pin = null, fallbackName = name)
            } else if (local.name != name) {
                profileDao.update(local.copy(name = name))
            }
        }
        for ((sid, lid) in links.toMap()) if (sid !in seen) {
            profileDao.getById(lid)?.let { runCatching { profiles.delete(it) } }
            links.remove(sid)
        }
        store.profileLinks = links
        return seen.size
    }

    private suspend fun applySources(records: Map<String, JSONObject>, dk: ByteArray?): Triple<Int, Int, Int> {
        val links = store.sourceLinks.toMutableMap()
        val profileLinks = store.profileLinks
        var added = 0; var updated = 0; var removed = 0
        for ((id, r) in records) {
            if (r.optString("kind") != "source") continue
            val link = links[id]
            if (r.optBoolean("deleted")) {
                if (link != null) {
                    sourceDao.getById(link.localId)?.let { sources.deleteSource(it); removed++ }
                    links.remove(id)
                }
                continue
            }
            val sid = r.getString("profileId")
            val lid = profileLinks[sid] ?: continue
            val rev = r.optInt("rev")
            val data = r.optJSONObject("data") ?: JSONObject()
            val secret = r.optString("secret").ifBlank { null }?.let { s ->
                if (dk == null) null else runCatching { JSONObject(AccountCrypto.decryptSecret(dk, s)) }.getOrNull()
            } ?: if (r.optString("secret").isBlank()) JSONObject() else null
            val local = link?.let { sourceDao.getById(it.localId) }
            if (local == null) {
                val e = SourceRecords.toEntity(data, secret) ?: continue
                val newId = sourceDao.insert(e)
                sourceDao.link(ProfileSourceCrossRef(profileId = lid, sourceId = newId))
                val saved = e.copy(id = newId)
                links[id] = AccountStore.SourceLink(newId, rev, SourceRecords.fingerprint(saved), sid)
                runCatching { catalog.enqueueSync(newId, reason = "account") }
                added++
            } else if (link.rev != rev) {
                val e = SourceRecords.toEntity(data, secret, base = local) ?: continue
                if (SourceRecords.fingerprint(e) != SourceRecords.fingerprint(local)) {
                    sourceDao.update(e)
                    val addressChanged = e.url != local.url || e.username != local.username || e.password != local.password || e.mac != local.mac
                    if (addressChanged) runCatching { catalog.enqueueSync(e.id, reason = "account") }
                    updated++
                }
                links[id] = AccountStore.SourceLink(local.id, rev, SourceRecords.fingerprint(e), sid)
            }
        }
        store.sourceLinks = links
        return Triple(added, updated, removed)
    }

    /** «Видит плейлисты главного профиля»: link the main profile's sources to that profile on this device. */
    private suspend fun applySharing(server: JSONArray) {
        val profileLinks = store.profileLinks
        var mainSid: String? = null
        for (i in 0 until server.length()) server.getJSONObject(i).let { if (it.optBoolean("isMain")) mainSid = it.getString("id") }
        val mainLocal = mainSid?.let { profileLinks[it] } ?: return
        val mainSources = sourceDao.observeForProfile(mainLocal).first()
        for (i in 0 until server.length()) {
            val p = server.getJSONObject(i)
            if (!p.optBoolean("shareSources") || p.optBoolean("isMain")) continue
            val lid = profileLinks[p.getString("id")] ?: continue
            mainSources.forEach { sourceDao.link(ProfileSourceCrossRef(profileId = lid, sourceId = it.id)) }
        }
    }

    /** Favorites of the account's profiles, both ways (see [AccountFavorites]). Returns how many records went up. */
    private suspend fun syncFavorites(t: String, records: Map<String, JSONObject>): Int {
        val profileLinks = store.profileLinks
        val sourceLinks = store.sourceLinks
        for (r in records.values) {
            if (r.optString("kind") != "favorite" || r.optBoolean("deleted")) continue
            val lid = profileLinks[r.optString("profileId")] ?: continue
            val sourceLid = sourceLinks[r.optJSONObject("data")?.optString("source")]?.localId ?: continue
            runCatching { favorites.apply(r, lid, sourceLid) }.onFailure { Log.w(TAG, "favorite apply: ${it.message}") }
        }
        val ridByLocal = sourceLinks.entries.associate { it.value.localId to it.key }
        val changes = JSONArray()
        val pending = HashMap<String, JSONObject>()
        for ((sid, lid) in profileLinks) {
            for (s in sourceDao.observeForProfile(lid).first()) {
                val rid = ridByLocal[s.id] ?: continue
                val (id, change) = favorites.change(sid, lid, rid, s.id) ?: continue
                changes.put(change); pending[id] = change
            }
        }
        if (changes.length() == 0) return 0
        val r = api.obj("POST", "/v1/sync", JSONObject().put("changes", changes), t)
        val applied = r.optJSONArray("applied") ?: JSONArray()
        for (i in 0 until applied.length()) {
            val a = applied.getJSONObject(i)
            pending[a.getString("id")]?.let { favorites.accepted(a.getString("id"), a.getInt("rev"), it) }
        }
        return applied.length()
    }

    /** Local changes in the account's profiles go up: new sources, edited ones, deleted ones. */
    private suspend fun push(t: String, dk: ByteArray?): Pair<Int, Int> {
        val links = store.sourceLinks.toMutableMap()
        val byLocal = links.entries.associateBy { it.value.localId }
        val changes = JSONArray()
        val pending = HashMap<String, Pair<Long, String>>() // recordId → (localId, fingerprint)
        var locked = 0
        for ((sid, lid) in store.profileLinks) {
            for (s in sourceDao.observeForProfile(lid).first()) {
                val rec = SourceRecords.toRecord(s) ?: continue
                val existing = byLocal[s.id]
                val fp = SourceRecords.fingerprint(s)
                if (existing != null && existing.value.fingerprint == fp) continue
                if (existing == null && links.values.any { it.localId == s.id }) continue
                val hasSecret = rec.secret.length() > 0
                if (hasSecret && dk == null) { locked++; continue }
                val id = existing?.key ?: UUID.randomUUID().toString()
                changes.put(JSONObject().put("id", id).put("profileId", existing?.value?.profileServerId ?: sid).put("kind", "source")
                    .put("data", rec.data).put("baseRev", existing?.value?.rev ?: 0).apply {
                        if (hasSecret) put("secret", AccountCrypto.encryptSecret(dk!!, rec.secret.toString()))
                    })
                pending[id] = s.id to fp
                if (existing == null) links[id] = AccountStore.SourceLink(s.id, 0, "", sid)
            }
        }
        for ((id, link) in links.toMap()) {
            if (id in pending || link.rev == 0) continue
            if (sourceDao.getById(link.localId) == null) {
                changes.put(JSONObject().put("id", id).put("profileId", link.profileServerId).put("kind", "source").put("deleted", true).put("baseRev", link.rev))
                pending[id] = -1L to ""
            }
        }
        var pushed = 0
        if (changes.length() > 0) {
            val r = api.obj("POST", "/v1/sync", JSONObject().put("changes", changes), t)
            val applied = r.optJSONArray("applied") ?: JSONArray()
            for (i in 0 until applied.length()) {
                val a = applied.getJSONObject(i)
                val id = a.getString("id")
                val (localId, fp) = pending[id] ?: continue
                if (localId < 0) links.remove(id) else links[id] = links.getValue(id).copy(rev = a.getInt("rev"), fingerprint = fp)
                pushed++
            }
            // conflicts: the next pull brings the server's version
            val conflicts = r.optJSONArray("conflicts") ?: JSONArray()
            for (i in 0 until conflicts.length()) {
                val id = conflicts.getJSONObject(i).getString("id")
                if (links[id]?.rev == 0) links.remove(id)
            }
        }
        store.sourceLinks = links.filterValues { it.rev > 0 || it.fingerprint.isNotEmpty() }
        return pushed to locked
    }

    private companion object {
        const val TAG = "AccountService"
        const val START_DELAY_MS = 5_000L
        const val PULL_EVERY_MS = 5 * 60_000L
        const val PUSH_DEBOUNCE_MS = 4_000L
    }
}
