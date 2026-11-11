// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
// Logic of «Ускорь телевизор» without Android UI: the TV lists, the plan, restore points and the ADB steps.
// The per-model lists come from Gathin23/cleanyourtv (MIT), converted by tools/tvoptimizer/build_catalog.py.
package tv.own.owntv.extras.tvoptimizer

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Group a list puts an app in; the app shows a Russian/English/German name for each. */
enum class AppGroup { ADS, TRACKING, PRELOAD, BRAND, LAUNCHER, OTHER }

data class CatalogEntry(val packageName: String, val group: AppGroup, val note: String?)

data class CatalogModel(val id: String, val name: String, val brand: String, val os: String, val entries: List<CatalogEntry>)

/** The community TV lists. [keepVotes] counts the lists that keep an app on purpose. */
class OptimizerCatalog(
    val source: String,
    val models: List<CatalogModel>,
    private val keepVotes: Map<String, Int>,
) {
    private val disableVotes: Map<String, Int> =
        models.flatMap { m -> m.entries.map { it.packageName } }.groupingBy { it }.eachCount()

    private val bestEntry: Map<String, CatalogEntry> =
        models.flatMap { it.entries }.groupBy { it.packageName }.mapValues { (_, list) ->
            list.groupingBy { it.group }.eachCount().maxBy { it.value }.key.let { g ->
                CatalogEntry(list.first().packageName, g, list.firstNotNullOfOrNull { it.note })
            }
        }

    val brands: List<String> = models.map { it.brand }.filter { it.isNotBlank() }.distinct().sorted()

    fun disableVotes(pkg: String): Int = disableVotes[pkg] ?: 0
    fun keepVotes(pkg: String): Int = keepVotes[pkg] ?: 0
    fun entryFor(pkg: String): CatalogEntry? = bestEntry[pkg]

    fun search(query: String): List<CatalogModel> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return models
        return models.filter { it.name.lowercase().contains(q) || it.brand.lowercase().contains(q) }
    }

    /** Models whose brand matches what the TV reports (case and spaces ignored). */
    fun modelsOfBrand(brand: String): List<CatalogModel> {
        val b = norm(brand)
        if (b.isEmpty()) return emptyList()
        return models.filter { norm(it.brand) == b || norm(it.brand).startsWith(b) || b.startsWith(norm(it.brand).ifEmpty { "\u0000" }) }
    }

    /** The careful brand list: only apps that every list of this brand switches off (needs at least two lists). */
    fun brandConsensus(brand: String): List<CatalogEntry> {
        val lists = modelsOfBrand(brand)
        if (lists.size < 2) return emptyList()
        val common = lists.map { m -> m.entries.map { it.packageName }.toSet() }.reduce { a, b -> a intersect b }
        return common.sorted().mapNotNull { bestEntry[it] }
    }

    /** Best guess of the TV's list from what it reports: every word of the model code found in a list name. */
    fun guessModel(brand: String, model: String): CatalogModel? {
        val code = norm(model)
        if (code.length < 3) return null
        return modelsOfBrand(brand).firstOrNull { norm(it.name).contains(code) }
            ?: models.firstOrNull { norm(it.name).contains(code) }
    }

    companion object {
        private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        fun parse(json: String): OptimizerCatalog {
            val root = JSONObject(json)
            val groups = root.getJSONArray("groups").let { a -> List(a.length()) { a.getString(it) } }
            val notes = root.getJSONArray("notes").let { a -> List(a.length()) { a.getString(it) } }
            val keep = root.optJSONObject("keep")?.let { o -> o.keys().asSequence().associateWith { o.getInt(it) } } ?: emptyMap()
            val modelsJson = root.getJSONArray("models")
            val models = List(modelsJson.length()) { i ->
                val m = modelsJson.getJSONObject(i)
                val rows = m.getJSONArray("p")
                CatalogModel(
                    id = m.getString("id"),
                    name = m.getString("tv"),
                    brand = m.optString("brand"),
                    os = m.optString("os"),
                    entries = List(rows.length()) { r ->
                        val row = rows.getJSONArray(r)
                        val group = groups.getOrNull(row.getInt(1))?.let { groupOf(it) } ?: AppGroup.OTHER
                        CatalogEntry(row.getString(0), group, notes.getOrNull(row.getInt(2)))
                    }.filter { runCatching { AdbCommandHelper.buildEnableCommand(it.packageName) }.isSuccess },
                )
            }
            return OptimizerCatalog(root.optString("source"), models, keep)
        }

        private fun groupOf(name: String): AppGroup = when (name) {
            "ads" -> AppGroup.ADS
            "tracking" -> AppGroup.TRACKING
            "preload" -> AppGroup.PRELOAD
            "brand" -> AppGroup.BRAND
            "launcher" -> AppGroup.LAUNCHER
            else -> AppGroup.OTHER
        }
    }
}

