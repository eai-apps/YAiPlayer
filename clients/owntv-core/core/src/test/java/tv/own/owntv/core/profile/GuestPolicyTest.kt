package tv.own.owntv.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class GuestPolicyTest {

    private fun fakeSource(id: Long, type: SourceType): SourceEntity =
        SourceEntity(
            id = id,
            name = "Test $id",
            type = type,
            url = "http://test/$id",
        )

    @Test
    fun `isPlaylist identifies M3U, Xtream and LocalBackup`() {
        assertTrue(GuestPolicy.isPlaylist(SourceType.M3U))
        assertTrue(GuestPolicy.isPlaylist(SourceType.XTREAM))
        assertTrue(GuestPolicy.isPlaylist(SourceType.LOCAL_BACKUP))
        assertFalse(GuestPolicy.isPlaylist(SourceType.STALKER))
    }

    @Test
    fun `isStalker identifies Stalker only`() {
        assertTrue(GuestPolicy.isStalker(SourceType.STALKER))
        assertFalse(GuestPolicy.isStalker(SourceType.M3U))
        assertFalse(GuestPolicy.isStalker(SourceType.XTREAM))
        assertFalse(GuestPolicy.isStalker(SourceType.LOCAL_BACKUP))
    }

    @Test
    fun `guest with empty sources can add playlist and stalker`() {
        val sources = emptyList<SourceEntity>()
        assertTrue(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.M3U))
        assertTrue(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.XTREAM))
        assertTrue(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.STALKER))
        assertTrue(GuestPolicy.canAddAnySource(isGuest = true, currentSources = sources))
    }

    @Test
    fun `guest with 1 M3U cannot add another playlist but can add stalker`() {
        val sources = listOf(fakeSource(1, SourceType.M3U))
        assertFalse(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.M3U))
        assertFalse(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.XTREAM))
        assertTrue(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.STALKER))
        assertTrue(GuestPolicy.canAddAnySource(isGuest = true, currentSources = sources))
    }

    @Test
    fun `guest with 1 Xtream cannot add another playlist but can add stalker`() {
        val sources = listOf(fakeSource(1, SourceType.XTREAM))
        assertFalse(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.M3U))
        assertFalse(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.XTREAM))
        assertTrue(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.STALKER))
        assertTrue(GuestPolicy.canAddAnySource(isGuest = true, currentSources = sources))
    }

    @Test
    fun `guest with 1 Stalker cannot add another Stalker but can add playlist`() {
        val sources = listOf(fakeSource(1, SourceType.STALKER))
        assertTrue(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.M3U))
        assertTrue(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.XTREAM))
        assertFalse(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.STALKER))
        assertTrue(GuestPolicy.canAddAnySource(isGuest = true, currentSources = sources))
    }

    @Test
    fun `guest with 1 playlist and 1 Stalker cannot add any source`() {
        val sources = listOf(fakeSource(1, SourceType.M3U), fakeSource(2, SourceType.STALKER))
        assertFalse(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.M3U))
        assertFalse(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.XTREAM))
        assertFalse(GuestPolicy.canAddSource(isGuest = true, currentSources = sources, type = SourceType.STALKER))
        assertFalse(GuestPolicy.canAddAnySource(isGuest = true, currentSources = sources))
    }

    @Test
    fun `regular non-guest profile is unlimited`() {
        val sources = listOf(
            fakeSource(1, SourceType.M3U),
            fakeSource(2, SourceType.XTREAM),
            fakeSource(3, SourceType.STALKER),
            fakeSource(4, SourceType.STALKER),
        )
        assertTrue(GuestPolicy.canAddSource(isGuest = false, currentSources = sources, type = SourceType.M3U))
        assertTrue(GuestPolicy.canAddSource(isGuest = false, currentSources = sources, type = SourceType.XTREAM))
        assertTrue(GuestPolicy.canAddSource(isGuest = false, currentSources = sources, type = SourceType.STALKER))
        assertTrue(GuestPolicy.canAddAnySource(isGuest = false, currentSources = sources))
        assertFalse(GuestPolicy.isLimitReached(isGuest = false, currentSources = sources, type = SourceType.M3U))
    }

    @Test
    fun `shouldShowHook returns false for regular non-guest profile`() {
        assertFalse(GuestPolicy.shouldShowHook(isGuest = false, dismissedAt = 0L))
        assertFalse(GuestPolicy.shouldShowHook(isGuest = false, dismissedAt = 1000L))
    }

    @Test
    fun `shouldShowHook returns true when never dismissed`() {
        assertTrue(GuestPolicy.shouldShowHook(isGuest = true, dismissedAt = 0L))
    }

    @Test
    fun `shouldShowHook respects 7-day dismissal window`() {
        val now = 1_000_000_000_000L
        val sixDaysAgo = now - 6L * 24 * 60 * 60 * 1000L
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000L
        val eightDaysAgo = now - 8L * 24 * 60 * 60 * 1000L

        assertFalse(GuestPolicy.shouldShowHook(isGuest = true, dismissedAt = sixDaysAgo, now = now))
        assertTrue(GuestPolicy.shouldShowHook(isGuest = true, dismissedAt = sevenDaysAgo, now = now))
        assertTrue(GuestPolicy.shouldShowHook(isGuest = true, dismissedAt = eightDaysAgo, now = now))
    }
}
