// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioParserTest {
    @Test fun `radio browser answer is filtered and folded like the sample does`() {
        val json = """[
          {"stationuuid":"a","name":"Radio Paradise","url_resolved":"https://stream.radioparadise.com/aac-128","bitrate":128,"clickcount":5,"tags":"eclectic,rock"},
          {"stationuuid":"b","name":"radio  paradise","url_resolved":"https://stream.radioparadise.com/aac-320","bitrate":320,"clickcount":1,"tags":"eclectic"},
          {"stationuuid":"c","name":"Broken","url_resolved":"rtsp://x","bitrate":64},
          {"stationuuid":"d","name":"","url_resolved":"https://x/y"},
          {"stationuuid":"e","name":"Jazz FM","url":"http://jazz.example/stream","url_resolved":"","favicon":"javascript:alert(1)"}
        ]"""
        val list = RadioParser.stations(json)
        assertEquals(listOf("b", "e"), list.map { it.id })
        assertEquals(320, list[0].bitrate)
        assertEquals("http://jazz.example/stream", list[1].url)
        assertEquals(null, list[1].favicon)
        assertTrue(RadioParser.stations("not json").isEmpty())
    }

    @Test fun `curated collections come from the site catalogue`() {
        val catalog = """{"curated":[{"id":"morning","name":"Доброе утро","emoji":"☀️","note":"Лёгкие хиты",
          "stations":[{"stationuuid":"ex-rp-main","name":"Radio Paradise — Main Mix","url":"https://stream.radioparadise.com/aac-320","url_resolved":"https://stream.radioparadise.com/aac-320","bitrate":320}]},
          {"id":"empty","name":"Пусто","stations":[]}]}"""
        val c = RadioParser.collections(catalog)
        assertEquals(1, c.size)
        assertEquals("Доброе утро", c[0].name)
        assertEquals("ex-rp-main", c[0].stations.single().id)
    }

    @Test fun `favourites survive their JSON form`() {
        val s = RadioStation("id1", "Name", "https://a/b", null, "https://a/i.png", listOf("pop"), "DE", "MP3", 128, 3)
        assertEquals(s, RadioParser.station(RadioParser.toJson(s)))
    }

    @Test fun `search query matches the sample`() {
        assertEquals(
            "/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=192&tag=russian+rock",
            RadioUrls.searchPath("Russian Rock", null),
        )
        assertEquals(40, RadioGenres.all.size)
        assertEquals("", RadioGenres.all.first().tag)
    }
}
