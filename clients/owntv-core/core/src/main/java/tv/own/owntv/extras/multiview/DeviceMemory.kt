// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.multiview

import android.app.ActivityManager
import android.content.Context

object DeviceMemory {

    /** Low-RAM threshold: 192 MB memoryClass or isLowRamDevice flag true. */
    const val LOW_RAM_THRESHOLD_MB = 192

    /**
     * Determines whether the current device is considered low-RAM (e.g. cheap TV sticks, budget boxes).
     */
    fun isLowRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isLowRamDevice || am.memoryClass <= LOW_RAM_THRESHOLD_MB
    }

    /**
     * Caps multiview tile count to 2 on low-RAM devices to avoid OOM or hardware decoder exhaustion.
     */
    fun maxAllowedTiles(context: Context, preferredTiles: Int): Int {
        return if (isLowRam(context)) {
            minOf(2, preferredTiles)
        } else {
            preferredTiles.coerceIn(2, 4)
        }
    }
}
