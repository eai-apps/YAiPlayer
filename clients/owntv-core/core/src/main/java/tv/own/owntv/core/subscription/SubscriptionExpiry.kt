package tv.own.owntv.core.subscription

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.SourceTester
import tv.own.owntv.core.repository.SourceTestResult

/** A source whose paid period ends soon. [daysLeft] is 0 on the last day. */
data class ExpiryNotice(val sourceId: Long, val sourceName: String, val daysLeft: Int)

/** State of the subscription expiry badge on Home. */
sealed interface SubscriptionBadgeState {
    /**
     * Active subscription with an expiration date.
     * [days] is days left, 0 if expired.
     * [isWarning] is true if <= 7 days left or expired.
     */
    data class Days(val days: Int, val isWarning: Boolean) : SubscriptionBadgeState

    /** Unlimited subscription or no end date specified. Displays infinity sign "∞". */
    data object Unlimited : SubscriptionBadgeState

    /** Expiry is unknown or unsupported (e.g. plain M3U, not checked yet). Badge is hidden. */
    data object Unknown : SubscriptionBadgeState
}

/** Days from [nowMs] to [expiryMs], rounded up, never negative. */
fun daysUntil(expiryMs: Long, nowMs: Long): Int =
    ((expiryMs - nowMs).coerceAtLeast(0L) + DAY_MS - 1).div(DAY_MS).toInt()

private val UNLIMITED_KEYWORDS = setOf("unlimited", "бессрочно", "безлимит", "0", "null")

/**
 * Calculates the subscription badge state according to the rules:
 * 1. Shows "N дн." for active source. If <= 7 days, accent color. If expired, shows "0 дн." (accent color).
 * 2. If subscription is unlimited or no end date is set, shows infinity "∞".
 * 3. If expiry is unknown, shows nothing (Unknown).
 */
fun calculateSubscriptionBadge(
    sourceType: SourceType?,
    expiryText: String? = null,
    expiryMs: Long? = null,
    isUnlimited: Boolean = false,
    isAccountChecked: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
): SubscriptionBadgeState {
    if (sourceType != SourceType.XTREAM && sourceType != SourceType.STALKER) {
        return SubscriptionBadgeState.Unknown
    }
    if (!isAccountChecked) {
        return SubscriptionBadgeState.Unknown
    }
    if (isUnlimited) {
        return SubscriptionBadgeState.Unlimited
    }
    val parsedMs = expiryText?.takeIf { it.isNotBlank() }?.let(::parseExpiryText)
    val endMs = parsedMs ?: expiryMs?.takeIf { it > 0 }
    if (endMs != null) {
        return if (endMs <= nowMs) {
            SubscriptionBadgeState.Days(days = 0, isWarning = true)
        } else {
            val days = daysUntil(endMs, nowMs)
            SubscriptionBadgeState.Days(days = days, isWarning = days <= SubscriptionExpiryChecker.WARN_DAYS)
        }
    }
    if (expiryText != null && expiryText.isNotBlank()) {
        val t = expiryText.trim().lowercase(Locale.US)
        if (t in UNLIMITED_KEYWORDS) {
            return SubscriptionBadgeState.Unlimited
        }
        return SubscriptionBadgeState.Unknown
    }
    // For Xtream or Stalker where account is checked but no expiration date/time is returned
    return SubscriptionBadgeState.Unlimited
}

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

private const val PREF_CHECKED = "checked_"
private const val PREF_UNLIMITED = "is_unlimited_"
private const val PREF_EXPIRY_MS = "expiry_ms_"
private const val PREF_EXPIRY_TEXT = "expiry_text_"

