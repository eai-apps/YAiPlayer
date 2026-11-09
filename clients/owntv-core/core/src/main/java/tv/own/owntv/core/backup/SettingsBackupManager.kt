package tv.own.owntv.core.backup

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.StartupMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings backup and migration for moving setup to a new device.
 *
 * File format: `yaiplayer-backup-YYYYMMDD.json` with format version in header.
 *
 * Included:
 * - Profiles (name, avatar, quick-start mode)
 * - Favorites
 * - Hidden and pinned categories / channels
 * - Custom categories and category / item order
 * - Sort preferences, UI and player settings
 * - Standalone EPG sources list
 *
 * Excluded (NO SECRETS):
 * - Logins, passwords, tokens, MAC addresses, full playlist URLs with user keys.
 *   Only playlist name and type are saved.
 *
 * When restoring on a new device, user reconnects the playlist; the app matches by name
 * and restores favorites, categories and order.
 */
class SettingsBackupManager(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settingsRepository: SettingsRepository,
    private val customizationStore: CustomizationStore,
    private val epgSourceStore: EpgSourceStore,
    private val userDataResolver: UserDataResolver,
    private val contentOrderDao: ContentOrderDao,
    private val favoriteDao: FavoriteDao,
    private val db: OwnTVDatabase,
    private val storageDir: File,
) {
    companion object {
        const val APP_ID = "yaiplayer"
        const val CURRENT_FORMAT_VERSION = 1
        const val MAX_BACKUP_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
        private const val PENDING_FILE_NAME = "pending_backup_playlists.json"

    /** Pure format check; no database or settings involved, so it can be called (and tested) on its own. */
    fun validate(raw: String): ValidationResult {
        if (raw.isBlank() || raw.length > MAX_BACKUP_SIZE_BYTES) {
            return ValidationResult.InvalidFormat
        }
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return ValidationResult.InvalidFormat

        val app = json.optString("app")
        if (app != APP_ID) return ValidationResult.InvalidFormat

        val version = json.optInt("formatVersion", 0)
        if (version <= 0) return ValidationResult.InvalidFormat
        if (version > CURRENT_FORMAT_VERSION) return ValidationResult.NewerVersion(version)

        val profileCount = json.optJSONArray("profiles")?.length() ?: 0
        val playlistCount = json.optJSONArray("playlists")?.length() ?: 0
        val epgSourceCount = json.optJSONArray("epgSources")?.length() ?: 0

        var favoriteCount = 0
        val userDataArr = json.optJSONArray("userData")
        if (userDataArr != null) {
            for (i in 0 until userDataArr.length()) {
                val item = userDataArr.optJSONObject(i) ?: continue
                if (item.optString("kind") == "fav") {
                    favoriteCount++
                }
            }
        }

        var categoryCount = 0
        val custObj = json.optJSONObject("customizations")
        if (custObj != null) {
            custObj.keys().forEach { k ->
                val v = custObj.optString(k)
                if (v.isNotBlank()) {
                    runCatching {
                        val o = JSONObject(v)
                        categoryCount += o.optJSONArray("hiddenCats")?.length() ?: 0
                        categoryCount += o.optJSONArray("catOrder")?.length() ?: 0
                        categoryCount += o.optJSONObject("catPos")?.length() ?: 0
                    }
                }
            }
        }

        val summary = BackupSummary(
            profileCount = profileCount,
            playlistCount = playlistCount,
            favoriteCount = favoriteCount,
            categoryCount = categoryCount,
            epgSourceCount = epgSourceCount,
        )
        return ValidationResult.Success(summary, json)
    }
    }

    sealed interface ValidationResult {
        data class Success(val summary: BackupSummary, val parsedJson: JSONObject) : ValidationResult
        data object InvalidFormat : ValidationResult
        data class NewerVersion(val version: Int) : ValidationResult
    }

    data class BackupSummary(
        val profileCount: Int,
        val playlistCount: Int,
        val favoriteCount: Int,
        val categoryCount: Int,
        val epgSourceCount: Int,
    )

    data class PendingPlaylistData(
        val backupSourceId: Long,
        val customizations: Map<String, String>,
        val userData: JSONArray,
    )

    fun formatFilename(nowMs: Long = System.currentTimeMillis()): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMs))
        return "yaiplayer-backup-$date.json"
    }

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("formatVersion", CURRENT_FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        // 1. Profiles (name, avatar, quickStartMode, NO PIN hashes or credentials)
        val profiles = profileDao.getAllOnce()
        val startupModes = settingsRepository.exportStartupModes()
        val profilesArr = JSONArray()
        for (p in profiles) {
            val prof = JSONObject()
            prof.put("id", p.id)
            prof.put("name", p.name)
            prof.put("avatarId", p.avatarId)
            p.avatarPath?.let { prof.put("avatarPath", it) }
            prof.put("isKids", p.isKids)
            prof.put("quickStartMode", startupModes.optString(p.id.toString(), "LAST_CHANNEL"))
            profilesArr.put(prof)
        }
        root.put("profiles", profilesArr)

        // 2. Playlists (name and type ONLY; NO url, username, password, token, mac)
        val sources = sourceDao.getAllOnce()
        val playlistsArr = JSONArray()
        for (s in sources) {
            val pl = JSONObject()
            pl.put("id", s.id)
            pl.put("name", s.name)
            pl.put("type", s.type.name)
            playlistsArr.put(pl)
        }
        root.put("playlists", playlistsArr)

        // 3. EPG Sources (name, url, enabled; non-builtin)
        val epgs = epgSourceStore.getAll().filterNot { it.builtin }
        val epgArr = JSONArray()
        for (e in epgs) {
            val obj = JSONObject()
            obj.put("name", e.name)
            obj.put("url", e.url)
            obj.put("enabled", true)
            epgArr.put(obj)
        }
        root.put("epgSources", epgArr)

        // 4. UI and Player Settings (strip any secret/credential keys)
        val settingsObj = settingsRepository.exportSettings()
        stripSecrets(settingsObj)
        root.put("settings", settingsObj)

        // 5. Customizations (hidden, pinned, reordered categories / channels)
        val cust = customizationStore.exportAll()
        val custObj = JSONObject()
        cust.forEach { (k, v) -> custObj.put(k, v) }
        root.put("customizations", custObj)

        // 6. UserData (favorites, manual item orders, series sort orders, custom category members)
        val kinds = setOf("fav", "order", "sort", "member")
        val userData = userDataResolver.exportAll(kinds)
        root.put("userData", userData)

        return root.toString(2)
    }

    fun validateJson(raw: String): ValidationResult = validate(raw)

    suspend fun restoreFromJson(raw: String): Result<BackupSummary> = runCatching {
        val validation = validateJson(raw)
        if (validation !is ValidationResult.Success) {
            when (validation) {
                is ValidationResult.NewerVersion -> error("backup_newer_version")
                else -> error("backup_invalid_format")
            }
        }
        val root = validation.parsedJson
        val summary = validation.summary

        // 1. Profiles: match existing by name, or create new ProfileEntity
        val existingProfiles = profileDao.getAllOnce()
        val profileIdMap = mutableMapOf<Long, Long>() // backupId -> localId
        val profilesArr = root.optJSONArray("profiles") ?: JSONArray()
        for (i in 0 until profilesArr.length()) {
            val prof = profilesArr.getJSONObject(i)
            val backupId = prof.optLong("id", (i + 1).toLong())
            val name = prof.getString("name")
            val isKids = prof.optBoolean("isKids", false)
            val avatarId = prof.optInt("avatarId", 0)
            val avatarPath = prof.optStringOrNull("avatarPath")
            val quickStart = prof.optStringOrNull("quickStartMode")

            val match = existingProfiles.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
            val localId = if (match != null) {
                match.id
            } else {
                profileDao.insert(
                    ProfileEntity(
                        name = name,
                        avatarColor = 0,
                        avatarId = avatarId,
                        avatarPath = avatarPath,
                        isKids = isKids,
                    ),
                )
            }
            profileIdMap[backupId] = localId
            if (quickStart != null) {
                runCatching { StartupMode.valueOf(quickStart) }.getOrNull()
                    ?.let { settingsRepository.setStartupMode(localId, it) }
            }
        }

        // 2. Settings: import UI and player settings
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            stripSecrets(settingsObj)
            settingsRepository.importSettings(settingsObj, keepDeviceSettings = true)
        }

        // 3. EPG Sources: add new standalone EPG sources
        val epgArr = root.optJSONArray("epgSources") ?: JSONArray()
        for (i in 0 until epgArr.length()) {
            val epg = epgArr.getJSONObject(i)
            val name = epg.optString("name", "EPG")
            val url = epg.getString("url")
            epgSourceStore.ensure(name, url)
        }

        // 4. Playlists & UserData & Customizations matching
        val existingSources = sourceDao.getAllOnce()
        val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()
        val sourceIdMap = mutableMapOf<Long, Long>() // backupSourceId -> localSourceId
        val unmappedPlaylists = mutableMapOf<Long, String>() // backupSourceId -> playlistName

        for (i in 0 until playlistsArr.length()) {
            val pl = playlistsArr.getJSONObject(i)
            val backupId = pl.getLong("id")
            val name = pl.getString("name")
            val match = existingSources.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
            if (match != null) {
                sourceIdMap[backupId] = match.id
            } else {
                unmappedPlaylists[backupId] = name
            }
        }

        // Customizations
        val custObj = root.optJSONObject("customizations")
        val rawCustMap = mutableMapOf<String, String>()
        if (custObj != null) {
            custObj.keys().forEach { k -> rawCustMap[k] = custObj.getString(k) }
        }

        // Profile-remap customization keys: "cust_<backupPid>_<section>" -> "cust_<localPid>_<section>"
        val profileRemappedCust = mutableMapOf<String, String>()
        for ((key, value) in rawCustMap) {
            val parts = key.split('_')
            if (parts.size >= 3 && parts[0] == "cust") {
                val backupPid = parts[1].toLongOrNull()
                val localPid = backupPid?.let { profileIdMap[it] } ?: backupPid
                val newKey = "cust_${localPid}_${parts.drop(2).joinToString("_")}"
                profileRemappedCust[newKey] = value
            } else {
                profileRemappedCust[key] = value
            }
        }

        // Apply customizations for mapped sources immediately
        if (sourceIdMap.isNotEmpty()) {
            val immediateCust = profileRemappedCust.mapValues { (_, value) ->
                remapCustomizationValue(value, sourceIdMap)
            }
            customizationStore.mergeAll(immediateCust)
        }

        // UserData (favorites, orders)
        val userDataArr = root.optJSONArray("userData") ?: JSONArray()
        val immediateUserData = JSONArray()
        val pendingByPlaylist = mutableMapOf<String, PendingPlaylistData>()

        for ((backupId, name) in unmappedPlaylists) {
            pendingByPlaylist[name] = PendingPlaylistData(
                backupSourceId = backupId,
                customizations = profileRemappedCust,
                userData = JSONArray(),
            )
        }

        for (i in 0 until userDataArr.length()) {
            val item = userDataArr.getJSONObject(i)
            val backupSrc = item.optLong("src")
            val backupPid = item.optLong("p")
            val localPid = profileIdMap[backupPid] ?: backupPid

            val remappedItem = JSONObject(item.toString())
            remappedItem.put("p", localPid)

            if (backupSrc in sourceIdMap) {
                val localSrc = sourceIdMap[backupSrc]!!
                remappedItem.put("src", localSrc)
                val ctx = remappedItem.optStringOrNull("ctx")
                if (ctx != null) {
                    remappedItem.put("ctx", remapContentContextKey(ctx, sourceIdMap))
                }
                immediateUserData.put(remappedItem)
            } else if (backupSrc in unmappedPlaylists) {
                val plName = unmappedPlaylists[backupSrc]
                if (plName != null) {
                    pendingByPlaylist[plName]?.userData?.put(remappedItem)
                }
            }
        }

        if (immediateUserData.length() > 0) {
            userDataResolver.importAll(immediateUserData)
        }

        if (pendingByPlaylist.isNotEmpty()) {
            val currentPending = loadPendingPlaylists() ?: mutableMapOf()
            currentPending.putAll(pendingByPlaylist)
            savePendingPlaylists(currentPending)
        }

        summary
    }

    suspend fun onSourceConnected(source: SourceEntity) {
        val pending = loadPendingPlaylists() ?: return
        val plName = source.name.trim().lowercase(Locale.ROOT)
        val matchKey = pending.keys.firstOrNull { it.trim().lowercase(Locale.ROOT) == plName } ?: return
        val data = pending[matchKey] ?: return

        val backupSourceId = data.backupSourceId
        val sourceIdMap = mapOf(backupSourceId to source.id)

        // 1. Remap customizations
        if (data.customizations.isNotEmpty()) {
            val remappedCust = data.customizations.mapValues { (_, json) ->
                remapCustomizationValue(json, sourceIdMap)
            }
            customizationStore.mergeAll(remappedCust)
        }

        // 2. Remap userData (favorites, orders)
        if (data.userData.length() > 0) {
            val remappedUserData = JSONArray()
            for (i in 0 until data.userData.length()) {
                val record = JSONObject(data.userData.getJSONObject(i).toString())
                if (record.optLong("src") == backupSourceId) {
                    record.put("src", source.id)
                }
                val ctx = record.optStringOrNull("ctx")
                if (ctx != null) {
                    record.put("ctx", remapContentContextKey(ctx, sourceIdMap))
                }
                remappedUserData.put(record)
            }
            userDataResolver.importAll(remappedUserData)
            userDataResolver.resolvePending()
        }

        pending.remove(matchKey)
        savePendingPlaylists(pending)
    }

    private fun stripSecrets(obj: JSONObject) {
        val keysToRemove = mutableListOf<String>()
        val iter = obj.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            val lower = k.lowercase(Locale.ROOT)
            if (lower.contains("pass") || lower.contains("token") || lower.contains("key") ||
                lower.contains("secret") || lower.contains("pin")
            ) {
                keysToRemove.add(k)
            }
        }
        keysToRemove.forEach { obj.remove(it) }
    }

    private fun savePendingPlaylists(map: Map<String, PendingPlaylistData>) {
        runCatching {
            val root = JSONObject()
            for ((name, data) in map) {
                val obj = JSONObject()
                obj.put("backupSourceId", data.backupSourceId)
                val custObj = JSONObject()
                data.customizations.forEach { (k, v) -> custObj.put(k, v) }
                obj.put("customizations", custObj)
                obj.put("userData", data.userData)
                root.put(name, obj)
            }
            val file = File(storageDir, PENDING_FILE_NAME)
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        }
    }

    private fun loadPendingPlaylists(): MutableMap<String, PendingPlaylistData>? {
        val file = File(storageDir, PENDING_FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            val text = file.readText()
            val root = JSONObject(text)
            val out = mutableMapOf<String, PendingPlaylistData>()
            root.keys().forEach { name ->
                val obj = root.getJSONObject(name)
                val backupSourceId = obj.getLong("backupSourceId")
                val custObj = obj.getJSONObject("customizations")
                val custMap = mutableMapOf<String, String>()
                custObj.keys().forEach { k -> custMap[k] = custObj.getString(k) }
                val userData = obj.optJSONArray("userData") ?: JSONArray()
                out[name] = PendingPlaylistData(backupSourceId, custMap, userData)
            }
            out
        }.getOrNull()
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
