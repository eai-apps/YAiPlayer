package tv.own.owntv.core.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SettingsBackupManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun sampleValidBackupJson(
        version: Int = 1,
        app: String = "yaiplayer",
    ): String {
        val root = JSONObject()
        root.put("app", app)
        root.put("formatVersion", version)
        root.put("exportedAt", 1731110000000L)

        val profiles = JSONArray()
        profiles.put(JSONObject().apply {
            put("id", 1L)
            put("name", "Parent")
            put("avatarId", 1)
            put("isKids", false)
            put("quickStartMode", "LAST_CHANNEL")
        })
        profiles.put(JSONObject().apply {
            put("id", 2L)
            put("name", "Kids")
            put("avatarId", 2)
            put("isKids", true)
            put("quickStartMode", "GUIDE")
        })
        root.put("profiles", profiles)

        val playlists = JSONArray()
        playlists.put(JSONObject().apply {
            put("id", 10L)
            put("name", "Home TV")
            put("type", "M3U")
        })
        root.put("playlists", playlists)

        val epgSources = JSONArray()
        epgSources.put(JSONObject().apply {
            put("name", "EPG 1")
            put("url", "https://epg.example/1.xml")
            put("enabled", true)
        })
        epgSources.put(JSONObject().apply {
            put("name", "EPG 2")
            put("url", "https://epg.example/2.xml")
            put("enabled", true)
        })
        root.put("epgSources", epgSources)

        val settings = JSONObject().apply {
            put("theme", "dark")
            put("player_engine", "EXO")
        }
        root.put("settings", settings)

        val customizations = JSONObject().apply {
            put("cust_1_LIVE", JSONObject().apply {
                put("hiddenCats", JSONArray().apply {
                    put("10:cat_hidden1")
                    put("10:cat_hidden2")
                })
                put("catPos", JSONObject().apply {
                    put("10:cat_pos1", 0)
                })
            }.toString())
        }
        root.put("customizations", customizations)

        val userData = JSONArray().apply {
            put(JSONObject().apply {
                put("p", 1L)
                put("src", 10L)
                put("t", "LIVE")
                put("name", "Channel 1")
                put("rid", "ch1")
                put("kind", "fav")
                put("at", 1731110000000L)
            })
            put(JSONObject().apply {
                put("p", 1L)
                put("src", 10L)
                put("t", "LIVE")
                put("name", "Channel 2")
                put("rid", "ch2")
                put("kind", "fav")
                put("at", 1731110001000L)
            })
            put(JSONObject().apply {
                put("p", 1L)
                put("src", 10L)
                put("t", "LIVE")
                put("name", "Channel 3")
                put("rid", "ch3")
                put("kind", "order")
                put("ctx", "10:news")
                put("pos", 0)
            })
        }
        root.put("userData", userData)

        return root.toString()
    }

    @Test
    fun testFilenameFormat() {
        // Filename matches pattern yaiplayer-backup-YYYYMMDD.json
        val fakeManager = SettingsBackupManager(
            profileDao = FakeProfileDao(),
            sourceDao = FakeSourceDao(),
            settingsRepository = FakeSettingsRepository(),
            customizationStore = FakeCustomizationStore(),
            epgSourceStore = FakeEpgSourceStore(),
            userDataResolver = FakeUserDataResolver(),
            contentOrderDao = FakeContentOrderDao(),
            favoriteDao = FakeFavoriteDao(),
            db = FakeDb(),
            storageDir = folder.root,
        )
        val filename = fakeManager.formatFilename(1731110400000L)
        assertTrue(filename.startsWith("yaiplayer-backup-"))
        assertTrue(filename.endsWith(".json"))
        assertEquals(29, filename.length) // yaiplayer-backup-YYYYMMDD.json = 17 + 8 + 5 = 30 or 29
    }

    @Test
    fun testValidateJson_validFormatAndSummaryCounts() {
        val manager = SettingsBackupManager(
            profileDao = FakeProfileDao(),
            sourceDao = FakeSourceDao(),
            settingsRepository = FakeSettingsRepository(),
            customizationStore = FakeCustomizationStore(),
            epgSourceStore = FakeEpgSourceStore(),
            userDataResolver = FakeUserDataResolver(),
            contentOrderDao = FakeContentOrderDao(),
            favoriteDao = FakeFavoriteDao(),
            db = FakeDb(),
            storageDir = folder.root,
        )

        val json = sampleValidBackupJson()
        val result = manager.validateJson(json)

        assertTrue(result is SettingsBackupManager.ValidationResult.Success)
        val success = result as SettingsBackupManager.ValidationResult.Success
        assertEquals(2, success.summary.profileCount)
        assertEquals(1, success.summary.playlistCount)
        assertEquals(2, success.summary.epgSourceCount)
        assertEquals(2, success.summary.favoriteCount)
        assertEquals(3, success.summary.categoryCount) // 2 hidden + 1 pos
    }

    @Test
    fun testValidateJson_corruptJson_rejectedGracefully() {
        val manager = SettingsBackupManager(
            profileDao = FakeProfileDao(),
            sourceDao = FakeSourceDao(),
            settingsRepository = FakeSettingsRepository(),
            customizationStore = FakeCustomizationStore(),
            epgSourceStore = FakeEpgSourceStore(),
            userDataResolver = FakeUserDataResolver(),
            contentOrderDao = FakeContentOrderDao(),
            favoriteDao = FakeFavoriteDao(),
            db = FakeDb(),
            storageDir = folder.root,
        )

        assertEquals(SettingsBackupManager.ValidationResult.InvalidFormat, manager.validateJson(""))
        assertEquals(SettingsBackupManager.ValidationResult.InvalidFormat, manager.validateJson("{ broken json"))
        assertEquals(SettingsBackupManager.ValidationResult.InvalidFormat, manager.validateJson("""{"app":"other"}"""))
        assertEquals(SettingsBackupManager.ValidationResult.InvalidFormat, manager.validateJson("""{"app":"yaiplayer","formatVersion":0}"""))
    }

    @Test
    fun testValidateJson_newerVersion_rejectedWithPromptToUpdate() {
        val manager = SettingsBackupManager(
            profileDao = FakeProfileDao(),
            sourceDao = FakeSourceDao(),
            settingsRepository = FakeSettingsRepository(),
            customizationStore = FakeCustomizationStore(),
            epgSourceStore = FakeEpgSourceStore(),
            userDataResolver = FakeUserDataResolver(),
            contentOrderDao = FakeContentOrderDao(),
            favoriteDao = FakeFavoriteDao(),
            db = FakeDb(),
            storageDir = folder.root,
        )

        val newerJson = sampleValidBackupJson(version = 2)
        val result = manager.validateJson(newerJson)

        assertTrue(result is SettingsBackupManager.ValidationResult.NewerVersion)
        assertEquals(2, (result as SettingsBackupManager.ValidationResult.NewerVersion).version)
    }

    @Test
    fun testExport_excludesAllSecrets() {
        val profileDao = FakeProfileDao(
            listOf(
                ProfileEntity(id = 1L, name = "Dad", avatarColor = 0, pinHash = "SECRET_HASH_1234"),
            ),
        )
        val sourceDao = FakeSourceDao(
            listOf(
                SourceEntity(
                    id = 10L,
                    name = "My IPTV",
                    type = SourceType.XTREAM,
                    url = "http://provider.test/get.php?username=usr&password=SECRET_PASSWORD_999",
                    username = "usr",
                    password = "SECRET_PASSWORD_999",
                    mac = "00:1A:79:AA:BB:CC",
                    stalkerSerialNumber = "SECRET_SERIAL",
                ),
            ),
        )
        val settingsRepo = FakeSettingsRepository(
            rawSettings = mapOf(
                "theme" to "dark",
                "proxy_pass" to "SECRET_PROXY_PASS",
                "proxy_pass_enc" to "SECRET_ENC",
                "tmdb_api_key" to "SECRET_TMDB_KEY",
                "open_subtitles_api_key" to "SECRET_OPENSUB_KEY",
            ),
        )

        val manager = SettingsBackupManager(
            profileDao = profileDao,
            sourceDao = sourceDao,
            settingsRepository = settingsRepo,
            customizationStore = FakeCustomizationStore(),
            epgSourceStore = FakeEpgSourceStore(),
            userDataResolver = FakeUserDataResolver(),
            contentOrderDao = FakeContentOrderDao(),
            favoriteDao = FakeFavoriteDao(),
            db = FakeDb(),
            storageDir = folder.root,
        )

        val exported = kotlinx.coroutines.runBlocking { manager.exportJson() }

        // Must NOT contain any secret data
        assertFalse(exported.contains("SECRET_HASH_1234"))
        assertFalse(exported.contains("SECRET_PASSWORD_999"))
        assertFalse(exported.contains("SECRET_SERIAL"))
        assertFalse(exported.contains("00:1A:79:AA:BB:CC"))
        assertFalse(exported.contains("SECRET_PROXY_PASS"))
        assertFalse(exported.contains("SECRET_TMDB_KEY"))
        assertFalse(exported.contains("SECRET_OPENSUB_KEY"))

        // Playlists must only have id, name, type
        val root = JSONObject(exported)
        val playlists = root.getJSONArray("playlists")
        assertEquals(1, playlists.length())
        val pl = playlists.getJSONObject(0)
        assertEquals(10L, pl.getLong("id"))
        assertEquals("My IPTV", pl.getString("name"))
        assertEquals("XTREAM", pl.getString("type"))
        assertFalse(pl.has("url"))
        assertFalse(pl.has("username"))
        assertFalse(pl.has("password"))
        assertFalse(pl.has("mac"))

        // Profiles must not have pinHash
        val profiles = root.getJSONArray("profiles")
        assertEquals(1, profiles.length())
        val pr = profiles.getJSONObject(0)
        assertFalse(pr.has("pinHash"))
    }

    @Test
    fun testRestoreAndConnectPlaylistLater_matchesByName() {
        val targetProfileDao = FakeProfileDao()
        val targetSourceDao = FakeSourceDao()
        val targetSettingsRepo = FakeSettingsRepository()
        val targetCustStore = FakeCustomizationStore()
        val targetEpgStore = FakeEpgSourceStore()
        val targetUserDataResolver = FakeUserDataResolver()

        val manager = SettingsBackupManager(
            profileDao = targetProfileDao,
            sourceDao = targetSourceDao,
            settingsRepository = targetSettingsRepo,
            customizationStore = targetCustStore,
            epgSourceStore = targetEpgStore,
            userDataResolver = targetUserDataResolver,
            contentOrderDao = FakeContentOrderDao(),
            favoriteDao = FakeFavoriteDao(),
            db = FakeDb(),
            storageDir = folder.root,
        )

        val json = sampleValidBackupJson()

        // 1. Restore on target device before playlist is connected
        val restoreResult = kotlinx.coroutines.runBlocking { manager.restoreFromJson(json) }
        assertTrue(restoreResult.isSuccess)

        // Profiles restored
        val profiles = kotlinx.coroutines.runBlocking { targetProfileDao.getAllOnce() }
        assertEquals(2, profiles.size)
        assertEquals("Parent", profiles[0].name)
        assertEquals("Kids", profiles[1].name)

        // EPG sources restored
        val epgs = kotlinx.coroutines.runBlocking { targetEpgStore.getAll() }
        assertEquals(2, epgs.size)

        // Pending playlist backup data must be stored because "Home TV" is not yet added
        val pendingFile = File(folder.root, "pending_backup_playlists.json")
        assertTrue(pendingFile.exists())

        // 2. User connects playlist "Home TV" on device (gets local sourceId = 42)
        val connectedSource = SourceEntity(
            id = 42L,
            name = "Home TV",
            type = SourceType.M3U,
            url = "http://my-home-server/tv.m3u",
        )
        kotlinx.coroutines.runBlocking {
            manager.onSourceConnected(connectedSource)
        }

        // Pending file is cleared after matching
        assertFalse(File(folder.root, "pending_backup_playlists.json").readText().contains("Home TV"))

        // Customizations remapped from 10: to 42:
        val custMap = kotlinx.coroutines.runBlocking { targetCustStore.exportAll() }
        assertTrue(custMap.isNotEmpty())
        val custVal = custMap.values.first()
        assertTrue("Category hidden key remapped to 42", custVal.contains("42:cat_hidden1"))

        // UserData remapped to src 42
        assertEquals(3, targetUserDataResolver.importedItems.size)
        for (item in targetUserDataResolver.importedItems) {
            assertEquals(42L, item.optLong("src"))
        }
    }

    @Test
    fun testSettingsTransferServer_exportAndRestoreFlow() {
        val server = SettingsTransferServer()
        val exportState = server.startExport(
            jsonContent = """{"app":"yaiplayer"}""",
            filename = "yaiplayer-backup-test.json",
            timeoutMs = 60000L,
        )
        assertNotNull(exportState)
        val exportUrl = exportState!!.url
        assertNotNull(exportState.qrBitmap)

        // Test GET export
        val conn = URL(exportUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals("""{"app":"yaiplayer"}""", body)
        conn.disconnect()
        server.stop()

        // Test Restore
        val latch = CountDownLatch(1)
        var receivedPayload: String? = null
        val restoreState = server.startRestore(
            timeoutMs = 60000L,
            onBackupReceived = { payload ->
                receivedPayload = payload
                latch.countDown()
            },
        )
        assertNotNull(restoreState)
        val restoreUrl = restoreState!!.url

        // GET restore HTML form
        val getConn = URL(restoreUrl).openConnection() as HttpURLConnection
        getConn.requestMethod = "GET"
        assertEquals(200, getConn.responseCode)
        val html = getConn.inputStream.bufferedReader().readText()
        assertTrue(html.contains("Загрузить резервную копию"))
        getConn.disconnect()

        // POST upload JSON
        val postConn = URL(restoreUrl).openConnection() as HttpURLConnection
        postConn.requestMethod = "POST"
        postConn.doOutput = true
        postConn.setRequestProperty("Content-Type", "application/json")
        val sampleJson = """{"app":"yaiplayer","formatVersion":1}"""
        postConn.outputStream.use { it.write(sampleJson.toByteArray(Charsets.UTF_8)) }
        assertEquals(200, postConn.responseCode)
        postConn.disconnect()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(sampleJson, receivedPayload)
        server.stop()
    }
}

