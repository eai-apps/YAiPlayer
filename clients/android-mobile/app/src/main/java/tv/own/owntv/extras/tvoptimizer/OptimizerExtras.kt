// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
// Ideas (not code) from the MIT-licensed projects Gathin23/cleanyourtv and udhaykumarbala/cleanyourtv-app.
package tv.own.owntv.extras.tvoptimizer

/** What «Ускорь ТВ» can do on this TV box, said before anything changes. */
enum class TvVerdict {
    /** Known model on Android 8+: new Home screen and clean-up. */
    FULL,

    /** Clean-up; the new Home screen only by hand and at the user's risk (unknown model, casting device). */
    CLEAN_ONLY,

    /** Clean-up only: the new Home screen does not hold on this system (Fire OS, Android 7 and older, China builds). */
    NO_LAUNCHER,
}

enum class VerdictReason { KNOWN_MODEL, UNKNOWN_MODEL, CASTING_DEVICE, NO_LAUNCHER_BUNDLED, FIRE_OS, OLD_ANDROID, CHINA_BUILD }

data class TvAssessment(val verdict: TvVerdict, val reason: VerdictReason)

object TvAssessor {
    fun androidMajor(release: String): Int? = release.trim().takeWhile { it.isDigit() }.toIntOrNull()

    fun assess(tv: TvInfo, knownModel: Boolean, launcherBundled: Boolean): TvAssessment {
        val all = "${tv.brand} ${tv.manufacturer} ${tv.model} ${tv.fingerprint}".lowercase()
        val major = androidMajor(tv.android)
        return when {
            tv.manufacturer.equals("Amazon", true) || tv.brand.equals("Amazon", true) || "fireos" in all ->
                TvAssessment(TvVerdict.NO_LAUNCHER, VerdictReason.FIRE_OS)
            major != null && major <= 7 -> TvAssessment(TvVerdict.NO_LAUNCHER, VerdictReason.OLD_ANDROID)
            ("tcl" in all) && (Regex("[_/-]cn[_/-]|china").containsMatchIn(tv.fingerprint.lowercase())) ->
                TvAssessment(TvVerdict.NO_LAUNCHER, VerdictReason.CHINA_BUILD)
            !launcherBundled -> TvAssessment(TvVerdict.CLEAN_ONLY, VerdictReason.NO_LAUNCHER_BUNDLED)
            "chromecast" in all || "streamer" in all -> TvAssessment(TvVerdict.CLEAN_ONLY, VerdictReason.CASTING_DEVICE)
            knownModel -> TvAssessment(TvVerdict.FULL, VerdictReason.KNOWN_MODEL)
            else -> TvAssessment(TvVerdict.CLEAN_ONLY, VerdictReason.UNKNOWN_MODEL)
        }
    }
}

/** How much faster the TV's own animations become. */
enum class SpeedLevel(val scale: String) { HALF("0.5"), OFF("0") }

/**
 * Speeding up the TV box itself: the three animation scales and the background process limit, as cleanyourtv's
 * low-RAM profiles do. Old values are read first and kept in the restore point, so the way back is exact
 * (an unset key is deleted again, not set to 1).
 */
object SpeedTweaks {
    val ANIMATION_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    const val PROCESS_LIMIT_KEY = "background_process_limit"
    val ALL_KEYS = ANIMATION_KEYS + PROCESS_LIMIT_KEY
    val PROCESS_LIMITS = listOf(2, 3, 4)

    private val VALUE = Regex("-?[0-9]{1,3}(\\.[0-9]{1,3})?")

    fun validValue(value: String): Boolean = VALUE.matches(value)

    fun readCommand(key: String): String {
        require(key in ALL_KEYS)
        return "settings get global $key"
    }

    /** `null` deletes the key (the TV's own default comes back). */
    fun putCommand(key: String, value: String?): String {
        require(key in ALL_KEYS)
        if (value == null) return "settings delete global $key"
        require(validValue(value))
        return "settings put global $key $value"
    }

    /** `settings get` answers "null" for a key that was never set. */
    fun parse(output: String): String? = output.trim().takeIf { it.isNotEmpty() && it != "null" && validValue(it) }

    /** The changes for [level] and [processLimit] (null = leave the limit alone). */
    fun plan(level: SpeedLevel?, processLimit: Int?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        level?.let { l -> ANIMATION_KEYS.forEach { out[it] = l.scale } }
        processLimit?.let { require(it in PROCESS_LIMITS); out[PROCESS_LIMIT_KEY] = it.toString() }
        return out
    }
}