/** What the TV reported about itself. [id] binds restore points to this TV. */
data class TvInfo(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val android: String,
    val fingerprint: String,
    val id: String,
) {
    val label: String get() = listOf(brand.ifBlank { manufacturer }, model).filter { it.isNotBlank() }.joinToString(" ")
}

/** Where the plan's list came from. */
sealed interface PlanSource {
    data class Model(val model: CatalogModel) : PlanSource
    data class Brand(val brand: String, val lists: Int) : PlanSource
    data object Common : PlanSource
}

data class PlanItem(
    val packageName: String,
    val group: AppGroup,
    val note: String?,
    val optional: OptionalArea?,
    val disableVotes: Int,
    val keepVotes: Int,
    /** Ticked when the plan opens: not optional, and not kept by more lists than switch it off. */
    val recommended: Boolean,
)

data class Plan(
    val source: PlanSource,
    val items: List<PlanItem>,
    /** On the TV and in the list, but never touched (Home, Wi-Fi, keyboard, …). */
    val protectedFound: List<String>,
    /** In the list but already switched off on this TV. */
    val alreadyOff: List<String>,
)

object OptimizerPlanner {
    /** Apps that at least this many lists switch off form the common list for TVs with no list of their own. */
    const val COMMON_MIN_VOTES = 25

    fun entriesFor(source: PlanSource, catalog: OptimizerCatalog): List<CatalogEntry> = when (source) {
        is PlanSource.Model -> source.model.entries
        is PlanSource.Brand -> catalog.brandConsensus(source.brand)
        PlanSource.Common -> catalog.models.flatMap { it.entries }.map { it.packageName }.distinct()
            .filter { catalog.disableVotes(it) >= COMMON_MIN_VOTES && catalog.disableVotes(it) > catalog.keepVotes(it) * 2 }
            .mapNotNull { catalog.entryFor(it) }
    }

    fun plan(
        source: PlanSource,
        catalog: OptimizerCatalog,
        enabled: Set<String>,
        disabled: Set<String>,
        homeHandlers: Set<String>,
        imePackage: String?,
    ): Plan {
        val items = ArrayList<PlanItem>()
        val protectedFound = ArrayList<String>()
        val alreadyOff = ArrayList<String>()
        for (e in entriesFor(source, catalog).distinctBy { it.packageName }) {
            val pkg = e.packageName
            when {
                pkg in disabled -> alreadyOff += pkg
                pkg !in enabled -> Unit
                e.group == AppGroup.LAUNCHER || OptimizerSafety.isProtected(pkg, homeHandlers, imePackage) -> protectedFound += pkg
                else -> {
                    val optional = OptimizerSafety.optionalArea(pkg)
                    val keep = catalog.keepVotes(pkg)
                    val off = catalog.disableVotes(pkg)
                    items += PlanItem(pkg, e.group, e.note, optional, off, keep, optional == null && keep <= off)
                }
            }
        }
        return Plan(source, items.sortedWith(compareBy({ it.group.ordinal }, { it.packageName })), protectedFound.sorted(), alreadyOff.sorted())
    }
}

/** One TV's record of what YAiPlayer switched off, written before the first change. */
data class RestorePoint(val tvId: String, val label: String, val createdAt: Long, val packages: Set<String>) {
    fun toJson(): JSONObject = JSONObject().put("tvId", tvId).put("label", label).put("createdAt", createdAt)
        .put("packages", JSONArray(packages.sorted()))

    companion object {
        fun fromJson(o: JSONObject): RestorePoint? {
            val tvId = o.optString("tvId").takeIf { it.isNotBlank() } ?: return null
            val arr = o.optJSONArray("packages") ?: JSONArray()
            val pkgs = List(arr.length()) { arr.optString(it) }
                .filter { runCatching { AdbCommandHelper.buildEnableCommand(it) }.isSuccess }.toSet()
            return RestorePoint(tvId, o.optString("label"), o.optLong("createdAt"), pkgs)
        }

        fun listToJson(points: List<RestorePoint>): String = JSONArray(points.map { it.toJson() }).toString()

        fun listFromJson(json: String?): List<RestorePoint> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { arr.optJSONObject(it) }.mapNotNull { it?.let(::fromJson) }
            }.getOrDefault(emptyList())
        }
    }
}

/** Plain-text scripts for a computer with adb: the same changes, and the way back. */
object OptimizerScripts {
    fun disableScript(packages: Collection<String>): String =
        packages.sorted().joinToString("\n") { "adb shell " + AdbCommandHelper.buildDisableCommand(it) }

    fun restoreScript(point: RestorePoint): String =
        (listOf("# YAiPlayer restore point: ${point.label}") +
            point.packages.sorted().map { "adb shell " + AdbCommandHelper.buildEnableCommand(it) }).joinToString("\n")
}

/** Findings of the tracking audit: matched by package name and by how the community lists file the app. */
enum class PrivacyKind { ACR, VOICE, TELEMETRY }

