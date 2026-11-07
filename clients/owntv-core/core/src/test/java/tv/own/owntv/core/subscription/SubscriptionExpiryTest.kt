package tv.own.owntv.core.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionExpiryTest {
    private val day = 86_400_000L

    @Test fun daysRoundUp() {
        assertEquals(0, daysUntil(1000, 1000))
        assertEquals(1, daysUntil(1000 + 1, 1000))
        assertEquals(2, daysUntil(1000 + day + 1, 1000))
        assertEquals(7, daysUntil(1000 + 7 * day, 1000))
    }

    @Test fun readsCommonPortalDates() {
        assertNotNull(parseExpiryText("2026-11-12"))
        assertNotNull(parseExpiryText("2026-11-12 23:59:59"))
        assertNotNull(parseExpiryText("12.11.2026"))
        assertNotNull(parseExpiryText("1794000000"))
        assertEquals(parseExpiryText("2026-11-12"), parseExpiryText("12.11.2026"))
    }

    @Test fun unknownTextStaysUnknown() {
        assertNull(parseExpiryText("Unlimited"))
        assertNull(parseExpiryText(""))
    }
}
