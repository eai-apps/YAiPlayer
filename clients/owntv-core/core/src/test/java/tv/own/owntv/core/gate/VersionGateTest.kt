package tv.own.owntv.core.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionGateTest {
    private val day = 86_400_000L
    private fun cfg(enforce: Boolean = true, min: String = "2.0.50", blockAt: Long? = null) = GateConfig(enforce, min, blockAt, "u")

    @Test fun disabledByDefault() {
        assertEquals(GateState.Ok, VersionGateLogic.decide(cfg(enforce = false), "2.0.1", 0))
        assertEquals(GateState.Ok, VersionGateLogic.decide(null, "2.0.1", 0))
    }

    @Test fun newEnoughVersionIsFine() {
        assertEquals(GateState.Ok, VersionGateLogic.decide(cfg(blockAt = 1), "2.0.50", 10 * day))
        assertEquals(GateState.Ok, VersionGateLogic.decide(cfg(blockAt = 1), "2.1.0", 10 * day))
    }

    @Test fun oldVersionBlocksAfterDeadlineOrWithoutOne() {
        assertTrue(VersionGateLogic.decide(cfg(blockAt = 5 * day), "2.0.44", 6 * day) is GateState.Block)
        assertTrue(VersionGateLogic.decide(cfg(blockAt = null), "2.0.44", 0) is GateState.Block)
    }

    @Test fun warnsInWeeklyStagesInsideFourWeeks() {
        val at = 100 * day
        assertEquals(GateState.Ok, VersionGateLogic.decide(cfg(blockAt = at), "2.0.44", at - 40 * day))
        val w4 = VersionGateLogic.decide(cfg(blockAt = at), "2.0.44", at - 28 * day) as GateState.Warn
        val w3 = VersionGateLogic.decide(cfg(blockAt = at), "2.0.44", at - 21 * day) as GateState.Warn
        assertEquals(28, w4.daysLeft)
        assertTrue(w4.stage != w3.stage)
        val still = VersionGateLogic.decide(cfg(blockAt = at), "2.0.44", at - 20 * day) as GateState.Warn
        assertEquals(w3.stage, still.stage)
    }

    @Test fun parsesConfig() {
        val c = VersionGateLogic.parseConfig("""{"enforce":true,"min_version":"2.0.60","block_at":"2026-12-05"}""", "fb")!!
        assertTrue(c.enforce); assertEquals("2.0.60", c.minVersion); assertEquals("fb", c.updateUrl)
        assertTrue(c.blockAtMs!! > 0)
    }
}
