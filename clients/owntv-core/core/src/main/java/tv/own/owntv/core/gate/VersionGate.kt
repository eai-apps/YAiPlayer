package tv.own.owntv.core.gate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.own.owntv.core.CoreBuildInfo
import java.time.LocalDate
import java.time.ZoneId

/** What the app must show about its own version. */
sealed interface GateState {
    data object Ok : GateState
    /** This version stops working in [daysLeft] days ([stage] identifies the reminder so it is shown once). */
    data class Warn(val daysLeft: Int, val stage: Int, val updateUrl: String) : GateState
    data class Block(val updateUrl: String) : GateState
}

/** The remote switch (file `app-config.json` in the repo root). Off unless `enforce` is true. */
data class GateConfig(val enforce: Boolean, val minVersion: String, val blockAtMs: Long?, val updateUrl: String)

/** Pure decision, unit-tested: no network, no clock of its own. */
object VersionGateLogic {
    private const val DAY_MS = 86_400_000L

    fun parseConfig(json: String, fallbackUrl: String): GateConfig? = runCatching {
        val o = JSONObject(json)
        val blockAt = o.optString("block_at").takeIf { it.isNotBlank() }?.let {
            LocalDate.parse(it).plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
        GateConfig(
            enforce = o.optBoolean("enforce", false),
            minVersion = o.optString("min_version"),
            blockAtMs = blockAt,
            updateUrl = o.optString("update_url").ifBlank { fallbackUrl },
        )
    }.getOrNull()

    /** "2.0.45" vs "2.0.44": numeric, part by part. */
    fun isOlder(version: String, than: String): Boolean {
        val a = version.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val b = than.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    fun decide(cfg: GateConfig?, currentVersion: String, nowMs: Long): GateState {
        if (cfg == null || !cfg.enforce || cfg.minVersion.isBlank()) return GateState.Ok
        if (!isOlder(currentVersion, cfg.minVersion)) return GateState.Ok
        val at = cfg.blockAtMs ?: return GateState.Block(cfg.updateUrl)
        if (nowMs >= at) return GateState.Block(cfg.updateUrl)
        val days = ((at - nowMs + DAY_MS - 1) / DAY_MS).toInt()
        // Reminders: 4, 3, 2 and 1 weeks out, then each of the last three days.
        val stage = if (days <= 3) days else (days + 6) / 7 + 10
        return if (days <= 28) GateState.Warn(days, stage, cfg.updateUrl) else GateState.Ok
    }
}

class VersionGate(context: Context, private val client: OkHttpClient) {
    private val prefs = context.applicationContext.getSharedPreferences("version_gate", Context.MODE_PRIVATE)

    /** Fail-open: any network or parse problem is [GateState.Ok]. A warning already shown for this stage is skipped. */
    suspend fun check(nowMs: Long = System.currentTimeMillis()): GateState = withContext(Dispatchers.IO) {
        val fallback = DEFAULT_UPDATE_URL
        val body = runCatching {
            val req = Request.Builder().url("https://raw.githubusercontent.com/${CoreBuildInfo.releaseRepo}/main/app-config.json").build()
            client.newCall(req).execute().use { if (it.isSuccessful) it.body.string() else null }
        }.getOrNull() ?: return@withContext GateState.Ok
        val state = VersionGateLogic.decide(VersionGateLogic.parseConfig(body, fallback), CoreBuildInfo.versionName, nowMs)
        if (state is GateState.Warn && prefs.getInt("warned_stage", -1) == state.stage) GateState.Ok else state
    }

    fun markWarned(state: GateState.Warn) { prefs.edit().putInt("warned_stage", state.stage).apply() }

    companion object {
        const val DEFAULT_UPDATE_URL = "https://yai-iptv-player.pages.dev"
    }
}
