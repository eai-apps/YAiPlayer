package tv.own.owntv.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainSectionTest {

    @Test
    fun `favorites sits between Live TV and Sports and is a browse item`() {
        val order = MainSection.browseOrder
        assertEquals(order.indexOf(MainSection.LIVE_TV) + 1, order.indexOf(MainSection.FAVORITES))
        assertEquals(order.indexOf(MainSection.FAVORITES) + 1, order.indexOf(MainSection.SPORTS))
        assertTrue(MainSection.FAVORITES.isBrowse)
        assertTrue(MainSection.FAVORITES in MainSection.allBrowse)
        assertTrue(MainSection.SPORTS.isBrowse)
        assertTrue(MainSection.SPORTS in MainSection.allBrowse)
    }

    @Test
    fun `dynamic mode always shows sports, and shows favorites when live is present`() {
        for (live in listOf(false, true)) for (movies in listOf(false, true)) for (series in listOf(false, true)) {
            assertTrue(MainSection.SPORTS in MainSection.dynamicVisible(live, movies, series))
            assertEquals(live, MainSection.FAVORITES in MainSection.dynamicVisible(live, movies, series))
        }
        assertEquals(setOf(MainSection.HOME, MainSection.SPORTS), MainSection.dynamicVisible(false, false, false))
    }

    @Test
    fun `static mode hides favorites and sports only when the user hid them`() {
        // An existing user's hidden set never names SPORTS or FAVORITES, so the update shows it.
        assertTrue(MainSection.SPORTS in MainSection.staticVisible(setOf("MOVIES", "DOWNLOADS", "GONE")))
        assertTrue(MainSection.FAVORITES in MainSection.staticVisible(setOf("MOVIES", "DOWNLOADS", "GONE")))
        assertFalse(MainSection.SPORTS in MainSection.staticVisible(setOf("SPORTS")))
        assertFalse(MainSection.FAVORITES in MainSection.staticVisible(setOf("FAVORITES")))
    }
}