// Minimal In-Memory Fakes for Testing

private class FakeProfileDao(initial: List<ProfileEntity> = emptyList()) : tv.own.owntv.core.database.dao.ProfileDao {
    private val profiles = initial.toMutableList()
    private var nextId = (profiles.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(profile: ProfileEntity): Long {
        val id = if (profile.id > 0) profile.id else nextId++
        val p = profile.copy(id = id)
        profiles.add(p)
        return id
    }

    override suspend fun getAllOnce(): List<ProfileEntity> = profiles.toList()
    override suspend fun getById(id: Long): ProfileEntity? = profiles.firstOrNull { it.id == id }
    override fun observeAll(): kotlinx.coroutines.flow.Flow<List<ProfileEntity>> = kotlinx.coroutines.flow.flowOf(profiles)
    override suspend fun update(profile: ProfileEntity) {
        val idx = profiles.indexOfFirst { it.id == profile.id }
        if (idx >= 0) profiles[idx] = profile
    }
    override suspend fun delete(profile: ProfileEntity) { profiles.removeAll { it.id == profile.id } }
    override suspend fun deleteById(id: Long) { profiles.removeAll { it.id == id } }
    override suspend fun count(): Int = profiles.size
}

private class FakeSourceDao(initial: List<SourceEntity> = emptyList()) : tv.own.owntv.core.database.dao.SourceDao {
    private val sources = initial.toMutableList()
    private var nextId = (sources.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun insert(source: SourceEntity): Long {
        val id = if (source.id > 0) source.id else nextId++
        sources.add(source.copy(id = id))
        return id
    }

    override suspend fun getAllOnce(): List<SourceEntity> = sources.toList()
    override suspend fun getById(id: Long): SourceEntity? = sources.firstOrNull { it.id == id }
    override fun observeForProfile(profileId: Long): kotlinx.coroutines.flow.Flow<List<SourceEntity>> = kotlinx.coroutines.flow.flowOf(sources)
    override fun observeAll(): kotlinx.coroutines.flow.Flow<List<SourceEntity>> = kotlinx.coroutines.flow.flowOf(sources)
    override suspend fun update(source: SourceEntity) {
        val idx = sources.indexOfFirst { it.id == source.id }
        if (idx >= 0) sources[idx] = source
    }
    override suspend fun delete(source: SourceEntity) { sources.removeAll { it.id == source.id } }
    override suspend fun link(crossRef: tv.own.owntv.core.database.entity.ProfileSourceCrossRef) {}
    override suspend fun unlink(profileId: Long, sourceId: Long) {}
    override suspend fun allLinks(): List<tv.own.owntv.core.database.entity.ProfileSourceCrossRef> = emptyList()
    override suspend fun linksForProfile(profileId: Long): List<tv.own.owntv.core.database.entity.ProfileSourceCrossRef> = emptyList()
    override suspend fun unlinkAllForProfile(profileId: Long) {}
    override suspend fun unlinkAllForSource(sourceId: Long) {}
}

private class FakeSettingsRepository(
    private val rawSettings: Map<String, Any> = emptyMap(),
) : tv.own.owntv.core.settings.SettingsRepository(FakeContext()) {
    private val startupModes = mutableMapOf<String, String>()

    override suspend fun exportSettings(): JSONObject = JSONObject(rawSettings)
    override suspend fun exportStartupModes(): JSONObject = JSONObject(startupModes as Map<*, *>)
    override suspend fun setStartupMode(profileId: Long, mode: String) {
        startupModes[profileId.toString()] = mode
    }
    override suspend fun importSettings(o: JSONObject, keepDeviceSettings: Boolean): tv.own.owntv.core.settings.SettingsImportResult {
        return tv.own.owntv.core.settings.SettingsImportResult()
    }
}

private class FakeCustomizationStore : tv.own.owntv.core.customize.CustomizationStore(FakeContext()) {
    private val map = mutableMapOf<String, String>()

