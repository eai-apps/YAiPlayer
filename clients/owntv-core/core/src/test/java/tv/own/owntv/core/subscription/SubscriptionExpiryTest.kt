package tv.own.owntv.core.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.model.SourceType

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

    // --- Unit tests for subscription days calculation (rule 6) ---

    @Test fun calculateDaysNormalDate() {
        val now = 1_700_000_000_000L
        // 10 days left (> 7 days, no warning)
        val res10 = calculateSubscriptionBadge(
            sourceType = SourceType.XTREAM,
            expiryMs = now + 10 * day,
            nowMs = now,
        )
        assertTrue(res10 is SubscriptionBadgeState.Days)
        assertEquals(10, (res10 as SubscriptionBadgeState.Days).days)
        assertFalse(res10.isWarning)

        // 5 days left (<= 7 days, warning accent)
        val res5 = calculateSubscriptionBadge(
            sourceType = SourceType.STALKER,
            expiryMs = now + 5 * day,
            nowMs = now,
        )
        assertTrue(res5 is SubscriptionBadgeState.Days)
        assertEquals(5, (res5 as SubscriptionBadgeState.Days).days)
        assertTrue(res5.isWarning)

        // Stalker date text: 3 days left
        val stalkerDate = "2026-11-20"
        val parsed = parseExpiryText(stalkerDate)!!
        val resStalker = calculateSubscriptionBadge(
            sourceType = SourceType.STALKER,
            expiryText = stalkerDate,
            nowMs = parsed - 3 * day,
        )
        assertTrue(resStalker is SubscriptionBadgeState.Days)
        assertEquals(3, (resStalker as SubscriptionBadgeState.Days).days)
        assertTrue(resStalker.isWarning)
    }

    @Test fun calculateDaysUnlimited() {
        val now = 1_700_000_000_000L
        // Xtream with no expiryMs (null) when account checked
        val resXtream = calculateSubscriptionBadge(
            sourceType = SourceType.XTREAM,
            expiryMs = null,
            isAccountChecked = true,
            nowMs = now,
        )
        assertEquals(SubscriptionBadgeState.Unlimited, resXtream)

        // Stalker with "Unlimited" text
        val resStalker = calculateSubscriptionBadge(
            sourceType = SourceType.STALKER,
            expiryText = "Unlimited",
            isAccountChecked = true,
            nowMs = now,
        )
        assertEquals(SubscriptionBadgeState.Unlimited, resStalker)

        // Explicit isUnlimited = true
        val resExplicit = calculateSubscriptionBadge(
            sourceType = SourceType.XTREAM,
            isUnlimited = true,
            nowMs = now,
        )
        assertEquals(SubscriptionBadgeState.Unlimited, resExplicit)

        // Stalker with no date set when account checked
        val resStalkerNull = calculateSubscriptionBadge(
            sourceType = SourceType.STALKER,
            expiryText = null,
            isAccountChecked = true,
            nowMs = now,
        )
        assertEquals(SubscriptionBadgeState.Unlimited, resStalkerNull)
    }

    @Test fun calculateDaysUnknown() {
        val now = 1_700_000_000_000L
        // M3U source type -> Unknown
        val resM3u = calculateSubscriptionBadge(
            sourceType = SourceType.M3U,
            nowMs = now,
        )
        assertEquals(SubscriptionBadgeState.Unknown, resM3u)

        // Null source type -> Unknown
        val resNull = calculateSubscriptionBadge(
            sourceType = null,
            nowMs = now,
        )
        assertEquals(SubscriptionBadgeState.Unknown, resNull)

        // Source not checked yet -> Unknown
        val resUnchecked = calculateSubscriptionBadge(
            sourceType = SourceType.XTREAM,
            isAccountChecked = false,
            nowMs = now,
        )
        assertEquals(SubscriptionBadgeState.Unknown, resUnchecked)
    }

    @Test fun calculateDaysExpired() {
        val now = 1_700_000_000_000L
        // Expiry date in past (expired) -> 0 days, warning
        val resExpired = calculateSubscriptionBadge(
            sourceType = SourceType.XTREAM,
            expiryMs = now - 1000L,
            nowMs = now,
        )
        assertTrue(resExpired is SubscriptionBadgeState.Days)
        assertEquals(0, (resExpired as SubscriptionBadgeState.Days).days)
        assertTrue((resExpired as SubscriptionBadgeState.Days).isWarning)

        // Stalker date text in past -> 0 days, warning
        val resStalkerExpired = calculateSubscriptionBadge(
            sourceType = SourceType.STALKER,
            expiryText = "2020-01-01",
            nowMs = now,
        )
        assertTrue(resStalkerExpired is SubscriptionBadgeState.Days)
        assertEquals(0, (resStalkerExpired as SubscriptionBadgeState.Days).days)
        assertTrue((resStalkerExpired as SubscriptionBadgeState.Days).isWarning)
    }
}
