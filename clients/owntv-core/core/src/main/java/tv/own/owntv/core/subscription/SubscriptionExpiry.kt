package tv.own.owntv.core.subscription

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.SourceTester
import tv.own.owntv.core.repository.SourceTestResult

/** A source whose paid period ends soon. [daysLeft] is 0 on the last day. */
data class ExpiryNotice(val sourceId: Long, val sourceName: String, val daysLeft: Int)

/** Days from [nowMs] to [expiryMs], rounded up, never negative. */
fun daysUntil(expiryMs: Long, nowMs: Long): Int =
    ((expiryMs - nowMs).coerceAtLeast(0L) + DAY_MS - 1).div(DAY_MS).toInt()

/**
 * Reads a date a portal wrote in its own words. Returns null when it cannot be read with
 * confidence — a wrong date would show a wrong "days left", so unknown stays unknown.
 */
fun parseExpiryText(text: String): Long? {
    val t = text.trim()
    t.toLongOrNull()?.let { n ->
        if (n in 1_000_000_000L..99_999_999_999L) return n * 1000
        if (n in 100_000_000_000L..99_999_999_999_999L) return n
    }
    for (pattern in PATTERNS) {
        val f = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false; timeZone = TimeZone.getDefault() }
        val pos = java.text.ParsePosition(0)
        val d = f.parse(t, pos)
        if (d != null && pos.index > 0) {
            // A bare date means "through that day".
            return if (pattern.contains("HH")) d.time else d.time + DAY_MS - 1
        }
    }
    return null
}

private val PATTERNS = listOf(
    "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd",
    "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm", "dd.MM.yyyy", "dd/MM/yyyy", "dd-MM-yyyy",
    "MMMM d, yyyy", "MMM d, yyyy", "d MMMM yyyy", "d MMM yyyy", "EEEE, MMMM d, yyyy, h:mm a",
)

private const val DAY_MS = 86_400_000L

/**
 * Looks at every saved Xtream / Stalker source once and says which ones run out within
 * [WARN_DAYS]. Works for any provider that reports an end date; one that reports none is simply
 * never mentioned. Each source is announced at most once per calendar day.
 */
class SubscriptionExpiryChecker(
    context: Context,
    private val sourceDao: SourceDao,
    private val tester: SourceTester,
) {
    private val prefs = context.applicationContext.getSharedPreferences("subscription_expiry", Context.MODE_PRIVATE)

    suspend fun check(nowMs: Long = System.currentTimeMillis()): List<ExpiryNotice> = withContext(Dispatchers.IO) {
        val today = nowMs / DAY_MS
        val out = mutableListOf<ExpiryNotice>()
        for (s in sourceDao.getAllOnce()) {
            if (s.type != SourceType.XTREAM && s.type != SourceType.STALKER) continue
            if (prefs.getLong("shown_${s.id}", -1L) == today) continue
            val ok = runCatching { tester.test(s) }.getOrNull() as? SourceTestResult.Ok ?: continue
            val end = ok.expiryText?.let(::parseExpiryText) ?: ok.expiryMs ?: continue
            val left = daysUntil(end, nowMs)
            if (end > nowMs && left <= WARN_DAYS) out += ExpiryNotice(s.id, s.name, left)
        }
        out
    }

    fun markShown(notices: List<ExpiryNotice>, nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().apply { notices.forEach { putLong("shown_${it.sourceId}", nowMs / DAY_MS) } }.apply()
    }

    /** True exactly once per process: the launch check must not repeat each time the screen changes. */
    fun claimLaunchRun(): Boolean = synchronized(Companion) { !ranThisLaunch.also { ranThisLaunch = true } }

    companion object {
        const val WARN_DAYS = 7
        private var ranThisLaunch = false
    }
}
