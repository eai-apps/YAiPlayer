// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.screensaver

enum class ScreensaverMode {
    POSTERS,
    NATURE_SPACE,
    CLOCK,
    MIX;

    companion object {
        fun fromString(value: String): ScreensaverMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MIX
        }
    }
}

data class ScreensaverItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String,
    val license: String = "",
    val source: String = "",
)

data class ScreensaverConfig(
    val enabled: Boolean = true,
    val timeoutMinutes: Int = 5,
    val mode: ScreensaverMode = ScreensaverMode.MIX,
    val showCornerClock: Boolean = true,
) {
    val timeoutMs: Long get() = timeoutMinutes * 60 * 1000L
}
