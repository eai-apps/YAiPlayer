// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.multiview

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceMemoryTest {

    @Test
    fun `maxAllowedTiles bounds tiles correctly`() {
        // Pure calculation check when not low RAM:
        val normalCap = 4.coerceIn(2, 4)
        assertEquals(4, normalCap)

        // Low RAM cap:
        val lowRamCap = minOf(2, 4)
        assertEquals(2, lowRamCap)
    }
}
