package tv.own.owntv.core.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Task 13: Tests verifying channel matching against EPG guide entries using real-world
 * provider channel names (e.g. Stalker portals where tvg-id is absent and matching is purely name-based).
 *
 * Captures ACTUAL behavior of [EpgMatcher] without modifying application code.
 * Gaps where intuitive matching fails are recorded and marked with @Ignore("known gap: ...").
 */
class EpgMatchRealNamesTest {

    data class TestCase(
        val playlistName: String,
        val guideName: String,
        val expectedScoreMin: Double,
        val shouldAutoMatch: Boolean,
        val shouldReviewMatch: Boolean,
        val description: String = "",
    )

    private fun matchScore(playlistName: String, guideName: String): Double {
        val normA = EpgMatcher.normalizeForEpg(playlistName)
        val normB = EpgMatcher.normalizeForEpg(guideName)
        return EpgMatcher.scoreNormalized(normA, normB)
    }

    private fun bestMatch(playlistName: String, guideCandidates: List<String>): EpgMatcher.Result? {
        val candidates = guideCandidates.mapIndexed { idx, name ->
            EpgMatcher.Candidate("epg-$idx", name)
        }
        return EpgMatcher.bestEpgMatch(playlistName, candidates)
    }

    // -------------------------------------------------------------------------
    // 1. Federal Russian channels & quality variations
    // -------------------------------------------------------------------------

    @Test
    fun `pervy kanal HD matches Pervy kanal automatically`() {
        // "Первый канал HD" vs "Первый канал"
        val score = matchScore("Первый канал HD", "Первый канал")
        assertEquals(1.0, score, 0.001)
        assertTrue("Must clear AUTO_THRESHOLD", score >= EpgMatcher.AUTO_THRESHOLD)

        val result = bestMatch("Первый канал HD", listOf("Первый канал", "Россия 1", "НТВ"))
        assertEquals("epg-0", result?.epgChannelId)
    }

    @Test
    fun `actual behavior - Pervy kanal vs Pervy reaches review but falls below auto-threshold`() {
        // ACTUAL: "первый канал" vs "первый" scores 0.900 due to Jaro-Winkler length difference
        val score = matchScore("Первый канал", "Первый")
        assertEquals(0.900, score, 0.005)
        assertTrue("Reaches review threshold", score >= EpgMatcher.REVIEW_THRESHOLD)
        assertFalse("Does NOT reach auto threshold (0.900 < 0.92)", score >= EpgMatcher.AUTO_THRESHOLD)
    }

    @Ignore("known gap: omitting 'канал' leaves only 'первый'; score 0.900 falls just short of AUTO_THRESHOLD (0.92)")
    @Test
    fun `desired behavior - Pervy kanal should auto-match Pervy`() {
        val score = matchScore("Первый канал", "Первый")
        assertTrue("Expected auto-match for 'Первый канал' and 'Первый'", score >= EpgMatcher.AUTO_THRESHOLD)
    }

    @Test
    fun `rossiya 1 variations with FHD and hyphen all match automatically`() {
        // "Россия 1 FHD" / "Россия 1" / "Россия-1"
        val pairs = listOf(
            "Россия 1 FHD" to "Россия 1",
            "Россия 1" to "Россия-1",
            "Россия 1 FHD" to "Россия-1",
        )
        for ((p, g) in pairs) {
            val score = matchScore(p, g)
            assertEquals("Pair '$p' <-> '$g' must normalize identically", 1.0, score, 0.001)
            assertTrue("Must clear AUTO_THRESHOLD", score >= EpgMatcher.AUTO_THRESHOLD)
        }
    }

    // -------------------------------------------------------------------------
    // 2. Time-shift (+2, +4) behavior
    // -------------------------------------------------------------------------

    @Test
    fun `time-shift (+2) is kept as its own token`() {
        assertEquals("нтв plus2", EpgMatcher.normalizeForEpg("НТВ HD (+2)"))
        assertEquals("нтв", EpgMatcher.normalizeForEpg("НТВ"))
        assertEquals("нтв plus2", EpgMatcher.normalizeForEpg("НТВ +2"))
        assertEquals("нтв minus1", EpgMatcher.normalizeForEpg("НТВ [-1]"))
        // "1+1" and "ТВ-3" are names, not shifts
        assertEquals("1 1", EpgMatcher.normalizeForEpg("[UA] 1+1"))
        assertEquals("тв 3", EpgMatcher.normalizeForEpg("ТВ-3"))
    }

    @Test
    fun `shifted channel pairs with the shifted guide`() {
        assertEquals(1.0, matchScore("НТВ HD (+2)", "НТВ (+2)"), 0.001)
    }

    @Test
    fun `desired behavior - time-shift (+2) must NOT match base unshifted channel`() {
        // Time-shifted stream has completely different broadcast schedule and must not pair with base channel
        val score = matchScore("НТВ HD (+2)", "НТВ")
        assertTrue("Time-shifted channel (+2) should not auto-match base channel without shift",
            score < EpgMatcher.AUTO_THRESHOLD)
    }

