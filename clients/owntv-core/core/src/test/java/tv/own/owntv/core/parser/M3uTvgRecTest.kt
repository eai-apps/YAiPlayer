package tv.own.owntv.core.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `tvg-rec="N"` (days of archive) is read as an archive, and an explicit `catchup` still wins. */
class M3uTvgRecTest {

    private fun parse(text: String): List<M3uEntry> = runBlocking {
        val out = mutableListOf<M3uEntry>()
        M3uParser().parse(text.byteInputStream()) { out += it }
        out
    }

    @Test
    fun tvgRecWithFlussonicUrlMeansFlussonicArchive() {
        val e = parse(
            "#EXTM3U\n#EXTINF:-1 tvg-id=\"r1\" tvg-rec=\"7\",Channel One\nhttp://h/ch1/index.m3u8?token=x\n",
        ).single()
        assertEquals("flussonic", e.catchup)
        assertEquals(7, e.catchupDays)
    }

    @Test
    fun tvgRecWithOtherUrlFallsBackToShift() {
        val e = parse("#EXTM3U\n#EXTINF:-1 tvg-rec=\"3\",Two\nhttp://h/live/two.ts\n").single()
        assertEquals("shift", e.catchup)
        assertEquals(3, e.catchupDays)
    }

    @Test
    fun explicitCatchupWins() {
        val e = parse("#EXTM3U\n#EXTINF:-1 catchup=\"append\" catchup-days=\"2\" tvg-rec=\"9\",Three\nhttp://h/three.m3u8\n").single()
        assertEquals("append", e.catchup)
        assertEquals(2, e.catchupDays)
    }

    @Test
    fun noArchiveAttributesMeansNoArchive() {
        val e = parse("#EXTM3U\n#EXTINF:-1,Four\nhttp://h/four.m3u8\n").single()
        assertNull(e.catchup)
    }
}
