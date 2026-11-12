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
enum class OptimizerStep { WELCOME, DEBUG_GUIDE, CONNECT, CHOOSE_LIST, PLAN, APPLYING, RESULT, MANAGE, LAUNCHER, PRIVACY }

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
                _ui.update { it.copy(connecting = false, connectProblem = problem) }
                return@launch
            }
            prefs.edit().putString(KEY_HOST, s.host.trim()).apply()
            analyze()
            _ui.update { it.copy(connecting = false, step = OptimizerStep.CHOOSE_LIST) }
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

    fun restoreAll() {
        val point = _ui.value.restorePoint ?: return
        viewModelScope.launch {
            point.stockHome?.let { home ->
                if (_ui.value.launcherPhase == LauncherPhase.ACTIVE) {
                    engine.restoreHome(home)
                    savePoint(point.copy(launcherActive = false))
                    _ui.update { it.copy(launcherPhase = LauncherPhase.REVERTED) }
                }
            }
            restore(point.packages)
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
        private const val KEY_HOST = "host"
        private const val KEY_POINTS = "restore_points"
    }
}
