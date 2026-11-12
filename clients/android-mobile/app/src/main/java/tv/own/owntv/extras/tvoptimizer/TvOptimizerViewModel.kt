// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dadb.AdbAuthException
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Wizard order: warning → debugging steps → connection → list choice → plan → applying → result; manage any time. */
enum class OptimizerStep { WELCOME, DEBUG_GUIDE, CONNECT, CHOOSE_LIST, PLAN, APPLYING, RESULT, MANAGE, LAUNCHER, PRIVACY, SPEED, DNS, MEASURE, REPORT, LOOK }

enum class ConnectProblem { NOT_LOCAL, UNREACHABLE, NOT_ALLOWED, OTHER, NOT_FOUND }

enum class LauncherPhase { IDLE, INSTALLING, WAITING_CONFIRM, ACTIVE, FAILED, REVERTED }

data class OptimizerUiState(
    val step: OptimizerStep = OptimizerStep.WELCOME,
    val riskAccepted: Boolean = false,
    val host: String = "",
    val port: String = AdbTransport.DEFAULT_ADB_PORT.toString(),
    val connecting: Boolean = false,
    val connectProblem: ConnectProblem? = null,
    val tv: TvInfo? = null,
    val busy: Boolean = false,
    val catalogReady: Boolean = false,
    val guessed: CatalogModel? = null,
    val modelQuery: String = "",
    val plan: Plan? = null,
    val selected: Set<String> = emptySet(),
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val succeeded: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val restorePoint: RestorePoint? = null,
    val leftovers: List<String> = emptyList(),
    val leftoversChecked: Boolean = false,
    val privacy: List<PrivacyFinding> = emptyList(),
    val launcherBundled: Boolean = false,
    val launcherPhase: LauncherPhase = LauncherPhase.IDLE,
    val watchdogLeft: Int = 0,
    val debuggingOff: Boolean = false,
    /** Owner/cleanyourtv: network debugging goes off at the end unless the user keeps it. */
    val keepDebugging: Boolean = false,
    val assessment: TvAssessment? = null,
    /** Where the user wanted to go before the TV was connected (a function from the start list). */
    val pending: OptimizerStep? = null,
    val speedLevel: SpeedLevel? = SpeedLevel.HALF,
    val processLimit: Int? = null,
    val speedNow: Map<String, String?> = emptyMap(),
    val dnsMode: String? = null,
    val dnsHost: String? = null,
    val measureBefore: TvMeasure? = null,
    val measureAfter: TvMeasure? = null,
    val report: String? = null,
    val message: Int? = null,
    // ---- look of the new Home screen (themes, own photos, dock) ----
    val themes: List<String> = emptyList(),
    val lookTheme: String? = null,
    val look: LookOverrides = LookOverrides(),
    val photos: Int = 0,
    val tvApps: List<String> = emptyList(),
    val launcherInstalled: Boolean = false,
    val lookBusy: Boolean = false,
    val lookResult: Int? = null,
)

class TvOptimizerViewModel(app: Application) : AndroidViewModel(app) {
    private val transport: AdbTransport = DadbTransport(app)
    private val engine = OptimizerEngine(transport)
    private val prefs = app.getSharedPreferences("tv_optimizer_v2", Context.MODE_PRIVATE)
    private var catalog: OptimizerCatalog? = null
    private var enabled: Set<String> = emptySet()
    private var disabled: Set<String> = emptySet()
    private var homeHandlers: Set<String> = emptySet()
    private var ime: String? = null
    private var stockHome: String? = null
    private var token: String? = null
    private var watchdogJob: Job? = null