    // -------------------------------------------------------------------------
    // 3. Punctuation, symbols, and channel numbers: Match! TV, Pyatnitsa!, TNT4, Discovery, TV-3
    // -------------------------------------------------------------------------

    @Test
    fun `match tv variations with exclamation mark and HD match automatically`() {
        // "Матч! ТВ" vs "Матч ТВ HD"
        val score = matchScore("Матч! ТВ", "Матч ТВ HD")
        assertEquals(1.0, score, 0.001)
        assertTrue(score >= EpgMatcher.AUTO_THRESHOLD)
    }

    @Test
    fun `pyatnitsa with exclamation mark matches automatically`() {
        // "Пятница!" vs "Пятница"
        val score = matchScore("Пятница!", "Пятница")
        assertEquals(1.0, score, 0.001)
        assertTrue(score >= EpgMatcher.AUTO_THRESHOLD)
    }

    @Test
    fun `tnt4 with and without space matches automatically`() {
        // "ТНТ4" vs "ТНТ 4"
        val score = matchScore("ТНТ4", "ТНТ 4")
        assertTrue("Score for 'ТНТ4' vs 'ТНТ 4' should auto-match", score >= EpgMatcher.AUTO_THRESHOLD)
    }

    @Test
    fun `tnt4 does NOT auto-match base TNT channel`() {
        // "ТНТ4" vs "ТНТ" -> digit 4 present only on one side, capped at DIGIT_MISSING_CAP (0.90)
        val score = matchScore("ТНТ4", "ТНТ")
        assertTrue("Scores for review", score >= EpgMatcher.REVIEW_THRESHOLD)
        assertTrue("Must stay below AUTO_THRESHOLD due to missing digit cap", score < EpgMatcher.AUTO_THRESHOLD)
    }

    @Test
    fun `discovery channel variations with HD and region tag in brackets match automatically`() {
        // "Discovery Channel HD" vs "Discovery Channel" vs "Discovery Channel (RU)"
        val pairs = listOf(
            "Discovery Channel HD" to "Discovery Channel",
            "Discovery Channel HD" to "Discovery Channel (RU)",
            "Discovery Channel" to "Discovery Channel (RU)",
        )
        for ((p, g) in pairs) {
            val score = matchScore(p, g)
            assertEquals("Pair '$p' <-> '$g' must normalize identically", 1.0, score, 0.001)
            assertTrue(score >= EpgMatcher.AUTO_THRESHOLD)
        }
    }

    @Test
    fun `tv-3 with hyphen, space, or glued digit matches automatically`() {
        // "ТВ-3" vs "ТВ 3" vs "ТВ3"
        val scoreSpace = matchScore("ТВ-3", "ТВ 3")
        assertEquals(1.0, scoreSpace, 0.001)
        assertTrue(scoreSpace >= EpgMatcher.AUTO_THRESHOLD)

        val scoreGlued = matchScore("ТВ-3", "ТВ3")
        assertTrue("Glued digit 'ТВ3' must also auto-match 'ТВ-3'", scoreGlued >= EpgMatcher.AUTO_THRESHOLD)
    }

    // -------------------------------------------------------------------------
    // 4. Country prefixes, brackets, and compatibility superscript
    // -------------------------------------------------------------------------

    @Test
    fun `country prefixes in various formats are cleanly stripped`() {
        // "RU: СТС Love" -> "СТС Love"
        val scoreRu = matchScore("RU: СТС Love", "СТС Love")
        assertEquals(1.0, scoreRu, 0.001)
        assertTrue(scoreRu >= EpgMatcher.AUTO_THRESHOLD)

        // "[UA] 1+1" -> "1+1"
        val scoreUa = matchScore("[UA] 1+1", "1+1")
        assertEquals(1.0, scoreUa, 0.001)
        assertTrue(scoreUa >= EpgMatcher.AUTO_THRESHOLD)

        // "| DE | RTL HD" -> "RTL"
        val scoreDe = matchScore("| DE | RTL HD", "RTL")
        assertEquals(1.0, scoreDe, 0.001)
        assertTrue(scoreDe >= EpgMatcher.AUTO_THRESHOLD)
    }

    @Test
    fun `superscript HD symbol folds and is stripped as noise`() {
        // "Звезда ᴴᴰ" vs "Звезда"
        val score = matchScore("Звезда ᴴᴰ", "Звезда")
        assertEquals(1.0, score, 0.001)
        assertTrue(score >= EpgMatcher.AUTO_THRESHOLD)
    }

    // -------------------------------------------------------------------------
    // 5. Letter cases, whitespace, and Cyrillic ё vs е
    // -------------------------------------------------------------------------

    @Test
    fun `case insensitivity and whitespace collapsing work reliably`() {
        // "СТС" vs "стс"
        val scoreCase = matchScore("СТС", "стс")
        assertEquals(1.0, scoreCase, 0.001)

        // "  Первый   канал  " vs "Первый канал"
        val scoreSpaces = matchScore("  Первый   канал  ", "Первый канал")
        assertEquals(1.0, scoreSpaces, 0.001)
    }

