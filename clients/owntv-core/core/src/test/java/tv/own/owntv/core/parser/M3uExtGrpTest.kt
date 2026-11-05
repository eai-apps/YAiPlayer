package tv.own.owntv.core.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `#EXTGRP:` names the group of the entries after it; an entry's own `group-title` still wins. */
class M3uExtGrpTest {

    private fun entries(text: String): List<M3uEntry> = runBlocking {
        val out = mutableListOf<M3uEntry>()
        M3uParser().parse(text.toByteArray().inputStream()) { out += it }
        out
    }

    @Test
    fun extgrpBetweenInfAndUrlSetsTheGroup() {
        val e = entries("#EXTM3U\n#EXTINF:-1,One\n#EXTGRP:News\nhttp://x/1\n")
        assertEquals("News", e.single().groupTitle)
    }

    @Test
    fun extgrpBeforeInfAppliesUntilTheNextExtgrp() {
        val e = entries(
            "#EXTM3U\n#EXTGRP:News\n#EXTINF:-1,One\nhttp://x/1\n#EXTINF:-1,Two\nhttp://x/2\n" +
                "#EXTGRP:Sport\n#EXTINF:-1,Three\nhttp://x/3\n",
        )
        assertEquals(listOf("News", "News", "Sport"), e.map { it.groupTitle })
    }

    @Test
    fun anEntrysOwnGroupTitleWins() {
        val e = entries("#EXTM3U\n#EXTGRP:News\n#EXTINF:-1 group-title=\"Kids\",One\nhttp://x/1\n")
        assertEquals("Kids", e.single().groupTitle)
    }

    @Test
    fun noExtgrpLeavesTheGroupEmpty() {
        assertNull(entries("#EXTM3U\n#EXTINF:-1,One\nhttp://x/1\n").single().groupTitle)
    }
}
