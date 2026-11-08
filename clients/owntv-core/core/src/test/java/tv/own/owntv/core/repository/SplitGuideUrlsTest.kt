package tv.own.owntv.core.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A playlist header may advertise several XMLTV feeds in one comma-separated `url-tvg`. */
class SplitGuideUrlsTest {

    @Test
    fun singleUrlIsUnchanged() {
        assertEquals(listOf("https://example.com/guide.xml.gz"), splitGuideUrls(" https://example.com/guide.xml.gz "))
    }

    @Test
    fun commaSeparatedFeedsBecomeSeparateUrls() {
        assertEquals(
            listOf("https://example.com/guide1.xml.gz", "https://example.com/guide2.xml.gz"),
            splitGuideUrls("https://example.com/guide1.xml.gz, https://example.com/guide2.xml.gz"),
        )
    }

    @Test
    fun duplicatesAreDropped() {
        assertEquals(
            listOf("http://example.com/g.xml"),
            splitGuideUrls("http://example.com/g.xml,http://example.com/g.xml"),
        )
    }

    @Test
    fun commaInsideOneUrlIsNotASeparator() {
        val url = "https://example.com/guide.php?ids=1,2,3"
        assertEquals(listOf(url), splitGuideUrls(url))
    }

    @Test
    fun nonHttpMarkerUrlIsNeverSplit() {
        val marker = "owntv-stalker://7,portal"
        assertEquals(listOf(marker), splitGuideUrls(marker))
    }

    @Test
    fun blankIsNoGuide() {
        assertEquals(emptyList<String>(), splitGuideUrls(null))
        assertEquals(emptyList<String>(), splitGuideUrls("   "))
    }

    @Test
    fun newlineSeparatedUrls() {
        val raw = "https://example.com/guide1.xml\nhttps://example.com/guide2.xml.gz"
        assertEquals(
            listOf("https://example.com/guide1.xml", "https://example.com/guide2.xml.gz"),
            splitGuideUrls(raw),
        )
    }

    @Test
    fun mixedNewlinesAndCommas() {
        val raw = "https://example.com/1.xml, https://example.com/2.xml\r\nhttps://example.com/3.xml"
        assertEquals(
            listOf("https://example.com/1.xml", "https://example.com/2.xml", "https://example.com/3.xml"),
            splitGuideUrls(raw),
        )
    }

    @Test
    fun jsonArrayOfUrls() {
        val json = "[\"https://example.com/1.xml\", \"https://example.com/2.xml\"]"
        assertEquals(
            listOf("https://example.com/1.xml", "https://example.com/2.xml"),
            splitGuideUrls(json),
        )
    }

    @Test
    fun joinGuideUrlsRoundtrip() {
        val urls = listOf("https://example.com/1.xml", "https://example.com/2.xml")
        val joined = joinGuideUrls(urls)
        assertEquals(urls, splitGuideUrls(joined))
    }

    @Test
    fun migrationSingleLegacyUrlBecomesFirstItem() {
        val legacy = "https://legacy-provider.org/xmltv.php?user=123"
        val list = splitGuideUrls(legacy)
        assertEquals(1, list.size)
        assertEquals(legacy, list[0])
    }

    @Test
    fun urlValidation() {
        assertTrue(isValidEpgUrl("http://example.com/epg.xml"))
        assertTrue(isValidEpgUrl("https://example.com/epg.xml.gz"))
        assertTrue(isValidEpgUrl("HTTPS://EXAMPLE.COM/EPG"))
        assertTrue(isValidEpgUrl("stalker://42"))
        assertTrue(isValidEpgUrl("owntv-stalker://1"))
        assertFalse(isValidEpgUrl("ftp://example.com/epg.xml"))
        assertFalse(isValidEpgUrl("example.com/epg.xml"))
        assertFalse(isValidEpgUrl(""))
        assertFalse(isValidEpgUrl("   "))
    }
}