data class PrivacyFinding(val packageName: String, val kind: PrivacyKind, val listedAsTracking: Int)

object PrivacyAudit {
    private val ACR = listOf("acr", "samba", "alphonso", "inscape", "viewing", "tvinteractive", "gracenote", "automatic.content")
    private val VOICE = listOf("katniss", "voice", "assistant", "alexa", "speech", "googlequicksearchbox", "hotword")
    private val TELEMETRY = listOf("analytics", "telemetry", "statistic", "tracking", "feedback", "logger", "crashreport",
        "usagestats", "bugreport", "diagnos", "adservices", ".ads.", "advert")

    fun audit(installed: Set<String>, catalog: OptimizerCatalog): List<PrivacyFinding> = installed.mapNotNull { pkg ->
        val n = pkg.lowercase()
        val tracking = catalog.models.count { m -> m.entries.any { it.packageName == pkg && it.group == AppGroup.TRACKING } }
        val kind = when {
            ACR.any { n.contains(it) } -> PrivacyKind.ACR
            VOICE.any { n.contains(it) } -> PrivacyKind.VOICE
            TELEMETRY.any { n.contains(it) } || tracking > 0 -> PrivacyKind.TELEMETRY
            else -> return@mapNotNull null
        }
        PrivacyFinding(pkg, kind, tracking)
    }.sortedWith(compareBy({ it.kind.ordinal }, { it.packageName }))
}

/** Keys for the ADB remote: the fallback for TV boxes without Google's remote service. */
object AdbRemoteCommands {
    fun key(keyCode: Int): String {
        require(keyCode in 1..400)
        return "input keyevent $keyCode"
    }

    /** `input text` reads its argument through the shell: spaces become %s and every other symbol is escaped. */
    fun text(value: String): String {
        require(value.isNotEmpty() && value.length <= 200)
        require(value.all { it.code in 32..126 }) { "ASCII only" }
        val escaped = buildString {
            for (c in value) when {
                c == ' ' -> append("%s")
                c.isLetterOrDigit() -> append(c)
                else -> append('\\').append(c)
            }
        }
        return "input text $escaped"
    }
}

/**
 * The ADB steps. Every command goes through [AdbCommandHelper], so a package name can never carry shell syntax.
 * Changes are confirmed by the exact `pm` answer, not by the transport saying the command ran.
 */
class OptimizerEngine(private val transport: AdbTransport) {

    private suspend fun shell(cmd: String): String = transport.runShell(cmd).getOrThrow()

    private suspend fun prop(name: String): String = runCatching { shell("getprop $name").trim() }.getOrDefault("")

    suspend fun probe(): TvInfo {
        val brand = prop("ro.product.brand")
        val manufacturer = prop("ro.product.manufacturer")
        val model = prop("ro.product.model")
        val android = prop("ro.build.version.release")
        val fingerprint = prop("ro.build.fingerprint")
        val androidId = runCatching { shell("settings get secure android_id").trim() }.getOrDefault("")
        val id = MessageDigest.getInstance("SHA-256").digest("$brand|$model|$androidId".toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return TvInfo(brand, manufacturer, model, android, fingerprint, id)
    }

    suspend fun enabledPackages(): Set<String> =
        OptimizerSafety.parsePackageList(shell("pm list packages -e --user 0")) ?: throw IllegalStateException("bad package list")

    suspend fun disabledPackages(): Set<String> =
        OptimizerSafety.parsePackageList(shell("pm list packages -d --user 0")) ?: throw IllegalStateException("bad package list")

    suspend fun homeHandlers(): Set<String> = runCatching {
        OptimizerSafety.parseHomeHandlers(shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"))
    }.getOrDefault(emptySet())

    suspend fun imePackage(): String? = runCatching { OptimizerSafety.parseImePackage(shell("settings get secure default_input_method")) }.getOrNull()

    /** Switches off one app; true only when the TV confirms the new state. */
    suspend fun disable(pkg: String): Boolean =
        runCatching { OptimizerSafety.confirmedState(shell(AdbCommandHelper.buildDisableCommand(pkg)), pkg, false) }.getOrDefault(false)

    suspend fun enable(pkg: String): Boolean =
        runCatching { OptimizerSafety.confirmedState(shell(AdbCommandHelper.buildEnableCommand(pkg)), pkg, true) }.getOrDefault(false)

    suspend fun key(keyCode: Int) = runCatching { shell(AdbRemoteCommands.key(keyCode)) }.isSuccess

    suspend fun text(value: String) = runCatching { shell(AdbRemoteCommands.text(value)) }.isSuccess

    /**
     * Turns off network debugging. The TV drops the connection while answering, so a lost connection here is the
     * expected result, not an error.
     */
    suspend fun disableDebugging() {
        runCatching { shell("settings put global adb_wifi_enabled 0") }
        runCatching { shell("settings put global adb_enabled 0") }
        runCatching { transport.disconnect() }
    }
}