    @Test
    fun `Cyrillic yo folds to e`() {
        assertEquals("еж тв", EpgMatcher.normalizeForEpg("Ёж ТВ"))
        assertEquals("еж тв", EpgMatcher.normalizeForEpg("Еж ТВ"))
    }

    @Test
    fun `desired behavior - Cyrillic yo should auto-match Cyrillic e`() {
        val score = matchScore("Ёж ТВ", "Еж ТВ")
        assertTrue("Expected 'Ёж ТВ' to auto-match 'Еж ТВ'", score >= EpgMatcher.AUTO_THRESHOLD)
    }

    // -------------------------------------------------------------------------
    // 6. Comprehensive Parameterized Matrix Test
    // -------------------------------------------------------------------------

    @Test
    fun `testRealNamesMatrix_recordsActualOutcomes`() {
        val matrix = listOf(
            TestCase("Первый канал HD", "Первый канал", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "HD suffix stripped"),
            TestCase("Россия 1 FHD", "Россия 1", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "FHD suffix stripped"),
            TestCase("Россия 1", "Россия-1", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Hyphen separator flattened"),
            TestCase("Россия 1 FHD", "Россия-1", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "FHD stripped & hyphen flattened"),
            TestCase("Матч! ТВ", "Матч ТВ HD", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Exclamation mark stripped, HD stripped"),
            TestCase("Матч! ТВ", "Матч ТВ", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Exclamation mark stripped"),
            TestCase("Пятница!", "Пятница", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Exclamation mark stripped"),
            TestCase("ТНТ4", "ТНТ 4", 0.95, shouldAutoMatch = true, shouldReviewMatch = true, "Digit glued vs separated"),
            TestCase("Discovery Channel HD", "Discovery Channel", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "English channel HD stripped"),
            TestCase("Discovery Channel HD", "Discovery Channel (RU)", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "(RU) in brackets stripped"),
            TestCase("Discovery Channel", "Discovery Channel (RU)", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Region in brackets stripped"),
            TestCase("ТВ-3", "ТВ 3", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Hyphen to space"),
            TestCase("ТВ-3", "ТВ3", 0.93, shouldAutoMatch = true, shouldReviewMatch = true, "Hyphen vs glued digit"),
            TestCase("RU: СТС Love", "СТС Love", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Prefix RU: stripped"),
            TestCase("[UA] 1+1", "1+1", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Prefix [UA] stripped, + flattened"),
            TestCase("| DE | RTL HD", "RTL", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Pipes and DE prefix stripped"),
            TestCase("Звезда ᴴᴰ", "Звезда", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Superscript HD folded via NFKC"),
            TestCase("СТС", "стс", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Cyrillic lowercase"),
            TestCase("  Первый   канал  ", "Первый канал", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Extra whitespaces collapsed"),
            TestCase("Кинопремьера", "Кинопремьера HD", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Cyrillic single word with HD"),

            // Cases that reach review but not auto-match (expected & actual behavior recorded)
            TestCase("Первый канал HD", "Первый", 0.90, shouldAutoMatch = false, shouldReviewMatch = true, "Word 'канал' missing"),
            TestCase("Первый канал", "Первый", 0.90, shouldAutoMatch = false, shouldReviewMatch = true, "Word 'канал' missing"),
            TestCase("ТНТ4", "ТНТ", 0.90, shouldAutoMatch = false, shouldReviewMatch = true, "Channel number 4 missing on one side"),
            TestCase("Ёж ТВ", "Еж ТВ", 1.00, shouldAutoMatch = true, shouldReviewMatch = true, "Ё folded to Е"),

            // Distractor / completely different channel numbers
            TestCase("Eurosport 1", "Eurosport 2", 0.60, shouldAutoMatch = false, shouldReviewMatch = false, "Different channel numbers"),
        )

        for (caseItem in matrix) {
            val score = matchScore(caseItem.playlistName, caseItem.guideName)
            val autoMatched = score >= EpgMatcher.AUTO_THRESHOLD
            val reviewMatched = score >= EpgMatcher.REVIEW_THRESHOLD

            assertEquals(
                "Auto-match outcome for '${caseItem.playlistName}' <-> '${caseItem.guideName}' (${caseItem.description})",
                caseItem.shouldAutoMatch,
                autoMatched
            )
            assertEquals(
                "Review-match outcome for '${caseItem.playlistName}' <-> '${caseItem.guideName}' (${caseItem.description})",
                caseItem.shouldReviewMatch,
                reviewMatched
            )
            assertTrue(
                "Score for '${caseItem.playlistName}' <-> '${caseItem.guideName}' was $score, expected at least ${caseItem.expectedScoreMin}",
                score >= caseItem.expectedScoreMin - 0.01
            )
        }
    }
}
