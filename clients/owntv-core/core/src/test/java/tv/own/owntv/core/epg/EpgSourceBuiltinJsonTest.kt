package tv.own.owntv.core.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgSourceBuiltinJsonTest {
    @Test
    fun builtinFlagRoundTripsAndDefaultsToFalse() {
        val list = listOf(
            EpgSource(-1, "mine", "https://a/x.xml"),
            EpgSource(-2, "Built-in guide", "https://iptvx.one/EPG_LITE", builtin = true),
        )
        val back = EpgSourceJson.parse(EpgSourceJson.write(list))
        assertEquals(list, back)
        assertFalse(back[0].builtin)
        assertTrue(back[1].builtin)
    }

    @Test
    fun oldStoredListWithoutFlagParsesAsNotBuiltin() {
        val back = EpgSourceJson.parse("""[{"id":-1,"name":"n","url":"u"}]""")
        assertFalse(back.single().builtin)
    }
}