/** Private DNS with an ad-blocking resolver (AdGuard), as in cleanyourtv; the old mode is kept for undo. */
object PrivateDns {
    const val MODE_KEY = "private_dns_mode"
    const val HOST_KEY = "private_dns_specifier"
    const val ADGUARD = "dns.adguard-dns.com"
    private val HOST = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+")
    private val MODES = setOf("off", "opportunistic", "hostname")

    fun validMode(v: String) = v in MODES
    fun validHost(v: String) = HOST.matches(v)

    fun parse(output: String): String? = output.trim().takeIf { it.isNotEmpty() && it != "null" }

    fun enableCommands(host: String = ADGUARD): List<String> {
        require(validHost(host))
        return listOf("settings put global $HOST_KEY $host", "settings put global $MODE_KEY hostname")
    }

    fun restoreCommands(mode: String?, host: String?): List<String> = listOf(
        if (host != null && validHost(host)) "settings put global $HOST_KEY $host" else "settings delete global $HOST_KEY",
        if (mode != null && validMode(mode)) "settings put global $MODE_KEY $mode" else "settings delete global $MODE_KEY",
    )
}

/**
 * Before/after numbers for one TV: free memory, number of running processes, CPU load at idle and how long the
 * Home screen takes to come up. Measured the same way twice so the user sees whether it helped.
 */
data class TvMeasure(
    val takenAt: Long,
    val memAvailableMb: Int?,
    val memTotalMb: Int?,
    val processes: Int?,
    val loadAvg: Double?,
    val homeStartMs: Int?,
)

object TvMeasureParser {
    fun memKb(meminfo: String, key: String): Int? = meminfo.lineSequence()
        .firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()?.substringBefore(' ')?.toIntOrNull()

    fun processes(psOutput: String): Int? =
        psOutput.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.count().takeIf { it > 1 }?.minus(1)

    fun loadAvg(loadavg: String): Double? = loadavg.trim().substringBefore(' ').toDoubleOrNull()

    /** `am start -W` prints "TotalTime: 812" (newer) or "ThisTime:" (older). */
    fun startMs(amOutput: String): Int? {
        val lines = amOutput.lineSequence().map { it.trim() }
        return (lines.firstOrNull { it.startsWith("TotalTime:") } ?: lines.firstOrNull { it.startsWith("ThisTime:") })
            ?.substringAfter(':')?.trim()?.toIntOrNull()
    }

    fun build(now: Long, meminfo: String, ps: String, loadavg: String, am: String): TvMeasure = TvMeasure(
        takenAt = now,
        memAvailableMb = memKb(meminfo, "MemAvailable")?.div(1024),
        memTotalMb = memKb(meminfo, "MemTotal")?.div(1024),
        processes = processes(ps),
        loadAvg = loadAvg(loadavg),
        homeStartMs = startMs(am),
    )
}

/**
 * Plain-text reports «Помогите добавить приставку» / «Сообщить о проблеме». No IP address, serial number, Android ID
 * or account appears in them, and nothing is sent: the user decides where to share the text.
 */
object OptimizerReports {
    private val IPV4 = Regex("\\b(\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val MAC = Regex("\\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b")
    private val SERIAL = Regex("(?i)(serial|serialno|android_id)\\s*[:=]\\s*\\S+")

    fun redact(text: String): String = text.replace(IPV4, "<ip>").replace(MAC, "<mac>").replace(SERIAL, "$1=<скрыто>")

    fun addTvReport(tv: TvInfo, enabled: Set<String>, disabled: Set<String>, homeHandlers: Set<String>): String = redact(
        buildString {
            appendLine("# Помогите добавить приставку (YAiPlayer «Ускорь ТВ»)")
            appendLine("brand: ${tv.brand}")
            appendLine("manufacturer: ${tv.manufacturer}")
            appendLine("model: ${tv.model}")
            appendLine("android: ${tv.android}")
            appendLine("build: ${tv.fingerprint.substringAfter(':', tv.fingerprint).substringBefore('/')}")
            appendLine("home: ${homeHandlers.sorted().joinToString()}")
            appendLine()
            appendLine("## Включённые приложения (${enabled.size})")
            enabled.sorted().forEach { appendLine(it) }
            appendLine()
            appendLine("## Выключенные приложения (${disabled.size})")
            disabled.sorted().forEach { appendLine(it) }
        },
    )

    fun problemReport(tv: TvInfo?, step: String, problem: String, journal: List<String>): String = redact(
        buildString {
            appendLine("# Сообщить о проблеме (YAiPlayer «Ускорь ТВ»)")
            tv?.let { appendLine("ТВ: ${it.label}, Android ${it.android}") }
            appendLine("Шаг: $step")
            appendLine("Что случилось: $problem")
            appendLine()
            appendLine("## Журнал")
            journal.takeLast(80).forEach { appendLine(it) }
        },
    )
}
