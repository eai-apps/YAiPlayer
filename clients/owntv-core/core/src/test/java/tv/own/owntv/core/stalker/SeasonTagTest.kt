package tv.own.owntv.core.stalker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonTagTest {
    @Test
    fun tagFollowedByLanguageTagsIsASeason() {
        assertTrue(StalkerClient.hasSeasonTag("Под наркозом [S01] [ua] [ru]"))
    }

    @Test
    fun tagAtEndAndEpisodeFormAreSeasons() {
        assertTrue(StalkerClient.hasSeasonTag("Show [S02]"))
        assertTrue(StalkerClient.hasSeasonTag("Show [s03e07]"))
    }

    @Test
    fun filmsWithoutTagAreNotSeasons() {
        assertFalse(StalkerClient.hasSeasonTag("S.W.A.T. 2003"))
        assertFalse(StalkerClient.hasSeasonTag("Season of the Witch [ua]"))
        assertFalse(StalkerClient.hasSeasonTag(null))
    }
}
