package tv.own.owntv.player

/**
 * "Auto 16:9": video below Full HD that arrives as a squeezed 4:3 picture (SD, anamorphic PAL/NTSC
 * channels, where the frame is stored narrow and meant to be stretched) is shown 16:9 at once.
 *
 * Applied only when the user has not decided otherwise: the default zoom is still "Fit", nothing was
 * remembered for this channel or film, and the person did not pick a zoom in the player themselves.
 * Anything already wide (720p, 1080p, …) and anything Full HD or larger is left alone.
 */
object AutoWidescreen {
    /** A picture narrower than this is the squeezed kind (4:3 = 1.33; 16:9 = 1.78). */
    private const val NARROW_BELOW = 1.70f

    fun applies(
        enabled: Boolean,
        defaultZoom: ZoomMode,
        current: ZoomMode,
        userChoseZoom: Boolean,
        width: Int,
        height: Int,
    ): Boolean {
        if (!enabled || userChoseZoom) return false
        if (defaultZoom != ZoomMode.FIT || current != ZoomMode.FIT) return false
        if (width <= 0 || height <= 0 || height >= 1080) return false
        return width.toFloat() / height.toFloat() < NARROW_BELOW
    }
}