private fun prefKey(prefix: String, id: Long): String = prefix + id

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
    private val _updates = MutableStateFlow(System.currentTimeMillis())
    val updates: StateFlow<Long> = _updates.asStateFlow()

    private val checkingSources = Collections.synchronizedSet(mutableSetOf<Long>())

    fun saveSourceExpiry(sourceId: Long, expiryMs: Long?, expiryText: String?, isUnlimited: Boolean) {
        prefs.edit().apply {
            putBoolean(prefKey(PREF_CHECKED, sourceId), true)
            putBoolean(prefKey(PREF_UNLIMITED, sourceId), isUnlimited)
            if (expiryMs != null) putLong(prefKey(PREF_EXPIRY_MS, sourceId), expiryMs) else remove(prefKey(PREF_EXPIRY_MS, sourceId))
            if (expiryText != null) putString(prefKey(PREF_EXPIRY_TEXT, sourceId), expiryText) else remove(prefKey(PREF_EXPIRY_TEXT, sourceId))
        }.apply()
        _updates.value = System.currentTimeMillis()
    }

    fun getBadgeState(source: SourceEntity?, nowMs: Long = System.currentTimeMillis()): SubscriptionBadgeState {
        if (source == null) return SubscriptionBadgeState.Unknown
        if (source.type != SourceType.XTREAM && source.type != SourceType.STALKER) {
            return SubscriptionBadgeState.Unknown
        }
        val isChecked = prefs.getBoolean(prefKey(PREF_CHECKED, source.id), false)
        if (!isChecked) {
            checkSourceAsync(source)
            return SubscriptionBadgeState.Unknown
        }
        val isUnlimited = prefs.getBoolean(prefKey(PREF_UNLIMITED, source.id), false)
        val msKey = prefKey(PREF_EXPIRY_MS, source.id)
        val expiryMs = if (prefs.contains(msKey)) prefs.getLong(msKey, 0L) else null
        val expiryText = prefs.getString(prefKey(PREF_EXPIRY_TEXT, source.id), null)

        return calculateSubscriptionBadge(
            sourceType = source.type,
            expiryText = expiryText,
            expiryMs = expiryMs,
            isUnlimited = isUnlimited,
            isAccountChecked = true,
            nowMs = nowMs,
        )
    }

    fun checkSourceAsync(source: SourceEntity) {
        if (source.type != SourceType.XTREAM && source.type != SourceType.STALKER) return
        if (!checkingSources.add(source.id)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val testResult = runCatching { tester.test(source) }.getOrNull()
                when (testResult) {
                    is SourceTestResult.Ok -> {
                        val end = testResult.expiryText?.let(::parseExpiryText) ?: testResult.expiryMs
                        if (end != null) {
                            saveSourceExpiry(source.id, expiryMs = end, expiryText = testResult.expiryText, isUnlimited = false)
                        } else {
                            saveSourceExpiry(source.id, expiryMs = null, expiryText = testResult.expiryText, isUnlimited = true)
                        }
                    }
                    is SourceTestResult.Expired -> {
                        saveSourceExpiry(source.id, expiryMs = testResult.expiryMs ?: 0L, expiryText = null, isUnlimited = false)
                    }
                    else -> {}
                }
            } finally {
                checkingSources.remove(source.id)
            }
        }
    }

    suspend fun check(nowMs: Long = System.currentTimeMillis()): List<ExpiryNotice> = withContext(Dispatchers.IO) {
        val today = nowMs / DAY_MS
        val out = mutableListOf<ExpiryNotice>()
        for (s in sourceDao.getAllOnce()) {
            if (s.type != SourceType.XTREAM && s.type != SourceType.STALKER) continue
            val testResult = runCatching { tester.test(s) }.getOrNull()
            if (testResult is SourceTestResult.Ok) {
                val end = testResult.expiryText?.let(::parseExpiryText) ?: testResult.expiryMs
                if (end != null) {
                    saveSourceExpiry(s.id, expiryMs = end, expiryText = testResult.expiryText, isUnlimited = false)
                } else {
                    saveSourceExpiry(s.id, expiryMs = null, expiryText = testResult.expiryText, isUnlimited = true)
                }
            } else if (testResult is SourceTestResult.Expired) {
                saveSourceExpiry(s.id, expiryMs = testResult.expiryMs ?: 0L, expiryText = null, isUnlimited = false)
            }
            if (prefs.getLong("shown_${s.id}", -1L) == today) continue
            val ok = testResult as? SourceTestResult.Ok ?: continue
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