    private val _ui = MutableStateFlow(
        OptimizerUiState(
            host = prefs.getString(KEY_HOST, "").orEmpty(),
            launcherBundled = runCatching { app.assets.open(LAUNCHER_ASSET).close() }.isSuccess,
        ),
    )
    val ui: StateFlow<OptimizerUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            catalog = withContext(Dispatchers.IO) {
                runCatching { OptimizerCatalog.parse(app.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }) }.getOrNull()
            }
            _ui.update { it.copy(catalogReady = catalog != null) }
        }
    }

    // ---- navigation ----
    fun go(step: OptimizerStep) = _ui.update { it.copy(step = step) }

    /**
     * Opens a function from the start list. Functions that talk to the TV first go through the connection step and
     * then land where the user wanted (owner 12.11: the wizard showed no functions until ADB worked).
     */
    fun open(step: OptimizerStep) {
        val needsTv = step != OptimizerStep.DEBUG_GUIDE && step != OptimizerStep.REPORT
        if (needsTv && _ui.value.tv == null) {
            _ui.update { it.copy(pending = step, step = if (it.riskAccepted) OptimizerStep.CONNECT else OptimizerStep.WELCOME, message = if (it.riskAccepted) null else R_ACCEPT_FIRST) }
            return
        }
        _ui.update { it.copy(step = step, message = null) }
        when (step) {
            OptimizerStep.SPEED -> readSpeed()
            OptimizerStep.DNS -> readDns()
            OptimizerStep.PRIVACY -> runPrivacyAudit()
            OptimizerStep.LOOK -> openLook()
            else -> Unit
        }
    }

    // ---- look: themes, own photos, dock (cleanyourtv tools/theme.sh on the phone) ----
    private val lookEngine = LookEngine(transport)
    private var themeCatalog: ThemeCatalog? = null
    private val photoFiles = ArrayList<File>()

    fun openLook() {
        if (_ui.value.tv == null) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (themeCatalog == null) themeCatalog = withContext(Dispatchers.IO) {
                runCatching { ThemeCatalog.parse(app.assets.open(THEMES_ASSET).bufferedReader().use { it.readText() }) }.getOrNull()
            }
            val apps = lookEngine.tvApps()
            val installed = engine.isInstalled(LauncherLook.PKG)
            val saved = prefs.getString(KEY_LOOK_THEME, null)
            _ui.update {
                it.copy(
                    themes = themeCatalog?.themes?.map { t -> t.name }.orEmpty(),
                    lookTheme = it.lookTheme ?: saved?.takeIf { n -> themeCatalog?.find(n) != null },
                    tvApps = apps,
                    launcherInstalled = installed,
                )
            }
        }
    }

    fun setLookTheme(name: String?) = _ui.update { it.copy(lookTheme = name, lookResult = null) }
    fun setLook(f: (LookOverrides) -> LookOverrides) = _ui.update { it.copy(look = f(it.look), lookResult = null) }

    fun toggleDock(pkg: String) = setLook { o ->
        val d = o.dock.orEmpty()
        o.copy(dock = if (pkg in d) d - pkg else d + pkg)
    }

    /** Own photos from the phone: centre crop 16:9, 1280×720, JPEG under 300 KB (cleanyourtv wallpaper_prep). */
    fun addPhotos(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(lookBusy = true) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                val dir = File(app.cacheDir, "look").apply { mkdirs() }
                for (u in uris) {
                    if (photoFiles.size >= LauncherLook.MAX_PHOTOS) break
                    runCatching { preparePhoto(app, u, File(dir, "photo-${photoFiles.size + 1}.jpg")) }.getOrNull()?.let { photoFiles += it }
                }
            }
            _ui.update { it.copy(lookBusy = false, photos = photoFiles.size, lookResult = null) }
        }
    }

    fun clearPhotos() {
        photoFiles.forEach { it.delete() }
        photoFiles.clear()
        _ui.update { it.copy(photos = 0) }
    }

    private fun preparePhoto(app: Application, uri: android.net.Uri, out: File): File {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        require(opts.outWidth > 0 && opts.outHeight > 0)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= LauncherLook.PHOTO_W && opts.outHeight / (sample * 2) >= LauncherLook.PHOTO_H) sample *= 2
        val src = app.contentResolver.openInputStream(uri).use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("decode")
        val c = LauncherLook.cropTo16x9(src.width, src.height)
        val cropped = android.graphics.Bitmap.createBitmap(src, c[0], c[1], c[2], c[3])
        val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, LauncherLook.PHOTO_W, LauncherLook.PHOTO_H, true)
        for (q in LauncherLook.QUALITY_STEPS) {
            out.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, it) }
            if (out.length() <= LauncherLook.PHOTO_MAX_BYTES) break
        }
        if (scaled !== cropped) scaled.recycle()
        if (cropped !== src) cropped.recycle()
        src.recycle()
        require(out.length() in 1..LauncherLook.PHOTO_MAX_BYTES)
        return out
    }

    private fun download(url: String, out: File): File {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        try {
            check(c.responseCode == 200) { "http ${c.responseCode}" }
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        check(total <= 8L * 1024 * 1024) { "too big" }
                        o.write(buf, 0, n)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return out
    }

    /** Sends the chosen look to the TV and restarts Home. */
    fun applyLook() {
        val s = _ui.value
        if (s.tv == null || s.lookBusy) return
        val theme = s.lookTheme?.let { themeCatalog?.find(it) }
        val commit = themeCatalog?.commit
        _ui.update { it.copy(lookBusy = true, lookResult = null) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val dir = File(app.cacheDir, "look").apply { mkdirs() }
            val ok = runCatching {
                val config = File(dir, "config.json").apply { writeText(LauncherLook.compose(theme, s.look, photoFiles.size)) }
                check(lookEngine.put("config.json", config))
                if (photoFiles.isNotEmpty()) {
                    lookEngine.clearCollection()
                    lookEngine.delete("wallpapers/captions.txt")
                    if (photoFiles.size == 1) {
                        check(lookEngine.put("wallpaper.jpg", photoFiles[0]))
                    } else {
                        lookEngine.delete("wallpaper.jpg")
                        photoFiles.forEachIndexed { i, f -> check(lookEngine.put("wallpapers/photo-${i + 1}.jpg", f)) }
                    }
                } else if (theme != null && commit != null) {
                    if (theme.wallpaper) {
                        val f = withContext(Dispatchers.IO) { download(LauncherLook.pictureUrl(commit, theme.name, "wallpaper.jpg"), File(dir, "theme-wallpaper.jpg")) }
                        check(lookEngine.put("wallpaper.jpg", f))
                    } else {
                        lookEngine.delete("wallpaper.jpg")
                    }
                    lookEngine.clearCollection()
                    for (pic in theme.wallpapers) {
                        val f = withContext(Dispatchers.IO) { download(LauncherLook.pictureUrl(commit, theme.name, "wallpapers/$pic"), File(dir, "theme-$pic")) }
                        check(lookEngine.put("wallpapers/$pic", f))
                    }
                    if (theme.captions) {
                        val f = withContext(Dispatchers.IO) { download(LauncherLook.pictureUrl(commit, theme.name, "wallpapers/captions.txt"), File(dir, "theme-captions.txt")) }
                        check(lookEngine.put("wallpapers/captions.txt", f))
                    } else {
                        lookEngine.delete("wallpapers/captions.txt")
                    }
                } else {
                    lookEngine.delete("wallpaper.jpg")
                    lookEngine.clearCollection()
                }
                val rev = File(dir, "theme_revision").apply { writeText(java.util.UUID.randomUUID().toString().replace("-", "")) }
                check(lookEngine.put("theme_revision", rev))
                check(lookEngine.restartHome())
            }.isSuccess
            if (ok) prefs.edit().putString(KEY_LOOK_THEME, theme?.name).apply()
            log("вид главного экрана: ${theme?.name ?: "свой"}, фото ${photoFiles.size}, ok=$ok")
            _ui.update { it.copy(lookBusy = false, lookResult = if (ok) R_LOOK_OK else R_LOOK_FAIL) }
        }
    }

    /** The launcher's built-in look (tools/theme.sh --reset). */
    fun resetLook() {
        if (_ui.value.tv == null) return
        _ui.update { it.copy(lookBusy = true, lookResult = null) }
        viewModelScope.launch {
            val dir = File(getApplication<Application>().cacheDir, "look").apply { mkdirs() }
            val ok = runCatching {
                check(lookEngine.reset())
                val rev = File(dir, "theme_revision").apply { writeText(java.util.UUID.randomUUID().toString().replace("-", "")) }
                check(lookEngine.put("theme_revision", rev))
                check(lookEngine.restartHome())
            }.isSuccess
            if (ok) prefs.edit().remove(KEY_LOOK_THEME).apply()
            _ui.update { it.copy(lookBusy = false, lookTheme = null, look = LookOverrides(), lookResult = if (ok) R_LOOK_OK else R_LOOK_FAIL) }
        }
    }

    fun setKeepDebugging(v: Boolean) = _ui.update { it.copy(keepDebugging = v) }
    fun setSpeedLevel(v: SpeedLevel?) = _ui.update { it.copy(speedLevel = v) }
    fun setProcessLimit(v: Int?) = _ui.update { it.copy(processLimit = v) }
    fun setRisk(accepted: Boolean) = _ui.update { it.copy(riskAccepted = accepted) }
    fun setHost(v: String) = _ui.update { it.copy(host = v.filter { c -> c.isDigit() || c == '.' }.take(15), connectProblem = null) }
    fun setPort(v: String) = _ui.update { it.copy(port = v.filter(Char::isDigit).take(5), connectProblem = null) }
    fun setQuery(v: String) = _ui.update { it.copy(modelQuery = v) }

    fun models(): List<CatalogModel> = catalog?.search(_ui.value.modelQuery).orEmpty()
    fun brandListCount(): Int = _ui.value.tv?.let { t -> catalog?.modelsOfBrand(t.brand.ifBlank { t.manufacturer })?.size } ?: 0

    // ---- connection and analysis ----
    private var autoTried = false

    /**
     * Owner 12.11: no address, no port. The address the remote uses, the remembered TV boxes, then the whole Wi-Fi
     * is searched for network debugging (5555); found → connects at once. Once per wizard; «Подключиться» repeats.
     */
    fun autoConnect(force: Boolean = false) {
        if (_ui.value.connecting || (autoTried && !force)) return
        autoTried = true
        _ui.update { it.copy(connecting = true, connectProblem = null) }
        viewModelScope.launch {
            val host = AdbFinder.find(getApplication(), listOf(_ui.value.host.takeIf { it.isNotBlank() }))
            if (host == null) {
                _ui.update { it.copy(connecting = false, connectProblem = ConnectProblem.NOT_FOUND) }
                return@launch
            }
            _ui.update { it.copy(host = host, port = AdbTransport.DEFAULT_ADB_PORT.toString(), connecting = false) }
            connect()
        }
    }

    fun connect() {
        val s = _ui.value
        val port = s.port.toIntOrNull()?.takeIf { it in 1..65535 } ?: AdbTransport.DEFAULT_ADB_PORT
        _ui.update { it.copy(connecting = true, connectProblem = null) }
        viewModelScope.launch {
            val r = transport.connect(s.host.trim(), port)
            if (r.isFailure) {
                val e = r.exceptionOrNull()
                val problem = when {
                    e is IllegalArgumentException -> ConnectProblem.NOT_LOCAL
                    e is AdbAuthException -> ConnectProblem.NOT_ALLOWED
                    e is java.net.SocketTimeoutException -> ConnectProblem.NOT_ALLOWED
                    e is java.io.IOException -> ConnectProblem.UNREACHABLE
                    else -> ConnectProblem.OTHER
                }
                log("ошибка подключения: $problem ${e?.javaClass?.simpleName.orEmpty()}")
                _ui.update { it.copy(connecting = false, connectProblem = problem) }
                return@launch
            }
            prefs.edit().putString(KEY_HOST, s.host.trim()).apply()
            log("подключено")
            analyze()
            _ui.update { it.copy(connecting = false, step = it.pending ?: OptimizerStep.CHOOSE_LIST, pending = null) }
            when (_ui.value.step) {
                OptimizerStep.SPEED -> readSpeed()
                OptimizerStep.DNS -> readDns()
                OptimizerStep.PRIVACY -> runPrivacyAudit()
                OptimizerStep.LOOK -> openLook()
                else -> Unit
            }
        }
    }

    private suspend fun analyze() {
        val info = engine.probe()
        enabled = runCatching { engine.enabledPackages() }.getOrDefault(emptySet())
        disabled = runCatching { engine.disabledPackages() }.getOrDefault(emptySet())
        homeHandlers = engine.homeHandlers()
        ime = engine.imePackage()
        val point = loadPoints().firstOrNull { it.tvId == info.id }
        val home = engine.currentHome()
        stockHome = point?.stockHome ?: home?.takeIf { it.substringBefore('/') != OptimizerSafety.OUR_LAUNCHER }
        val launcherActive = point?.launcherActive == true && home?.substringBefore('/') == OptimizerSafety.OUR_LAUNCHER
        _ui.update {
            it.copy(
                tv = info,
                restorePoint = point,
                guessed = catalog?.guessModel(info.brand.ifBlank { info.manufacturer }, info.model),
                assessment = TvAssessor.assess(
                    info,
                    knownModel = catalog?.guessModel(info.brand.ifBlank { info.manufacturer }, info.model) != null,
                    launcherBundled = it.launcherBundled,
                ),
                launcherPhase = if (launcherActive) LauncherPhase.ACTIVE else LauncherPhase.IDLE,
            )
        }
    }

    fun choose(source: PlanSource) {
        val c = catalog ?: return
        val launcherActive = _ui.value.launcherPhase == LauncherPhase.ACTIVE
        val plan = OptimizerPlanner.plan(source, c, enabled, disabled, homeHandlers, ime, launcherActive)
        _ui.update { it.copy(plan = plan, selected = plan.items.filter { i -> i.recommended }.map { i -> i.packageName }.toSet(), step = OptimizerStep.PLAN) }
    }

    fun brandSource(): PlanSource? {
        val t = _ui.value.tv ?: return null
        val brand = t.brand.ifBlank { t.manufacturer }
        val n = catalog?.modelsOfBrand(brand)?.size ?: 0
        return if (n >= 2) PlanSource.Brand(brand, n) else null
    }

    fun toggle(pkg: String) = _ui.update { it.copy(selected = if (pkg in it.selected) it.selected - pkg else it.selected + pkg) }
    fun selectRecommended() = _ui.update { s -> s.copy(selected = s.plan?.items?.filter { it.recommended }?.map { it.packageName }?.toSet().orEmpty()) }
    fun selectAll() = _ui.update { s -> s.copy(selected = s.plan?.items?.map { it.packageName }?.toSet().orEmpty()) }
    fun selectNone() = _ui.update { it.copy(selected = emptySet()) }

    fun commandsText(): String = OptimizerScripts.disableScript(_ui.value.selected)

    /** «Показать все команды»: the whole plan in order, before anything runs. */
    fun allCommandsText(): String {
        val s = _ui.value
        return OptimizerScripts.planScript(
            packages = s.selected,
            speed = SpeedTweaks.plan(s.speedLevel, s.processLimit),
            dns = false,
            launcher = false,
            debuggingOff = !s.keepDebugging,
        )
    }

    // ---- journal for «Сообщить о проблеме» (no addresses: they are cut out of the report) ----
    private val journal = ArrayList<String>()

    private fun log(line: String) {
        if (journal.size > 200) journal.removeAt(0)
        journal += "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())} $line"
    }

    fun helpAddTvReport() {
        val tv = _ui.value.tv ?: return
        _ui.update { it.copy(report = OptimizerReports.addTvReport(tv, enabled, disabled, homeHandlers), step = OptimizerStep.REPORT) }
    }

    fun problemReport(problem: String) {
        val s = _ui.value
        _ui.update { it.copy(report = OptimizerReports.problemReport(s.tv, s.step.name, problem.take(500), journal), step = OptimizerStep.REPORT) }
    }

    // ---- speeding up the TV box itself ----
    fun readSpeed() {
        if (_ui.value.tv == null) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val now = SpeedTweaks.ALL_KEYS.associateWith { k -> runCatching { engine.readGlobal(k) }.getOrNull() }
            _ui.update { it.copy(busy = false, speedNow = now) }
        }
    }

    fun applySpeed() {
        val s = _ui.value
        val tv = s.tv ?: return
        val changes = SpeedTweaks.plan(s.speedLevel, s.processLimit)
        if (changes.isEmpty()) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            changeSettings(tv, changes)
            log("скорость: $changes")
            readSpeed()
        }
    }

    /** Old values go into the restore point before the first change; a key already kept keeps its first old value. */
    private suspend fun changeSettings(tv: TvInfo, changes: Map<String, String?>): Boolean {
        val base = _ui.value.restorePoint ?: RestorePoint(tv.id, tv.label, System.currentTimeMillis(), emptySet(), stockHome)
        val old = LinkedHashMap(base.settings)
        for (k in changes.keys) if (k !in old) {
            val v = runCatching { engine.readGlobal(k) }.getOrElse {
                _ui.update { s -> s.copy(busy = false, message = R_TV_SILENT) }
                return false
            }
            old[k] = v
        }
        val point = base.copy(settings = old)
        savePoint(point)
        _ui.update { it.copy(restorePoint = point) }
        var ok = true
        changes.forEach { (k, v) -> if (!engine.putGlobal(k, v)) ok = false }
        _ui.update { it.copy(busy = false, message = if (ok) null else R_PARTLY) }
        return ok
    }

    /** Puts the given settings back to what they were before YAiPlayer touched them. */
    fun restoreSettings(keys: Collection<String>) {
        val point = _ui.value.restorePoint ?: return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val back = keys.filter { k -> k in point.settings && engine.putGlobal(k, point.settings[k]) }
            val left = point.copy(settings = point.settings - back.toSet())
            savePoint(left)
            log("возвращены настройки: $back")
            _ui.update { it.copy(busy = false, restorePoint = left) }
            if (back.any { it in SpeedTweaks.ALL_KEYS }) readSpeed()
            if (back.any { it == PrivateDns.MODE_KEY }) readDns()
        }
    }

    // ---- Private DNS ----
    fun readDns() {
        if (_ui.value.tv == null) return
        viewModelScope.launch {
            val mode = runCatching { engine.readGlobal(PrivateDns.MODE_KEY) }.getOrNull()
            val host = runCatching { engine.readGlobal(PrivateDns.HOST_KEY) }.getOrNull()
            _ui.update { it.copy(dnsMode = mode, dnsHost = host) }
        }
    }

    fun enableDns() {
        val tv = _ui.value.tv ?: return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            changeSettings(tv, linkedMapOf(PrivateDns.HOST_KEY to PrivateDns.ADGUARD, PrivateDns.MODE_KEY to "hostname"))
            log("DNS AdGuard")
            readDns()
        }
    }

    // ---- before/after numbers ----
    fun measure(after: Boolean) {
        if (_ui.value.tv == null) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val m = engine.measure(System.currentTimeMillis())
            prefs.edit().putString(if (after) KEY_AFTER else KEY_BEFORE, "${m.memAvailableMb}|${m.processes}|${m.loadAvg}|${m.homeStartMs}").apply()
            log("замер ${if (after) "после" else "до"}: $m")
            _ui.update { if (after) it.copy(busy = false, measureAfter = m) else it.copy(busy = false, measureBefore = m) }
        }
    }

    /** «Готово»: network debugging goes off unless the user chose to keep it (cleanyourtv does the same). */
    fun finish(onDone: () -> Unit) {
        val s = _ui.value
        if (s.tv == null || s.keepDebugging || s.debuggingOff) { onDone(); return }
        viewModelScope.launch {
            engine.disableDebugging()
            _ui.update { it.copy(debuggingOff = true) }
            onDone()
        }
    }

    // ---- apply and roll back ----
    fun apply() {
        val s = _ui.value
        val tv = s.tv ?: return
        val pkgs = s.selected.toList().sorted()
        if (pkgs.isEmpty() || !s.riskAccepted) return
        // The restore point is written before the first change.
        val before = s.restorePoint ?: RestorePoint(tv.id, tv.label, System.currentTimeMillis(), emptySet(), stockHome)
        val point = before.copy(packages = before.packages + pkgs)
        savePoint(point)
        _ui.update { it.copy(step = OptimizerStep.APPLYING, progressDone = 0, progressTotal = pkgs.size, succeeded = emptyList(), failed = emptyList(), restorePoint = point) }
        viewModelScope.launch {
            val ok = ArrayList<String>()
            val bad = ArrayList<String>()
            pkgs.forEachIndexed { i, p ->
                if (engine.disable(p)) ok += p else bad += p
                _ui.update { it.copy(progressDone = i + 1) }
            }
            // Only what was really switched off stays in the restore point.
            val kept = before.packages + ok
            val final = point.copy(packages = kept)
            savePoint(final)
            disabled = disabled + ok
            enabled = enabled - ok.toSet()
            _ui.update { it.copy(step = OptimizerStep.RESULT, succeeded = ok, failed = bad, restorePoint = final) }
        }
    }

    fun restore(pkgs: Collection<String>) {
        val point = _ui.value.restorePoint ?: return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val back = pkgs.filter { engine.enable(it) }
            val left = point.copy(packages = point.packages - back.toSet())
            savePoint(left)
            enabled = enabled + back
            disabled = disabled - back.toSet()
            _ui.update { it.copy(busy = false, restorePoint = left, leftovers = it.leftovers - back.toSet()) }
        }
    }

    /** Everything back in one pass: Home screen, settings, then apps (one coroutine, so the restore point stays exact). */
    fun restoreAll() {
        val point = _ui.value.restorePoint ?: return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            var launcherOff = !point.launcherActive
            point.stockHome?.let { home ->
                if (_ui.value.launcherPhase == LauncherPhase.ACTIVE || point.launcherActive) {
                    launcherOff = engine.restoreHome(home)
                    if (launcherOff) _ui.update { it.copy(launcherPhase = LauncherPhase.REVERTED) }
                }
            }
            val settingsBack = point.settings.keys.filter { k -> engine.putGlobal(k, point.settings[k]) }
            val appsBack = point.packages.filter { engine.enable(it) }
            val left = point.copy(
                packages = point.packages - appsBack.toSet(),
                settings = point.settings - settingsBack.toSet(),
                launcherActive = point.launcherActive && !launcherOff,
            )
            savePoint(left)
            enabled = enabled + appsBack
            disabled = disabled - appsBack.toSet()
            homeHandlers = engine.homeHandlers()
            log("вернуть всё: приложения ${appsBack.size}, настройки ${settingsBack.size}")
            _ui.update { it.copy(busy = false, restorePoint = left, leftovers = it.leftovers - appsBack.toSet()) }
        }
    }

    /** After a firmware update: what YAiPlayer switched off but is on again. */
    fun checkLeftovers() {
        val point = _ui.value.restorePoint ?: return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            enabled = runCatching { engine.enabledPackages() }.getOrDefault(enabled)
            _ui.update { it.copy(busy = false, leftovers = point.packages.filter { p -> p in enabled }.sorted(), leftoversChecked = true) }
        }
    }

    fun disableLeftovers() {
        val left = _ui.value.leftovers
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val ok = left.filter { engine.disable(it) }
            _ui.update { it.copy(busy = false, leftovers = left - ok.toSet()) }
        }
    }

    fun restoreScript(): String? = _ui.value.restorePoint?.let(OptimizerScripts::restoreScript)

    fun runPrivacyAudit() {
        val c = catalog ?: return
        _ui.update { it.copy(privacy = PrivacyAudit.audit(enabled, c), step = OptimizerStep.PRIVACY) }
    }

    fun disableDebugging() {
        viewModelScope.launch {
            engine.disableDebugging()
            _ui.update { it.copy(debuggingOff = true) }
        }
    }

    // ---- ADB remote (for TV boxes without Google's remote service) ----
    fun key(code: Int) = viewModelScope.launch { engine.key(code) }
    fun text(value: String) = viewModelScope.launch { runCatching { engine.text(value) } }

    // ---- our launcher ----
    fun installLauncher() {
        val tv = _ui.value.tv ?: return
        val home = stockHome
        if (home == null) {
            _ui.update { it.copy(launcherPhase = LauncherPhase.FAILED) }
            return
        }
        _ui.update { it.copy(launcherPhase = LauncherPhase.INSTALLING) }
        viewModelScope.launch {
            val apk = withContext(Dispatchers.IO) {
                runCatching {
                    File(getApplication<Application>().cacheDir, "tvhome.apk").also { f ->
                        getApplication<Application>().assets.open(LAUNCHER_ASSET).use { input -> f.outputStream().use { input.copyTo(it) } }
                    }
                }.getOrNull()
            }
            if (apk == null || transport.install(apk).isFailure) {
                _ui.update { it.copy(launcherPhase = LauncherPhase.FAILED) }
                return@launch
            }
            // The restore point remembers the stock Home before anything changes.
            val point = (_ui.value.restorePoint ?: RestorePoint(tv.id, tv.label, System.currentTimeMillis(), emptySet())).copy(stockHome = home)
            savePoint(point)
            _ui.update { it.copy(restorePoint = point) }
            val t = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            token = t
            val switched = runCatching { engine.switchHome(home, t, WATCHDOG_SECONDS) }.getOrDefault(false)
            if (!switched) {
                engine.restoreHome(home)
                _ui.update { it.copy(launcherPhase = LauncherPhase.FAILED) }
                return@launch
            }
            _ui.update { it.copy(launcherPhase = LauncherPhase.WAITING_CONFIRM, watchdogLeft = WATCHDOG_SECONDS) }
            watchdogJob?.cancel()
            watchdogJob = viewModelScope.launch {
                while (_ui.value.watchdogLeft > 0 && _ui.value.launcherPhase == LauncherPhase.WAITING_CONFIRM) {
                    delay(1_000)
                    _ui.update { it.copy(watchdogLeft = it.watchdogLeft - 1) }
                }
                if (_ui.value.launcherPhase == LauncherPhase.WAITING_CONFIRM) _ui.update { it.copy(launcherPhase = LauncherPhase.REVERTED) }
            }
        }
    }

    /** The user sees the new Home screen and the remote works on it. */
    fun confirmLauncher() {
        val t = token ?: return
        viewModelScope.launch {
            if (engine.confirmHome(t)) {
                watchdogJob?.cancel()
                _ui.value.restorePoint?.let { savePoint(it.copy(launcherActive = true)); _ui.update { s -> s.copy(restorePoint = it.copy(launcherActive = true)) } }
                homeHandlers = engine.homeHandlers()
                _ui.update { it.copy(launcherPhase = LauncherPhase.ACTIVE) }
            }
        }
    }

    /** Back to the TV's own Home screen now (also used when the new one does not work). */
    fun revertLauncher(uninstall: Boolean) {
        val home = _ui.value.restorePoint?.stockHome ?: stockHome ?: return
        watchdogJob?.cancel()
        viewModelScope.launch {
            engine.restoreHome(home)
            if (uninstall) engine.uninstallLauncher()
            _ui.value.restorePoint?.let { savePoint(it.copy(launcherActive = false)); _ui.update { s -> s.copy(restorePoint = it.copy(launcherActive = false)) } }
            homeHandlers = engine.homeHandlers()
            _ui.update { it.copy(launcherPhase = LauncherPhase.REVERTED) }
        }
    }

    // ---- storage ----
    private fun loadPoints(): List<RestorePoint> = RestorePoint.listFromJson(prefs.getString(KEY_POINTS, null))

    private fun savePoint(p: RestorePoint) {
        val all = loadPoints().filterNot { it.tvId == p.tvId } + p
        prefs.edit().putString(KEY_POINTS, RestorePoint.listToJson(all)).commit()
    }

    override fun onCleared() {
        val t = transport
        Thread { kotlinx.coroutines.runBlocking { t.disconnect() } }.start()
        super.onCleared()
    }

    companion object {
        const val CATALOG_ASSET = "tvoptimizer/catalog.json"
        const val LAUNCHER_ASSET = "tvoptimizer/launcher.apk"
        const val WATCHDOG_SECONDS = 150
        const val THEMES_ASSET = "tvoptimizer/themes.json"
        private const val KEY_LOOK_THEME = "look_theme"
        val R_LOOK_OK = tv.own.owntv.core.R.string.opt_look_ok
        val R_LOOK_FAIL = tv.own.owntv.core.R.string.opt_look_fail
        private const val KEY_BEFORE = "measure_before"
        private const val KEY_AFTER = "measure_after"
        val R_ACCEPT_FIRST = tv.own.owntv.core.R.string.opt_msg_accept_first
        val R_TV_SILENT = tv.own.owntv.core.R.string.opt_msg_tv_silent
        val R_PARTLY = tv.own.owntv.core.R.string.opt_msg_partly
        private const val KEY_HOST = "host"
        private const val KEY_POINTS = "restore_points"
    }
}
