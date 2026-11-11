// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioSyncTest {
    @Test fun `newer side wins, first sign-in merges`() {
        assertEquals(RadioSync.Action.MERGE, RadioSync.decide(true, 5, 9))
        assertEquals(RadioSync.Action.PULL, RadioSync.decide(false, 10, 9))
        assertEquals(RadioSync.Action.PUSH, RadioSync.decide(false, 9, 9))
        assertEquals(RadioSync.Action.PUSH, RadioSync.decide(false, 0, 0))
    }

    @Test fun `merge keeps server order, adds local ones once, keeps unknown fields`() {
        val server = JSONArray("""[{"stationuuid":"a","name":"A","votes":7},{"stationuuid":"b","name":"B"}]""")
        val local = JSONArray("""[{"stationuuid":"b","name":"B local"},{"stationuuid":"","url_resolved":"https://x/s","name":"X"},{"stationuuid":"a"}]""")
        val m = RadioSync.mergeFavorites(server, local)
        assertEquals(listOf("a", "b", "https://x/s"), (0 until m.length()).map { RadioSync.key(m.getJSONObject(it)) })
        assertEquals(7, m.getJSONObject(0).getInt("votes"))
        assertEquals("B", m.getJSONObject(1).getString("name"))
    }

    @Test fun `push keeps the site's tracks and playlists`() {
        val server = JSONObject("""{"favorites":[],"recognizedTracks":[{"artist":"x","title":"y"}],"playlists":[{"id":"p1","stations":[]}],"updatedAt":3}""")
        val body = RadioSync.body(JSONArray("""[{"stationuuid":"a"}]"""), server)
        assertEquals(1, body.getJSONArray("favorites").length())
        assertEquals("y", body.getJSONArray("recognizedTracks").getJSONObject(0).getString("title"))
        assertEquals("p1", body.getJSONArray("playlists").getJSONObject(0).getString("id"))
        val empty = RadioSync.body(JSONArray(), JSONObject())
        assertEquals(0, empty.getJSONArray("recognizedTracks").length())
        assertEquals(0, empty.getJSONArray("playlists").length())
    }

    @Test fun `login rule matches the worker`() {
        assertEquals("ivan.petrov", RadioSync.cleanLogin("  Ivan.Petrov "))
        assertEquals("тест_1", RadioSync.cleanLogin("Тест_1"))
        assertNull(RadioSync.cleanLogin("ab"))
        assertNull(RadioSync.cleanLogin("a b c"))
        assertNull(RadioSync.cleanLogin("x".repeat(33)))
        assertEquals(RadioSync.Failure.WRONG_CREDENTIALS, RadioSync.failureFor(401))
        assertEquals(RadioSync.Failure.LOGIN_TAKEN, RadioSync.failureFor(409))
        assertEquals(RadioSync.Failure.TOO_MANY, RadioSync.failureFor(429))
        assertEquals(RadioSync.Failure.UNAVAILABLE, RadioSync.failureFor(503))
    }
}
