// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
// Ports the theme push of Gathin23/cleanyourtv (MIT: tools/theme.sh, tools/theme_restart.py) to the phone.
package tv.own.owntv.extras.tvoptimizer

import org.json.JSONArray
import org.json.JSONObject

/** One theme of the cleanyourtv launcher: its config.json and the names of its pictures. */
data class LookTheme(
    val name: String,
    val config: JSONObject,
    val wallpaper: Boolean,
    val wallpapers: List<String>,
    val captions: Boolean,
)

data class ThemeCatalog(val commit: String, val themes: List<LookTheme>) {
    fun find(name: String): LookTheme? = themes.firstOrNull { it.name == name }

    companion object {
        private val COMMIT = Regex("[0-9a-f]{40}")

        fun parse(json: String): ThemeCatalog {
            val o = JSONObject(json)
            val commit = o.getString("commit").also { require(COMMIT.matches(it)) }
            val arr = o.getJSONArray("themes")
            val themes = (0 until arr.length()).mapNotNull { i ->
                val t = arr.getJSONObject(i)
                val name = t.optString("name")
                if (!LauncherLook.validName(name)) return@mapNotNull null
                val pics = t.optJSONArray("wallpapers") ?: JSONArray()
                LookTheme(
                    name = name,
                    config = t.optJSONObject("config") ?: JSONObject(),
                    wallpaper = t.optBoolean("wallpaper"),
                    wallpapers = (0 until pics.length()).map { pics.getString(it) }.filter(LauncherLook::validPicture),
                    captions = t.optBoolean("captions"),
                )
            }
            return ThemeCatalog(commit, themes)
        }
    }
}

/** The user's own choices on top of a theme (Launcher settings in cleanyourtv do the same on the TV). */
data class LookOverrides(
    val columns: Int? = null,
    val reducedMotion: Boolean? = null,
    val showStats: Boolean? = null,
    val appLibrary: Boolean? = null,
    val inputsPill: Boolean? = null,
    val dock: List<String>? = null,
    val rotationMinutes: Int? = null,
)

object LauncherLook {
    const val PKG = OptimizerSafety.OUR_LAUNCHER
    const val DIR = "/sdcard/Android/data/$PKG/files"
    const val URI = "content://$PKG.theme"
    const val STAGE_DIR = "/data/local/tmp"
    const val STAGE_PREFIX = "$STAGE_DIR/yai-theme-"
    const val WALLPAPERS = "wallpapers"
    const val OK = "YAI_OK"
    val COLUMNS = listOf(4, 5, 6)
    val ROTATION = listOf(0, 5, 15, 30, 60)

    /** Own photos: what cleanyourtv's wallpaper_prep.py makes (1280×720, under 300 KB). */
    const val PHOTO_W = 1280
    const val PHOTO_H = 720
    const val PHOTO_MAX_BYTES = 300 * 1024
    const val MAX_PHOTOS = 10

    private val NAME = Regex("[A-Za-z0-9_.-]{1,64}")
    private val PICTURE = Regex("(?i)[A-Za-z0-9_.-]{1,64}\\.(jpe?g|png|webp)")
    private val SERVICES = Regex("[A-Za-z0-9_.$/:]*")

    fun validName(v: String) = NAME.matches(v) && ".." !in v
    fun validPicture(v: String) = PICTURE.matches(v) && ".." !in v

    /** Files the launcher's ThemeProvider takes (ThemeFiles.path). */
    fun validTarget(t: String): Boolean = when {
        t == "config.json" || t == "wallpaper.jpg" || t == "theme_revision" -> true
        t.startsWith("$WALLPAPERS/") -> t.removePrefix("$WALLPAPERS/").let { it == "captions.txt" || validPicture(it) }
        else -> false
    }

    fun stagePath(target: String): String {
        require(validTarget(target))
        return STAGE_PREFIX + target.replace('/', '-')
    }

    fun validStage(p: String): Boolean = p.startsWith(STAGE_PREFIX) && validName(p.removePrefix(STAGE_PREFIX))

    /**
     * Writes a staged file through the launcher's provider (works where the shell cannot reach Android/data),
     * else copies it; prints [OK] only when the TV holds the same number of bytes.
     */
    fun putCommand(target: String): String {
        val stage = stagePath(target)
        val dest = "$DIR/$target"
        val parent = dest.substringBeforeLast('/')
        return "mkdir -p $parent 2>/dev/null; s=\$(wc -c < $stage); ok=; " +
            "content write --uri $URI/$target < $stage >/dev/null 2>&1; " +
            "[ \"\$(content read --uri $URI/$target 2>/dev/null | wc -c)\" = \"\$s\" ] && ok=1; " +
            "if [ -z \"\$ok\" ]; then cp $stage $dest 2>/dev/null && [ \"\$(wc -c < $dest)\" = \"\$s\" ] && ok=1; fi; " +
            "rm -f $stage; [ -n \"\$ok\" ] && echo $OK"
    }

    fun deleteCommand(target: String): String {
        require(validTarget(target))
        return "rm -f $DIR/$target 2>/dev/null; content delete --uri $URI/$target >/dev/null 2>&1; true"
    }

    const val CLEAR_COLLECTION = "rm -f $DIR/$WALLPAPERS/*.[jJ][pP][gG] $DIR/$WALLPAPERS/*.[jJ][pP][eE][gG] " +
        "$DIR/$WALLPAPERS/*.[pP][nN][gG] $DIR/$WALLPAPERS/*.[wW][eE][bB][pP] 2>/dev/null; " +
        "content delete --uri $URI/$WALLPAPERS >/dev/null 2>&1; true"