    override suspend fun exportAll(): Map<String, String> = map.toMap()
    override suspend fun mergeAll(entries: Map<String, String>) {
        map.putAll(entries)
    }
}

private class FakeEpgSourceStore : tv.own.owntv.core.epg.EpgSourceStore(FakeContext()) {
    private val list = mutableListOf<tv.own.owntv.core.epg.EpgSource>()
    private var nextId = -1L

    override suspend fun getAll(): List<tv.own.owntv.core.epg.EpgSource> = list.toList()
    override suspend fun ensure(name: String, url: String, userAgent: String?): tv.own.owntv.core.epg.EpgSource {
        list.firstOrNull { it.url == url }?.let { return it }
        val src = tv.own.owntv.core.epg.EpgSource(id = nextId--, name = name, url = url)
        list.add(src)
        return src
    }
}

private class FakeUserDataResolver : tv.own.owntv.core.backup.UserDataResolver(
    FakeContext(),
    FakeChannelDao(),
    FakeMovieDao(),
    FakeSeriesDao(),
    FakeProfileDao(),
    FakeFavoriteDao(),
    FakeHistoryDao(),
    FakeProgressDao(),
    FakeContentOrderDao(),
    FakeCustomCategoryDao(),
    FakeSeriesSortOrderDao(),
    FakeTombstoneDao(),
    FakeDb(),
) {
    val importedItems = mutableListOf<JSONObject>()

    override suspend fun exportAll(kinds: Set<String>): JSONArray = JSONArray()
    override suspend fun importAll(entries: JSONArray?): Int {
        if (entries != null) {
            for (i in 0 until entries.length()) {
                importedItems.add(entries.getJSONObject(i))
            }
        }
        return 0
    }
    override suspend fun resolvePending(): Int = 0
}

private class FakeContentOrderDao : tv.own.owntv.core.database.dao.ContentOrderDao {
    override suspend fun insertAll(rows: List<tv.own.owntv.core.database.entity.ContentOrderEntity>) {}
    override suspend fun clearContext(profileId: Long, type: tv.own.owntv.core.model.MediaType, contextKey: String) {}
    override fun observeContextKeys(profileId: Long, type: tv.own.owntv.core.model.MediaType): kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun dropListOrdersExcept(keep: String) {}
    override suspend fun exists(profileId: Long, type: tv.own.owntv.core.model.MediaType, contextKey: String, itemId: Long): Boolean = false
    override suspend fun count(): Int = 0
    override suspend fun purgeSnapshotOrphans(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemIds: List<Long>) {}
    override suspend fun movedItemIds(profileId: Long, type: tv.own.owntv.core.model.MediaType, contextKey: String): List<Long> = emptyList()
    override suspend fun exportRowsForSource(sourceId: Long): List<tv.own.owntv.core.database.dao.ContentOrderExportRow> = emptyList()
}

private class FakeFavoriteDao : tv.own.owntv.core.database.dao.FavoriteDao {
    override suspend fun add(favorite: tv.own.owntv.core.database.entity.FavoriteEntity) {}
    override suspend fun remove(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long) {}
    override suspend fun removeIfOlderThan(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long, at: Long): Int = 0
    override fun isFavorite(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun addedAt(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long): Long? = null
    override suspend fun exists(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long): Boolean = false
    override fun count(profileId: Long, type: tv.own.owntv.core.model.MediaType): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(0)
    override fun observeFavoriteIds(profileId: Long, type: tv.own.owntv.core.model.MediaType): kotlinx.coroutines.flow.Flow<List<Long>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getAllOnce(): List<tv.own.owntv.core.database.entity.FavoriteEntity> = emptyList()
    override suspend fun exportRowsForSource(sourceId: Long): List<tv.own.owntv.core.database.dao.UserDataExportRow> = emptyList()
    override suspend fun purgeSnapshotOrphan(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long) {}
}

private class FakeChannelDao : tv.own.owntv.core.database.dao.ChannelDao {
    override suspend fun getById(id: Long): tv.own.owntv.core.database.entity.ChannelEntity? = null
    override suspend fun findByRemote(sourceId: Long, remoteId: String): tv.own.owntv.core.database.entity.ChannelEntity? = null
    override suspend fun findByName(sourceId: Long, name: String): tv.own.owntv.core.database.entity.ChannelEntity? = null
    override suspend fun clearSource(sourceId: Long) {}
    override suspend fun insertAll(channels: List<tv.own.owntv.core.database.entity.ChannelEntity>): List<Long> = emptyList()
    override suspend fun deleteByIds(ids: List<Long>) {}
    override suspend fun searchByName(query: String, limit: Int): List<tv.own.owntv.core.database.entity.ChannelEntity> = emptyList()
    override fun observeForCategory(sourceId: Long, categoryId: Long): kotlinx.coroutines.flow.Flow<List<tv.own.owntv.core.database.entity.ChannelEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override fun observeAll(sourceId: Long): kotlinx.coroutines.flow.Flow<List<tv.own.owntv.core.database.entity.ChannelEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun count(): Int = 0
}

private class FakeMovieDao : tv.own.owntv.core.database.dao.MovieDao {
    override suspend fun getById(id: Long): tv.own.owntv.core.database.entity.MovieEntity? = null
    override suspend fun findByRemote(sourceId: Long, remoteId: String): tv.own.owntv.core.database.entity.MovieEntity? = null
    override suspend fun findByName(sourceId: Long, name: String): tv.own.owntv.core.database.entity.MovieEntity? = null
    override suspend fun clearSource(sourceId: Long) {}
    override suspend fun insertAll(movies: List<tv.own.owntv.core.database.entity.MovieEntity>): List<Long> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun exportRowsForSource(sourceId: Long): List<tv.own.owntv.core.database.dao.UserDataExportRow> = emptyList()
}

private class FakeSeriesDao : tv.own.owntv.core.database.dao.SeriesDao {
    override suspend fun getSeriesById(id: Long): tv.own.owntv.core.database.entity.SeriesEntity? = null
    override suspend fun findSeriesByRemote(sourceId: Long, remoteId: String): tv.own.owntv.core.database.entity.SeriesEntity? = null
    override suspend fun findSeriesByName(sourceId: Long, name: String): tv.own.owntv.core.database.entity.SeriesEntity? = null
    override suspend fun getEpisodeById(id: Long): tv.own.owntv.core.database.entity.EpisodeEntity? = null
    override suspend fun findEpisodeByRemote(seriesId: Long, remoteId: String): tv.own.owntv.core.database.entity.EpisodeEntity? = null
    override suspend fun findEpisodeByNumber(seriesId: Long, season: Int, episode: Int): tv.own.owntv.core.database.entity.EpisodeEntity? = null
    override suspend fun clearSource(sourceId: Long) {}
    override suspend fun insertSeries(series: List<tv.own.owntv.core.database.entity.SeriesEntity>): List<Long> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun exportRowsForSource(sourceId: Long): List<tv.own.owntv.core.database.dao.UserDataExportRow> = emptyList()
    override suspend fun invalidateEpisodeCaches(sourceId: Long) {}
}

private class FakeHistoryDao : tv.own.owntv.core.database.dao.HistoryDao {
    override suspend fun exists(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long): Boolean = false
    override suspend fun watchedAt(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long): Long? = null
    override suspend fun exportRowsForSource(sourceId: Long): List<tv.own.owntv.core.database.dao.UserDataExportRow> = emptyList()
    override suspend fun purgeSnapshotOrphan(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long) {}
}

private class FakeProgressDao : tv.own.owntv.core.database.dao.ProgressDao {
    override suspend fun get(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long): tv.own.owntv.core.database.entity.PlaybackProgressEntity? = null
    override suspend fun exportRowsForSource(sourceId: Long): List<tv.own.owntv.core.database.dao.UserDataExportRow> = emptyList()
    override suspend fun purgeSnapshotOrphan(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemId: Long) {}
}

private class FakeCustomCategoryDao : tv.own.owntv.core.database.dao.CustomCategoryDao {
    override suspend fun exists(profileId: Long, type: tv.own.owntv.core.model.MediaType, contextKey: String, itemId: Long): Boolean = false
    override suspend fun exportRowsForSource(sourceId: Long): List<tv.own.owntv.core.database.dao.CustomCategoryMemberExportRow> = emptyList()
    override suspend fun purgeSnapshotOrphans(profileId: Long, type: tv.own.owntv.core.model.MediaType, itemIds: List<Long>) {}
}

private class FakeSeriesSortOrderDao : tv.own.owntv.core.database.dao.SeriesSortOrderDao {
    override suspend fun findRowId(profileId: Long, seriesId: Long): Long? = null
    override suspend fun exportRowsForSource(sourceId: Long): List<tv.own.owntv.core.database.dao.SeriesSortOrderExportRow> = emptyList()
    override suspend fun purgeSnapshotOrphan(profileId: Long, seriesId: Long) {}
}

private class FakeTombstoneDao : tv.own.owntv.core.database.dao.TombstoneDao {
    override suspend fun record(profileId: Long, kind: String, identity: String, at: Long) {}
    override suspend fun count(): Int = 0
    override suspend fun prune(keep: Int) {}
    override suspend fun deleteForProfiles(profileIds: List<Long>) {}
    override suspend fun getAllOnce(): List<tv.own.owntv.core.database.entity.TombstoneEntity> = emptyList()
    override suspend fun deletedAt(profileId: Long, kind: String, identity: String): Long? = null
}

private class FakeDb : tv.own.owntv.core.database.OwnTVDatabase() {
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
        throw UnsupportedOperationException()
    }
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
        return androidx.room.InvalidationTracker(this, emptyMap(), emptyMap(), "profiles")
    }
    override fun clearAllTables() {}
    override fun channelDao(): tv.own.owntv.core.database.dao.ChannelDao = FakeChannelDao()
    override fun movieDao(): tv.own.owntv.core.database.dao.MovieDao = FakeMovieDao()
    override fun seriesDao(): tv.own.owntv.core.database.dao.SeriesDao = FakeSeriesDao()
    override fun profileDao(): tv.own.owntv.core.database.dao.ProfileDao = FakeProfileDao()
    override fun sourceDao(): tv.own.owntv.core.database.dao.SourceDao = FakeSourceDao()
    override fun favoriteDao(): tv.own.owntv.core.database.dao.FavoriteDao = FakeFavoriteDao()
    override fun historyDao(): tv.own.owntv.core.database.dao.HistoryDao = FakeHistoryDao()
    override fun progressDao(): tv.own.owntv.core.database.dao.ProgressDao = FakeProgressDao()
    override fun contentOrderDao(): tv.own.owntv.core.database.dao.ContentOrderDao = FakeContentOrderDao()
    override fun customCategoryDao(): tv.own.owntv.core.database.dao.CustomCategoryDao = FakeCustomCategoryDao()
    override fun seriesSortOrderDao(): tv.own.owntv.core.database.dao.SeriesSortOrderDao = FakeSeriesSortOrderDao()
    override fun tombstoneDao(): tv.own.owntv.core.database.dao.TombstoneDao = FakeTombstoneDao()
    override fun categoryDao(): tv.own.owntv.core.database.dao.CategoryDao = throw UnsupportedOperationException()
    override fun epgDao(): tv.own.owntv.core.database.dao.EpgDao = throw UnsupportedOperationException()
    override fun reminderDao(): tv.own.owntv.core.database.dao.ReminderDao = throw UnsupportedOperationException()
    override fun recordingDao(): tv.own.owntv.core.database.dao.RecordingDao = throw UnsupportedOperationException()
    override fun subtitleDao(): tv.own.owntv.core.database.dao.SubtitleDao = throw UnsupportedOperationException()
    override fun metadataDao(): tv.own.owntv.core.database.dao.MetadataDao = throw UnsupportedOperationException()
    override fun playbackQuirkDao(): tv.own.owntv.core.database.dao.PlaybackQuirkDao = throw UnsupportedOperationException()
    override fun playbackPrefsDao(): tv.own.owntv.core.database.dao.PlaybackPrefsDao = throw UnsupportedOperationException()
    override fun catalogBackfillDao(): tv.own.owntv.core.database.dao.CatalogBackfillDao = throw UnsupportedOperationException()
    override fun trendingDao(): tv.own.owntv.core.database.dao.TrendingDao = throw UnsupportedOperationException()
}

private class FakeContext : android.content.ContextWrapper(null)
