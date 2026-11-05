package tv.own.owntv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoWidescreenTest {
    private fun applies(
        w: Int, h: Int, enabled: Boolean = true, default: ZoomMode = ZoomMode.FIT,
        current: ZoomMode = ZoomMode.FIT, chose: Boolean = false,
    ) = AutoWidescreen.applies(enabled, default, current, chose, w, h)

    @Test fun sdFourByThreeBecomesWide() {
        assertTrue(applies(720, 576))
        assertTrue(applies(640, 480))
    }

    @Test fun alreadyWideOrFullHdIsLeftAlone() {
        assertFalse(applies(1280, 720))
        assertFalse(applies(1920, 1080))
        assertFalse(applies(1440, 1080))
        assertFalse(applies(3840, 2160))
        assertFalse(applies(1024, 576))
    }

    @Test fun userChoicesWin() {
        assertFalse(applies(720, 576, enabled = false))
        assertFalse(applies(720, 576, chose = true))
        assertFalse(applies(720, 576, default = ZoomMode.FILL))
        assertFalse(applies(720, 576, current = ZoomMode.STRETCH))
    }

    @Test fun unknownSizeIsIgnored() {
        assertFalse(applies(0, 0))
    }
}
