// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioCoverTest {
    @Test fun splitsArtistAndTitleAndDropsTheYear() {
        assertEquals("Double Key" to "We Love To Love", RadioCover.split("Double Key - We Love To Love - 1990"))
    }

    @Test fun titleWithoutArtistGivesNothing() {
        assertNull(RadioCover.split("Morning show"))
    }
}
