// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.settings

/**
 * «Продолжить» on the «Кто смотрит?» screen (owner 15.11): enter the last profile and pick up the last
 * channel full screen, whatever that profile's own start-up choice is. One shot, read by the shell's
 * start-up step and cleared there.
 */
object ResumeRequest {
    @Volatile private var pending = false

    fun request() { pending = true }

    /** True once after [request]. */
    fun consume(): Boolean = pending.also { pending = false }
}
