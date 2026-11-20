// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.live

import android.content.Context
import java.util.Date
import java.util.Locale

/**
 * Owner 20.11: «непонятно, насколько программа скачана». One line under the programme in the channel pane:
 * «Телепрограмма ещё на 5 дней (до пт, 27.11)», or «…заканчивается сегодня в 23:00». Null when the channel's
 * guide is not stored (short guide from the provider only) or already over.
 */
object GuideAheadText {
    private const val DAY_MS = 86_400_000L

    fun of(context: Context, untilMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
        if (untilMs == null || untilMs <= nowMs) return null
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val days = ((untilMs - nowMs) / DAY_MS).toInt()
        return if (days < 1) {
            val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(untilMs))
            context.getString(tv.own.owntv.core.R.string.guide_ahead_today, time)
        } else {
            context.resources.getQuantityString(tv.own.owntv.core.R.plurals.guide_ahead_days, days, days, date(locale, untilMs))
        }
    }

    /** «пт, 27.11» in the device's language — also the date the daily offer names. */
    fun date(locale: Locale, ms: Long): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEddMM")
        return java.text.SimpleDateFormat(pattern, locale).format(Date(ms))
    }
}
