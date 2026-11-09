package tv.own.owntv.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ProgrammeSearchTest {

    @Test
    fun matches_cyrillicCaseInsensitive() {
        assertTrue(ProgrammeSearchMatcher.matches("Футбол. Лига чемпионов", "футбол"))
        assertTrue(ProgrammeSearchMatcher.matches("Новости дня", "новости"))
        assertTrue(ProgrammeSearchMatcher.matches("новости спорта", "НОВОСТИ"))
        assertTrue(ProgrammeSearchMatcher.matches("Футбол. Лига Чемпионов", "лига чемпионов"))
        assertTrue(ProgrammeSearchMatcher.matches("ВЕЧЕРНИЙ УРГАНТ", "вечерний"))
    }

    @Test
    fun matches_yoAndEFolding() {
        // 'е' in query matches 'ё' in title
        assertTrue(ProgrammeSearchMatcher.matches("Звёздные войны", "звездные"))
        assertTrue(ProgrammeSearchMatcher.matches("Ёжик в тумане", "ежик"))
        assertTrue(ProgrammeSearchMatcher.matches("Всё о кино", "все"))

        // 'ё' in query matches 'е' in title
        assertTrue(ProgrammeSearchMatcher.matches("Звездные войны", "звёздные"))
        assertTrue(ProgrammeSearchMatcher.matches("Ежик в тумане", "ёжик"))
        assertTrue(ProgrammeSearchMatcher.matches("Все о кино", "всё"))
    }

    @Test
    fun matches_requiresAtLeastThreeCharacters() {
        assertFalse(ProgrammeSearchMatcher.matches("Футбол", "фу"))
        assertFalse(ProgrammeSearchMatcher.matches("Новости", "но"))
        assertTrue(ProgrammeSearchMatcher.matches("Новости", "нов"))
    }

    @Test
    fun queryVariants_generatesExpectedForms() {
        val variants = ProgrammeSearchMatcher.queryVariants("футбол")
        assertTrue("Expected 'футбол' in variants", variants.contains("футбол"))
        assertTrue("Expected 'Футбол' in variants", variants.contains("Футбол"))
        assertTrue("Expected 'ФУТБОЛ' in variants", variants.contains("ФУТБОЛ"))

        val yoVariants = ProgrammeSearchMatcher.queryVariants("звездные")
        assertTrue("Expected 'звёздные' in variants", yoVariants.contains("звёздные"))
        assertTrue("Expected 'Звёздные' in variants", yoVariants.contains("Звёздные"))
    }

    @Test
    fun collapseDuplicates_keepsBestVersionOfSameChannel() {
        val start = 1_700_000_000_000L
        val stop = start + 3_600_000L
        val prog = EpgProgrammeEntity(
            id = 1,
            sourceId = 1,
            epgChannelId = "match.tv",
            startMs = start,
            stopMs = stop,
            title = "Лига Чемпионов",
        )

        val chSd = ChannelEntity(id = 10, sourceId = 1, name = "Матч ТВ SD", streamUrl = "http://1")
        val chHd = ChannelEntity(id = 11, sourceId = 1, name = "Матч ТВ HD", streamUrl = "http://2")
        val ch4k = ChannelEntity(id = 12, sourceId = 1, name = "Матч ТВ 4K", streamUrl = "http://3")
        val chOther = ChannelEntity(id = 20, sourceId = 1, name = "Первый канал HD", streamUrl = "http://4")

        val results = listOf(
            ProgrammeSearchResult(prog.copy(id = 1), chSd),
            ProgrammeSearchResult(prog.copy(id = 2), chHd),
            ProgrammeSearchResult(prog.copy(id = 3), ch4k),
            ProgrammeSearchResult(prog.copy(id = 4, epgChannelId = "1tv"), chOther),
        )

        val collapsed = ProgrammeSearchMatcher.collapseDuplicates(results, listOf(1L))
        assertEquals(2, collapsed.size)

        // Winner for "Матч ТВ" should be 4K (qualityRank = 4)
        val matchWinner = collapsed.first { it.channel.name.contains("Матч") }
        assertEquals("Матч ТВ 4K", matchWinner.channel.name)

        // Other channel ("Первый канал HD") must remain
        val other = collapsed.first { it.channel.name.contains("Первый") }
        assertEquals("Первый канал HD", other.channel.name)
    }

    @Test
    fun programmeBadge_identifiesNowTodayTomorrowWeekday() {
        val tz = TimeZone.getTimeZone("UTC")
        val loc = Locale.ENGLISH
        val cal = Calendar.getInstance(tz, loc).apply {
            set(2026, Calendar.NOVEMBER, 9, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis

        // Currently on air: started 30 min ago, ends in 30 min
        val badgeNow = ProgrammeSearchMatcher.programmeBadge(now - 1_800_000L, now + 1_800_000L, now, loc, tz)
        assertEquals(ProgrammeSearchMatcher.ProgrammeTimeBadgeKind.NOW, badgeNow.kind)

        // Today: starts in 2 hours
        val badgeToday = ProgrammeSearchMatcher.programmeBadge(now + 7_200_000L, now + 10_800_000L, now, loc, tz)
        assertEquals(ProgrammeSearchMatcher.ProgrammeTimeBadgeKind.TODAY, badgeToday.kind)

        // Tomorrow: starts 26 hours from now
        val badgeTomorrow = ProgrammeSearchMatcher.programmeBadge(now + 26 * 3_600_000L, now + 28 * 3_600_000L, now, loc, tz)
        assertEquals(ProgrammeSearchMatcher.ProgrammeTimeBadgeKind.TOMORROW, badgeTomorrow.kind)

        // In 4 days (Friday)
        val badgeFriday = ProgrammeSearchMatcher.programmeBadge(now + 4 * 24 * 3_600_000L, now + 4 * 24 * 3_600_000L + 3_600_000L, now, loc, tz)
        assertEquals(ProgrammeSearchMatcher.ProgrammeTimeBadgeKind.DAY_OF_WEEK, badgeFriday.kind)
        assertEquals("Fri", badgeFriday.dayOfWeek)
    }
}
