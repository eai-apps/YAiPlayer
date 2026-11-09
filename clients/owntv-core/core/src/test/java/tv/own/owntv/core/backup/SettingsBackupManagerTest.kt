package tv.own.owntv.core.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.backup.SettingsBackupManager.ValidationResult

class SettingsBackupManagerTest {

    private fun backup(version: Int = 1, app: String = "yaiplayer"): JSONObject = JSONObject().apply {
        put("app", app)
        put("formatVersion", version)
        put("profiles", JSONArray().put(JSONObject().put("name", "Parent")).put(JSONObject().put("name", "Kids")))
        put("playlists", JSONArray().put(JSONObject().put("name", "Home TV").put("type", "M3U")))
        put("epgSources", JSONArray().put(JSONObject().put("name", "x").put("url", "https://example.invalid/x.xml")))
        put(
            "userData",
            JSONArray()
                .put(JSONObject().put("kind", "fav"))
                .put(JSONObject().put("kind", "fav"))
                .put(JSONObject().put("kind", "order")),
        )
    }

    @Test
    fun `valid backup is summarised`() {
        val r = SettingsBackupManager.validate(backup().toString())
        assertTrue(r is ValidationResult.Success)
        val s = (r as ValidationResult.Success).summary
        assertEquals(2, s.profileCount)
        assertEquals(1, s.playlistCount)
        assertEquals(1, s.epgSourceCount)
        assertEquals(2, s.favoriteCount)
    }

    @Test
    fun `blank or non json is invalid`() {
        assertEquals(ValidationResult.InvalidFormat, SettingsBackupManager.validate(""))
        assertEquals(ValidationResult.InvalidFormat, SettingsBackupManager.validate("not json"))
    }

    @Test
    fun `foreign app is invalid`() {
        assertEquals(ValidationResult.InvalidFormat, SettingsBackupManager.validate(backup(app = "other").toString()))
    }

    @Test
    fun `missing or zero version is invalid`() {
        assertEquals(ValidationResult.InvalidFormat, SettingsBackupManager.validate(backup(version = 0).toString()))
    }

    @Test
    fun `newer version is reported`() {
        val r = SettingsBackupManager.validate(backup(version = 99).toString())
        assertEquals(ValidationResult.NewerVersion(99), r)
    }
}
