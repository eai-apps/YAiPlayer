package tv.own.owntv.core.content

import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.matchName
import tv.own.owntv.core.live.ChannelVersionRules
import tv.own.owntv.core.sports.ChannelFacets
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Normalization, matching, deduplication and time labeling for programme search.
 * Supports Cyrillic case insensitivity, 'ё'/'е' folding, HD/SD duplicate collapsing,
 * and time badges (Now, Today, Tomorrow, Weekday).
 */
object ProgrammeSearchMatcher {

    /**
     * Normalizes text for programme search: NFKC decomposition-composition,
     * lowercased, with 'ё' folded to 'е', and trimmed.
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase()
            .replace('ё', 'е')
            .trim()
    }

    /**
     * Checks if [title] contains [query] using Cyrillic case-insensitive and ё/е folded matching.
     * Requires at least 3 characters in the normalized query.
     */
    fun matches(title: String, query: String): Boolean {
        val normQuery = normalize(query)
        if (normQuery.length < 3) return false
        val normTitle = normalize(title)
        return normTitle.contains(normQuery)
    }

    /**
     * Generates uppercase, lowercase, titlecase and ё/е variations of [query]
     * for SQLite LIKE queries.
     */
    fun queryVariants(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val set = LinkedHashSet<String>()
        set.add(trimmed)
        set.add(trimmed.lowercase())
        set.add(trimmed.uppercase())
        set.add(trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })

        val eToYo = trimmed.replace('е', 'ё').replace('Е', 'Ё')
        if (eToYo != trimmed) {
            set.add(eToYo)
            set.add(eToYo.lowercase())
            set.add(eToYo.uppercase())
            set.add(eToYo.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
        }

        val yoToE = trimmed.replace('ё', 'е').replace('Ё', 'Е')
        if (yoToE != trimmed) {
            set.add(yoToE)
            set.add(yoToE.lowercase())
            set.add(yoToE.uppercase())
            set.add(yoToE.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
        }

        return set.filter { it.isNotBlank() }
    }

    /**
     * Collapses duplicate programme search results across different versions of the same channel
     * (e.g. HD vs SD vs 4K), keeping the best version (highest qualityRank, then playlist order).
     */
    fun collapseDuplicates(
        results: List<ProgrammeSearchResult>,
        sourceOrder: List<Long> = emptyList(),
    ): List<ProgrammeSearchResult> {
        if (results.size <= 1) return results

        val groups = ArrayList<MutableList<ProgrammeSearchResult>>()

        for (item in results) {
            val itemNormTitle = normalize(item.programme.title)
            val itemFacets = ChannelFacets.of(item.channel.matchName)
            val itemEpgKey = item.programme.epgChannelId

            var matchedGroup: MutableList<ProgrammeSearchResult>? = null
            for (group in groups) {
                val rep = group[0]
                val repNormTitle = normalize(rep.programme.title)
                if (repNormTitle == itemNormTitle && rep.programme.startMs == item.programme.startMs) {
                    val repFacets = ChannelFacets.of(rep.channel.matchName)
                    val repEpgKey = rep.programme.epgChannelId
                    val isSameChannel = (repEpgKey.isNotEmpty() && repEpgKey == itemEpgKey) ||
                        ChannelVersionRules.same(repFacets, repEpgKey, itemFacets, itemEpgKey) ||
                        (repFacets.portableKey.isNotEmpty() && repFacets.portableKey == itemFacets.portableKey)
                    if (isSameChannel) {
                        matchedGroup = group
                        break
                    }
                }
            }

            if (matchedGroup != null) {
                matchedGroup.add(item)
            } else {
                val newGroup = ArrayList<ProgrammeSearchResult>()
                newGroup.add(item)
                groups.add(newGroup)
            }
        }

        return groups.map { group ->
            if (group.size == 1) {
                group[0]
            } else {
                group.maxWithOrNull(
                    compareBy<ProgrammeSearchResult> {
                        ChannelFacets.of(it.channel.matchName).qualityRank
                    }.thenByDescending {
                        val idx = sourceOrder.indexOf(it.channel.sourceId)
                        if (idx >= 0) -idx else -Int.MAX_VALUE
                    }.thenByDescending {
                        -it.channel.sortOrder
                    }
                ) ?: group[0]
            }
        }
    }

    enum class ProgrammeTimeBadgeKind {
        NOW,
        TODAY,
        TOMORROW,
        DAY_OF_WEEK,
    }

    data class ProgrammeTimeBadge(
        val kind: ProgrammeTimeBadgeKind,
        val dayOfWeek: String? = null,
    )

    /**
     * Determines whether [startMs..stopMs] is on now, today, tomorrow, or on another day of the week.
     */
    fun programmeBadge(
        startMs: Long,
        stopMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): ProgrammeTimeBadge {
        if (startMs <= nowMs && nowMs < stopMs) {
            return ProgrammeTimeBadge(ProgrammeTimeBadgeKind.NOW)
        }

        val calNow = Calendar.getInstance(timeZone, locale).apply { timeInMillis = nowMs }
        val calProg = Calendar.getInstance(timeZone, locale).apply { timeInMillis = startMs }

        val yearNow = calNow.get(Calendar.YEAR)
        val dayNow = calNow.get(Calendar.DAY_OF_YEAR)
        val yearProg = calProg.get(Calendar.YEAR)
        val dayProg = calProg.get(Calendar.DAY_OF_YEAR)

        if (yearNow == yearProg && dayNow == dayProg) {
            return ProgrammeTimeBadge(ProgrammeTimeBadgeKind.TODAY)
        }

        calNow.add(Calendar.DAY_OF_YEAR, 1)
        if (calNow.get(Calendar.YEAR) == yearProg && calNow.get(Calendar.DAY_OF_YEAR) == dayProg) {
            return ProgrammeTimeBadge(ProgrammeTimeBadgeKind.TOMORROW)
        }

        val sdf = SimpleDateFormat("EEE", locale).apply { this.timeZone = timeZone }
        val dayStr = sdf.format(Date(startMs)).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
        return ProgrammeTimeBadge(ProgrammeTimeBadgeKind.DAY_OF_WEEK, dayStr)
    }
}
