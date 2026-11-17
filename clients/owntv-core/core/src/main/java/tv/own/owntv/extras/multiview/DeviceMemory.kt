// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.multiview

import android.app.ActivityManager
import android.content.Context

object DeviceMemory {

    /**
     * Low-RAM: the system says so, or the device has less than this much memory in total.
     * Owner 17.11: a Google TV stick (2 GB, smooth) was offered Light Mode — the old rule looked at the
     * per-app heap (memoryClass ≤ 192 MB), which is 192 MB on most TV boxes whatever their real memory.
     */
    const val LOW_TOTAL_RAM_MB = 1_536L

    /**
     * Determines whether the current device is considered low-RAM (e.g. cheap TV sticks, budget boxes).
     */
    fun isLowRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo().also { runCatching { am.getMemoryInfo(it) } }
        val totalMb = info.totalMem / (1024L * 1024L)
        return totalMb in 1 until LOW_TOTAL_RAM_MB
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
