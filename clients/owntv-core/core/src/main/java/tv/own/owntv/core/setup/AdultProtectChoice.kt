package tv.own.owntv.core.setup

/**
 * What the add-playlist form says about "Protect adult channels with a PIN". The form writes it as the
 * user ticks the box and types a PIN; the import reads and clears it with [take] when the playlist is
 * saved. One value is enough because only one add form is ever open.
 */
object AdultProtectChoice {
    @Volatile var enabled: Boolean = false
    @Volatile var pin: String = ""

    fun reset() {
        enabled = false
        pin = ""
    }

    /** The current choice, cleared so it never carries over to the next playlist. */
    fun take(): Pair<Boolean, String> {
        val choice = enabled to pin
        reset()
        return choice
    }
}