    /** The theme's config.json with the user's choices on top; pictures switch the wallpaper on. */
    fun compose(theme: LookTheme?, o: LookOverrides, ownPhotos: Int): String {
        val c = JSONObject(theme?.config?.toString() ?: "{}")
        o.columns?.let { require(it in COLUMNS); c.put("columns", it) }
        o.reducedMotion?.let { c.put("reduced_motion", it) }
        o.showStats?.let { c.put("show_stats", it) }
        o.appLibrary?.let { c.put("app_library", it) }
        o.inputsPill?.let { c.put("show_inputs_pill", it) }
        o.dock?.let { d ->
            require(d.all { runCatching { AdbCommandHelper.buildEnableCommand(it) }.isSuccess })
            c.put("dock_apps", JSONArray(d.distinct()))
        }
        if (ownPhotos > 0) {
            c.put("wallpaper", true)
            c.remove("wallpaper_captions")
            c.remove("wallpaper_order")
            c.remove("wallpaper_moods")
        }
        o.rotationMinutes?.let { require(it in ROTATION); if (it == 0) c.remove("wallpaper_rotation_minutes") else c.put("wallpaper_rotation_minutes", it) }
        return c.toString(2)
    }

    /** Address of one theme picture at the pinned commit. */
    fun pictureUrl(commit: String, theme: String, file: String): String {
        require(Regex("[0-9a-f]{40}").matches(commit) && validName(theme))
        require(file == "wallpaper.jpg" || file == "$WALLPAPERS/captions.txt" || (file.startsWith("$WALLPAPERS/") && validPicture(file.removePrefix("$WALLPAPERS/"))))
        return "https://raw.githubusercontent.com/Gathin23/cleanyourtv/$commit/themes/$theme/$file"
    }

    /** Apps with a TV icon (`LEANBACK_LAUNCHER`), for the dock. */
    fun parseTvApps(output: String): List<String> = OptimizerSafety.parseHomeHandlers(output).filter { it != PKG }.sorted()

    const val TV_APPS = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"

    fun validServices(v: String) = SERVICES.matches(v)

    /** JPEG quality steps for an own photo: the first that fits under [PHOTO_MAX_BYTES] wins. */
    val QUALITY_STEPS = listOf(88, 80, 72, 64, 56, 48, 40)

    /** Centre crop of a w×h picture to 16:9: left, top, width, height. */
    fun cropTo16x9(w: Int, h: Int): IntArray {
        require(w > 0 && h > 0)
        return if (w * 9L > h * 16L) {
            val cw = (h * 16L / 9).toInt()
            intArrayOf((w - cw) / 2, 0, cw, h)
        } else {
            val ch = (w * 9L / 16).toInt()
            intArrayOf(0, (h - ch) / 2, w, ch)
        }
    }
}

/**
 * The ADB side of the look: files go to /data/local/tmp, then into the launcher's folder, then Home restarts
 * while the launcher's key helpers (accessibility services) stay on, as theme_restart.py does.
 */
class LookEngine(private val transport: AdbTransport) {
    private suspend fun shell(cmd: String): String = transport.runShell(cmd).getOrThrow()

    suspend fun tvApps(): List<String> = runCatching { LauncherLook.parseTvApps(shell(LauncherLook.TV_APPS)) }.getOrDefault(emptyList())

    /** Pushes one file and moves it into place; true when the TV confirms the size. */
    suspend fun put(target: String, file: java.io.File): Boolean = runCatching {
        transport.push(file, LauncherLook.stagePath(target)).getOrThrow()
        shell(LauncherLook.putCommand(target)).lines().any { it.trim() == LauncherLook.OK }
    }.getOrDefault(false)

    suspend fun delete(target: String) = runCatching { shell(LauncherLook.deleteCommand(target)) }.isSuccess

    suspend fun clearCollection() = runCatching { shell(LauncherLook.CLEAR_COLLECTION) }.isSuccess

    /** Back to the launcher's built-in look (tools/theme.sh --reset). */
    suspend fun reset(): Boolean = runCatching {
        shell("mkdir -p ${LauncherLook.DIR} 2>/dev/null; ${LauncherLook.deleteCommand("config.json")}; " +
            "${LauncherLook.deleteCommand("wallpaper.jpg")}; ${LauncherLook.deleteCommand("wallpapers/captions.txt")}; ${LauncherLook.CLEAR_COLLECTION}")
        true
    }.getOrDefault(false)

    suspend fun restartHome(): Boolean = runCatching {
        val user = shell("am get-current-user").trim().takeIf { it.all(Char::isDigit) && it.isNotEmpty() } ?: "0"
        val services = shell("settings --user $user get secure enabled_accessibility_services").trim().let { if (it == "null") "" else it }
        val flag = shell("settings --user $user get secure accessibility_enabled").trim()
        require(LauncherLook.validServices(services))
        val ours = services.split(':').any { it.startsWith(LauncherLook.PKG + "/") }
        shell("am force-stop --user $user ${LauncherLook.PKG}")
        shell("am start --user $user -a android.intent.action.MAIN -c android.intent.category.HOME")
        if (ours && flag == "1") {
            // Android drops force-stopped services; the list is written without and then with them so it rebinds.
            val others = services.split(':').filter { it.isNotEmpty() && !it.startsWith(LauncherLook.PKG + "/") }.joinToString(":")
            kotlinx.coroutines.delay(1_000)
            shell(
                if (others.isEmpty()) "settings --user $user delete secure enabled_accessibility_services"
                else "settings --user $user put secure enabled_accessibility_services '$others'",
            )
            kotlinx.coroutines.delay(2_000)
            shell("settings --user $user put secure enabled_accessibility_services '$services'; settings --user $user put secure accessibility_enabled 1")
        }
        true
    }.getOrDefault(false)
}
