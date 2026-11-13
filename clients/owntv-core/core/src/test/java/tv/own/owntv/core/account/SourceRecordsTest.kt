// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class SourceRecordsTest {
    @Test fun stalkerRoundTripKeepsSecretsOutOfData() {
        val s = SourceEntity(id = 7, name = "Портал", type = SourceType.STALKER, url = "https://p.example/c/", mac = "00:1a:79:00:00:01",
            username = "u", password = "p", stalkerSerialNumber = "SN")
        val r = SourceRecords.toRecord(s)!!
        assertEquals("stalker", r.data.getString("type"))
        assertFalse(r.data.toString().contains("00:1A") || r.data.toString().contains("\"p\""))
        assertEquals("00:1A:79:00:00:01", r.secret.getString("mac"))
        val back = SourceRecords.toEntity(r.data, r.secret)!!
        assertEquals(s.copy(id = 0, mac = "00:1A:79:00:00:01", createdAt = back.createdAt), back)
    }

    @Test fun m3uAddressIsSecret() {
        val r = SourceRecords.toRecord(SourceEntity(name = "PL", type = SourceType.M3U, url = "https://x.example/get?token=T"))!!
        assertFalse(r.data.has("url"))
        assertEquals("https://x.example/get?token=T", r.secret.getString("url"))
    }

    @Test fun readsSiteRecordsAndLockedSecrets() {
        val data = JSONObject("""{"type":"xtream","name":"Xt","url":"https://xt.example"}""")
        val e = SourceRecords.toEntity(data, JSONObject("""{"username":"a","password":"b"}"""))!!
        assertEquals(SourceType.XTREAM, e.type); assertEquals("a", e.username)
        // locked: keeps what this device already has
        val kept = SourceRecords.toEntity(data, null, base = e.copy(id = 3))!!
        assertEquals("b", kept.password); assertEquals(3, kept.id)
        assertNull(SourceRecords.toEntity(JSONObject("""{"type":"m3u","name":"x"}"""), null)) // M3U address locked, nothing local
        assertNull(SourceRecords.toEntity(JSONObject("""{"type":"epg","url":"https://e"}"""), null))
        assertNull(SourceRecords.typeOf(SourceEntity(name = "f", type = SourceType.LOCAL_BACKUP, url = "file")))
    }

    @Test fun fingerprintChangesWithCredentials() {
        val a = SourceEntity(name = "x", type = SourceType.XTREAM, url = "u", password = "1")
        assertTrue(SourceRecords.fingerprint(a) != SourceRecords.fingerprint(a.copy(password = "2")))
        assertEquals(SourceRecords.fingerprint(a), SourceRecords.fingerprint(a.copy(id = 9)))
    }
}
