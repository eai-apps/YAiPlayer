// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.

package tv.own.owntv.extras.epg

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

object EpgTimeShift {
    const val MIN_SHIFT_MINUTES = -12 * 60
    const val MAX_SHIFT_MINUTES = 12 * 60
    const val STEP_MINUTES = 30

    fun clampShiftMinutes(minutes: Int): Int =
        minutes.coerceIn(MIN_SHIFT_MINUTES, MAX_SHIFT_MINUTES)

    fun stepShiftMinutes(currentMinutes: Int, stepDelta: Int): Int =
        clampShiftMinutes(currentMinutes + stepDelta * STEP_MINUTES)

    fun applyEpgShift(timeMs: Long, shiftMinutes: Int): Long =
        timeMs + shiftMinutes * 60_000L

    fun toStoredTime(displayMs: Long, shiftMinutes: Int): Long =
        displayMs - shiftMinutes * 60_000L

    fun applyToProgramme(p: EpgProgrammeEntity, shiftMinutes: Int): EpgProgrammeEntity {
        if (shiftMinutes == 0) return p
        val delta = shiftMinutes * 60_000L
        return p.copy(startMs = p.startMs + delta, stopMs = p.stopMs + delta)
    }

    fun effectiveShift(
        channelOverride: Int?,
        playlistShift: Int?,
        globalShift: Int,
    ): Int = channelOverride ?: playlistShift ?: globalShift

    fun formatShiftMinutes(minutes: Int): String {
        if (minutes == 0) return "0"
        val sign = if (minutes > 0) "+" else "-"
        val abs = kotlin.math.abs(minutes)
        val hours = abs / 60
        val rem = abs % 60
        return if (rem == 0) "$sign${hours}h" else "$sign$hours:${rem.toString().padStart(2, '0')}"
    }
}

private val Context.epgShiftStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_playlist_epg_shifts")

class PlaylistEpgShiftStore(private val context: Context) {
    private val key = stringPreferencesKey("playlist_epg_shifts")

    fun observeShift(sourceId: Long): Flow<Int> =
        context.epgShiftStore.data.map { prefs ->
            val json = prefs[key] ?: return@map 0
            parseMap(json)[sourceId] ?: 0
        }.distinctUntilChanged()

    fun observeAll(): Flow<Map<Long, Int>> =
        context.epgShiftStore.data.map { prefs ->
            val json = prefs[key] ?: return@map emptyMap()
            parseMap(json)
        }.distinctUntilChanged()

    suspend fun setShift(sourceId: Long, minutes: Int) {
        val clamped = EpgTimeShift.clampShiftMinutes(minutes)
        context.epgShiftStore.edit { prefs ->
            val map = (prefs[key]?.let { parseMap(it) } ?: emptyMap()).toMutableMap()
            if (clamped == 0) {
                map.remove(sourceId)
            } else {
                map[sourceId] = clamped
            }
            if (map.isEmpty()) {
                prefs.remove(key)
            } else {
                val obj = JSONObject()
                map.forEach { (k, v) -> obj.put(k.toString(), v) }
                prefs[key] = obj.toString()
            }
        }
    }

    private fun parseMap(raw: String): Map<Long, Int> = runCatching {
        val obj = JSONObject(raw)
        val map = mutableMapOf<Long, Int>()
        obj.keys().forEach { k ->
            k.toLongOrNull()?.let { id -> map[id] = obj.getInt(k) }
        }
        map
    }.getOrDefault(emptyMap())
}
