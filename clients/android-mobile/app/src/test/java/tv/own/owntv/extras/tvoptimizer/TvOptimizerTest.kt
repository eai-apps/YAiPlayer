// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A TV that answers shell commands from a script; every command is recorded. */
private class ScriptedTv(private val answer: (String) -> String) : AdbTransport {
    val history = mutableListOf<String>()
    override var isConnected = true
    override suspend fun connect(host: String, port: Int): Result<Unit> = Result.success(Unit)
    override suspend fun runShell(cmd: String): Result<String> {
        history += cmd
        return runCatching { answer(cmd) }
    }
    override suspend fun disconnect() { isConnected = false }
}

class TvOptimizerTest {
    private val catalogFile = listOf("src/main/assets/tvoptimizer/catalog.json", "app/src/main/assets/tvoptimizer/catalog.json")
        .map(::File).first { it.isFile }
    private val catalog by lazy { OptimizerCatalog.parse(catalogFile.readText()) }

    private val tiny = OptimizerCatalog.parse(
        """{"source":"t","groups":["ads","tracking","preload","brand","launcher","other"],"notes":["Ads row","Spy"],
        "keep":{"com.vendor.keepme":5},
        "models":[
          {"id":"a","tv":"Acme X1 (AX1)","brand":"Acme","os":"Android TV 11","p":[["com.vendor.ads",0,0],["com.vendor.spy",1,1],["com.vendor.keepme",2,-1],["com.vendor.launcher",4,-1],["com.google.android.katniss",3,-1],["com.android.vending",2,-1]]},
          {"id":"b","tv":"Acme X2","brand":"Acme","os":"","p":[["com.vendor.ads",0,-1],["com.vendor.spy",1,-1]]},
          {"id":"c","tv":"Other","brand":"Zeta","os":"","p":[["com.zeta.x",0,-1],["bad;pkg",0,-1]]}
        ]}""",
    )

    @Test fun `package lists are parsed strictly`() {
        assertEquals(setOf("com.vendor.ads"), OptimizerSafety.parsePackageList("package:com.vendor.ads\n"))
        assertEquals(emptySet<String>(), OptimizerSafety.parsePackageList(""))
        assertNull(OptimizerSafety.parsePackageList("Error: device offline"))
        assertNull(OptimizerSafety.parsePackageList("package:com.vendor.ads;reboot"))
        assertEquals(
            setOf("com.google.android.tvlauncher", "com.gathin.tvhome"),
            OptimizerSafety.parseHomeHandlers("priority=0 preferredOrder=0\ncom.google.android.tvlauncher/.MainActivity\ncom.gathin.tvhome/.MainActivity\nbad line/x y"),
        )
        assertEquals("com.google.android.leanback.ime", OptimizerSafety.parseImePackage("com.google.android.leanback.ime/.LeanbackImeService\n"))
        assertNull(OptimizerSafety.parseImePackage("null"))
    }

    @Test fun `core apps, Home, keyboard and our launcher are protected`() {
        for (name in listOf("android", "com.android.settings", "com.google.android.gms", "com.android.vending", "com.yai.iptv.player.tv",
            "com.vendor.launcher", "com.google.android.tv.remote.service", "com.google.android.tungsten.setupwraith",
            "com.android.providers.tv", "com.vendor.bluetoothremote", "com.vendor.wifi.helper", OptimizerSafety.OUR_LAUNCHER)) {
            assertTrue(name, OptimizerSafety.isProtected(name))
        }
        assertTrue(OptimizerSafety.isProtected("com.vendor.home", homeHandlers = setOf("com.vendor.home")))
        assertTrue(OptimizerSafety.isProtected("com.vendor.kbd", imePackage = "com.vendor.kbd"))
        assertFalse(OptimizerSafety.isProtected("com.vendor.ads"))
        assertFalse(OptimizerSafety.isProtected("com.google.android.feedback"))
        // With our launcher confirmed as Home, the stock launcher may be switched off; ours never.
        assertFalse(OptimizerSafety.isProtected("com.google.android.tvlauncher", setOf("com.google.android.tvlauncher"), launcherActive = true))
        assertTrue(OptimizerSafety.isProtected(OptimizerSafety.OUR_LAUNCHER, launcherActive = true))
        assertTrue(OptimizerSafety.isProtected("com.android.vending", launcherActive = true))
    }

    @Test fun `optional areas are recognised`() {
        assertEquals(OptionalArea.VOICE, OptimizerSafety.optionalArea("com.google.android.katniss"))
        assertEquals(OptionalArea.CASTING, OptimizerSafety.optionalArea("com.google.android.apps.mediashell"))
        assertEquals(OptionalArea.ACCESSIBILITY, OptimizerSafety.optionalArea("com.google.android.marvin.talkback"))
        assertEquals(OptionalArea.UPDATES, OptimizerSafety.optionalArea("com.xiaomi.mitv.upgrade"))
        assertNull(OptimizerSafety.optionalArea("com.android.cellbroadcastreceiver"))
        assertNull(OptimizerSafety.optionalArea("com.vendor.ads"))
    }

    @Test fun `changes are confirmed only by the exact pm answer`() {
        assertFalse(OptimizerSafety.confirmedState("Success", "com.vendor.ads", true))
        assertFalse(OptimizerSafety.confirmedState("Package com.other.ads new state: enabled", "com.vendor.ads", true))
        assertTrue(OptimizerSafety.confirmedState("Package com.vendor.ads new state: enabled\n", "com.vendor.ads", true))
        assertTrue(OptimizerSafety.confirmedState("Package com.vendor.ads new state: disabled-user", "com.vendor.ads", false))
    }

    @Test fun `commands carry no shell syntax`() {
        assertEquals("pm disable-user --user 0 com.a.b", AdbCommandHelper.buildDisableCommand("com.a.b"))
        assertEquals("pm enable --user 0 com.a.b", AdbCommandHelper.buildEnableCommand("com.a.b"))
        assertEquals("com.a.b", AdbCommandHelper.parsePackageFromEnable("pm enable --user 0 com.a.b"))
        for (name in listOf("com.test;reboot", "com.test\nreboot", "com.test extra", "-x", "com..test", "", "   ")) {
            assertTrue(name, runCatching { AdbCommandHelper.buildDisableCommand(name) }.isFailure)
            assertTrue(name, runCatching { AdbCommandHelper.buildEnableCommand(name) }.isFailure)
        }
        assertEquals("input keyevent 3", AdbRemoteCommands.key(3))
        assertEquals("input text Hello%sworld\\!\\;", AdbRemoteCommands.text("Hello world!;"))
        assertTrue(runCatching { AdbRemoteCommands.text("Привет") }.isFailure)
        assertTrue(runCatching { AdbRemoteCommands.key(0) }.isFailure)
    }

    @Test fun `bundled TV lists load completely`() {
        assertEquals(468, catalog.models.size)
        assertTrue(catalog.source.startsWith("Gathin23/cleanyourtv@"))
        assertTrue(catalog.models.all { m -> m.entries.isNotEmpty() && m.entries.all { runCatching { AdbCommandHelper.buildEnableCommand(it.packageName) }.isSuccess } })
        assertTrue(catalog.brands.containsAll(listOf("TCL", "Sony", "Xiaomi", "Philips")))
        assertTrue(catalog.brandConsensus("TCL").isNotEmpty())
        val common = OptimizerPlanner.entriesFor(PlanSource.Common, catalog).map { it.packageName }
        assertTrue(common.contains("com.google.android.feedback"))
        // The common list may contain the setup wizard; the planner must still refuse it.
        val plan = OptimizerPlanner.plan(PlanSource.Common, catalog, common.toSet(), emptySet(), emptySet(), null)
        assertFalse(plan.items.any { it.packageName == "com.google.android.tungsten.setupwraith" })
        assertFalse(plan.items.any { it.packageName.contains("launcher") })
    }

    @Test fun `brand mode keeps only what most lists of the brand switch off`() {
        assertEquals(listOf("com.vendor.ads", "com.vendor.spy"), tiny.brandConsensus("ACME").map { it.packageName })
        assertTrue(tiny.brandConsensus("Zeta").isEmpty()) // one list is not a consensus
        assertEquals(1, tiny.models.first { it.id == "c" }.entries.size) // the invalid name was dropped
        assertEquals("a", tiny.guessModel("Acme", "AX1")?.id)
        assertNull(tiny.guessModel("Acme", "Q9"))
    }

    @Test fun `plan respects the TV, protection and optional areas`() {
        val model = PlanSource.Model(tiny.models.first { it.id == "a" })
        val enabled = setOf("com.vendor.spy", "com.vendor.keepme", "com.vendor.launcher", "com.google.android.katniss", "com.android.vending")
        val plan = OptimizerPlanner.plan(model, tiny, enabled, disabled = setOf("com.vendor.ads"), homeHandlers = setOf("com.vendor.launcher"), imePackage = null)
        assertEquals(listOf("com.vendor.ads"), plan.alreadyOff)
        assertEquals(listOf("com.android.vending", "com.vendor.launcher"), plan.protectedFound)
        val byName = plan.items.associateBy { it.packageName }
        assertTrue(byName.getValue("com.vendor.spy").recommended)
        assertEquals(AppGroup.TRACKING, byName.getValue("com.vendor.spy").group)
        assertEquals("Spy", byName.getValue("com.vendor.spy").note)
        assertFalse(byName.getValue("com.vendor.keepme").recommended) // 5 lists keep it, 1 switches it off
        assertEquals(OptionalArea.VOICE, byName.getValue("com.google.android.katniss").optional)
        assertFalse(byName.getValue("com.google.android.katniss").recommended)

        val withLauncher = OptimizerPlanner.plan(model, tiny, enabled, emptySet(), setOf("com.vendor.launcher", OptimizerSafety.OUR_LAUNCHER), null, launcherActive = true)
        val stock = withLauncher.items.first { it.packageName == "com.vendor.launcher" }
        assertEquals(AppGroup.LAUNCHER, stock.group)
        assertFalse(stock.recommended)
    }

    @Test fun `restore points survive storage and give a computer script`() {
        val p = RestorePoint("tv1", "Acme X1", 42L, setOf("com.b.c", "com.a.b"), "com.vendor.launcher/.Main", true)
        val back = RestorePoint.listFromJson(RestorePoint.listToJson(listOf(p)))
        assertEquals(listOf(p), back)
        assertEquals(
            "# YAiPlayer restore point: Acme X1\nadb shell pm enable --user 0 com.a.b\nadb shell pm enable --user 0 com.b.c\n" +
                "adb shell cmd package set-home-activity --user 0 com.vendor.launcher/.Main",
            OptimizerScripts.restoreScript(p),
        )
        // Broken or hostile entries are dropped, not executed.
        val hostile = """[{"tvId":"x","packages":["com.ok.app","x;reboot"],"stockHome":"a/b;rm"}]"""
        val parsed = RestorePoint.listFromJson(hostile).single()
        assertEquals(setOf("com.ok.app"), parsed.packages)
        assertNull(parsed.stockHome)
        assertTrue(RestorePoint.listFromJson("not json").isEmpty())
    }

    @Test fun `privacy audit names ACR, voice and telemetry`() {
        val found = PrivacyAudit.audit(setOf("tv.samba.ssm", "com.google.android.katniss", "com.miui.tv.analytics", "com.vendor.music"), catalog)
        assertEquals(PrivacyKind.ACR, found.first { it.packageName == "tv.samba.ssm" }.kind)
        assertEquals(PrivacyKind.VOICE, found.first { it.packageName == "com.google.android.katniss" }.kind)
        assertEquals(PrivacyKind.TELEMETRY, found.first { it.packageName == "com.miui.tv.analytics" }.kind)
        assertFalse(found.any { it.packageName == "com.vendor.music" })
    }

    @Test fun `engine confirms each change and reads the TV`() = runBlocking {
        val tv = ScriptedTv { cmd ->
            when {
                cmd == "getprop ro.product.brand" -> "Acme\n"
                cmd == "getprop ro.product.model" -> "AX1\n"
                cmd.startsWith("getprop") -> "\n"
                cmd == "settings get secure android_id" -> "abc\n"
                cmd == "pm disable-user --user 0 com.vendor.ads" -> "Package com.vendor.ads new state: disabled-user\n"
                cmd == "pm disable-user --user 0 com.vendor.bad" -> "Error: java.lang.SecurityException\n"
                cmd.startsWith("cmd package resolve-activity") -> "priority=0\ncom.vendor.launcher/.Main\n"
                cmd == "pm list packages -e --user 0" -> "package:com.vendor.ads\npackage:com.vendor.spy\n"
                else -> error("unexpected $cmd")
            }
        }
        val engine = OptimizerEngine(tv)
        val info = engine.probe()
        assertEquals("Acme AX1", info.label)
        assertEquals(info.id, engine.probe().id)
        assertEquals(24, info.id.length)
        assertTrue(engine.disable("com.vendor.ads"))
        assertFalse(engine.disable("com.vendor.bad"))
        assertEquals("com.vendor.launcher/.Main", engine.currentHome())
        assertEquals(setOf("com.vendor.ads", "com.vendor.spy"), engine.enabledPackages())
    }

    @Test fun `switching Home always arms the TV-side watchdog first`() = runBlocking {
        var home = "com.vendor.launcher/.Main"
        val tv = ScriptedTv { cmd ->
            when {
                cmd == "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity" -> { home = "com.gathin.tvhome/.MainActivity"; "Success\n" }
                cmd.startsWith("cmd package set-home-activity --user 0 ") -> { home = cmd.substringAfterLast(' '); "Success\n" }
                cmd.startsWith("cmd package resolve-activity") -> "$home\n"
                cmd.startsWith("touch ") -> "ok\n"
                else -> ""
            }
        }
        val engine = OptimizerEngine(tv)
        val token = "0123456789abcdef"
        assertTrue(engine.switchHome("com.vendor.launcher/.Main", token))
        val watchdog = tv.history.indexOfFirst { it.startsWith("nohup setsid sh -c 'sleep 150 & wait; [ -f /data/local/tmp/yaiplayer/home-ok-$token ] && exit 0; pm enable --user 0 com.vendor.launcher; cmd package set-home-activity --user 0 com.vendor.launcher/.Main;") }
        val switch = tv.history.indexOf("cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity")
        assertTrue(watchdog in 0 until switch)
        assertTrue(tv.history[watchdog].endsWith("yaiplayer-rollback-$token >/data/local/tmp/yaiplayer/watchdog-$token.log 2>&1 &"))
        assertTrue(engine.confirmHome(token))
        assertTrue(engine.restoreHome("com.vendor.launcher/.Main"))
        assertEquals("com.vendor.launcher/.Main", home)
        // Hostile values never reach the shell.
        assertTrue(runCatching { engine.switchHome("a/b;reboot", token) }.isFailure)
        assertTrue(runCatching { engine.switchHome("com.vendor.launcher/.Main", "x'; reboot") }.isFailure)
        assertNotNull(tv.history.lastOrNull())
    }

    @Test
    fun screenPeekKeepsOnlyBase64() {
        // The shell's base64 output wraps lines and may end with CR/LF; only the alphabet must remain.
        assertEquals("iVBORw0KGgo+/A==", ScreenPeekText.clean("iVBORw0K\r\nGgo+/A==\n"))
        assertEquals("", ScreenPeekText.clean("\n\r\t "))
    }

    /** Task Opus №3, п.12: «undo all» brings back every app that was switched off and the TV's own Home. */
    @Test fun `rollback restores every app and the stock Home`() = runBlocking {
        val off = mutableSetOf("com.vendor.ads", "com.vendor.spy", "com.vendor.launcher")
        var home = "com.gathin.tvhome/.MainActivity"
        val tv = ScriptedTv { cmd ->
            when {
                cmd.startsWith("pm enable --user 0 ") -> {
                    val pkg = cmd.substringAfterLast(' ')
                    // One app the TV refuses to switch on again: it must stay in the restore point.
                    if (pkg == "com.vendor.spy") "Error: java.lang.SecurityException\n"
                    else { off.remove(pkg); "Package $pkg new state: enabled\n" }
                }
                cmd.startsWith("cmd package set-home-activity --user 0 ") -> { home = cmd.substringAfterLast(' '); "Success\n" }
                cmd.startsWith("cmd package resolve-activity") -> "$home\n"
                cmd.startsWith("am start") -> "Status: ok\n"
                else -> ""
            }
        }
        val engine = OptimizerEngine(tv)
        val point = RestorePoint("tv1", "Acme X1", 42L, setOf("com.vendor.ads", "com.vendor.spy"), "com.vendor.launcher/.Main", true)
        assertTrue(engine.restoreHome(point.stockHome!!))
        assertEquals("com.vendor.launcher/.Main", engine.currentHome())
        assertFalse("the stock launcher is switched on again", "com.vendor.launcher" in off)
        val back = point.packages.filter { engine.enable(it) }
        assertEquals(listOf("com.vendor.ads"), back)
        val left = point.copy(packages = point.packages - back.toSet())
        assertEquals(setOf("com.vendor.spy"), left.packages)
        // The script for a computer still covers what the phone could not undo.
        assertTrue(OptimizerScripts.restoreScript(left).contains("adb shell pm enable --user 0 com.vendor.spy"))
        // Nothing but enable / Home commands ever reached the TV during a rollback.
        assertTrue(tv.history.all { it.startsWith("pm enable") || it.startsWith("cmd package") || it.startsWith("am start") })
    }

    /** Owner 12.11: the TV box is found on the Wi-Fi, no address typed. */
    @Test fun `finder tries known addresses first, then the phone's subnet`() {
        val hosts = AdbFinder.subnetHosts("192.168.2.17")
        assertEquals(253, hosts.size)
        assertTrue("192.168.2.43" in hosts)
        assertFalse("192.168.2.17" in hosts)
        assertTrue(AdbFinder.subnetHosts("fe80::1").isEmpty())
        assertEquals(listOf("192.168.2.43", "192.168.2.50"), AdbFinder.candidates(listOf(null, " 192.168.2.43"), listOf("192.168.2.43", "192.168.2.50", "tv.local")))
    }

    // ---- «Ускорь ТВ», этап 1–4 (12.11): группы приставки, скорость, DNS, все команды, замеры, отчёты ----

    private fun tv(brand: String = "Acme", man: String = brand, model: String = "X1", android: String = "10", fp: String = "acme/x1/x1:10/QP1A/1:user/release-keys") =
        TvInfo(brand, man, model, android, fp, "id")

    @Test fun `tv box verdict follows cleanyourtv groups`() {
        assertEquals(TvVerdict.FULL, TvAssessor.assess(tv(), knownModel = true, launcherBundled = true).verdict)
        assertEquals(VerdictReason.UNKNOWN_MODEL, TvAssessor.assess(tv(), knownModel = false, launcherBundled = true).reason)
        assertEquals(VerdictReason.NO_LAUNCHER_BUNDLED, TvAssessor.assess(tv(), knownModel = true, launcherBundled = false).reason)
        assertEquals(VerdictReason.FIRE_OS, TvAssessor.assess(tv(brand = "Amazon"), knownModel = true, launcherBundled = true).reason)
        assertEquals(VerdictReason.OLD_ANDROID, TvAssessor.assess(tv(android = "7.1.2"), knownModel = true, launcherBundled = true).reason)
        assertEquals(VerdictReason.CASTING_DEVICE, TvAssessor.assess(tv(brand = "google", model = "Chromecast"), knownModel = true, launcherBundled = true).reason)
        assertEquals(VerdictReason.CHINA_BUILD, TvAssessor.assess(tv(brand = "TCL", fp = "tcl/cn_t2/x:9/1:user/release-keys"), knownModel = true, launcherBundled = true).reason)
        assertEquals(TvVerdict.NO_LAUNCHER, TvAssessor.assess(tv(android = "7"), knownModel = true, launcherBundled = true).verdict)
    }

    @Test fun `speed plan and commands are strict`() {
        val plan = SpeedTweaks.plan(SpeedLevel.HALF, 2)
        assertEquals(4, plan.size)
        assertEquals("0.5", plan["animator_duration_scale"])
        assertEquals("2", plan["background_process_limit"])
        assertTrue(SpeedTweaks.plan(null, null).isEmpty())
        assertEquals("settings delete global window_animation_scale", SpeedTweaks.putCommand("window_animation_scale", null))
        assertNull(SpeedTweaks.parse("null\n"))
        assertEquals("1.0", SpeedTweaks.parse(" 1.0 "))
        assertTrue(runCatching { SpeedTweaks.putCommand("adb_enabled", "0") }.isFailure)
        assertTrue(runCatching { SpeedTweaks.putCommand("window_animation_scale", "0; reboot") }.isFailure)
        assertTrue(runCatching { SpeedTweaks.plan(null, 9) }.isFailure)
    }

    @Test fun `old settings live in the restore point and come back exactly`() {
        val p = RestorePoint("tv", "Acme X1", 1L, setOf("com.vendor.ads"), null, false,
            linkedMapOf("window_animation_scale" to null, "animator_duration_scale" to "1.0", "private_dns_mode" to "opportunistic"))
        val back = RestorePoint.listFromJson(RestorePoint.listToJson(listOf(p))).single()
        assertEquals(p.settings, back.settings)
        val bad = RestorePoint.listFromJson("""[{"tvId":"t","packages":[],"settings":{"adb_enabled":"1","window_animation_scale":"1; rm -rf /","private_dns_specifier":"evil host"}}]""").single()
        assertTrue(bad.settings.isEmpty())
        val script = OptimizerScripts.restoreScript(p)
        assertTrue("adb shell settings delete global window_animation_scale" in script)
        assertTrue("adb shell settings put global animator_duration_scale 1.0" in script)
        assertTrue("adb shell settings put global private_dns_mode opportunistic" in script)
    }

    @Test fun `global settings are confirmed by reading them back`() = runBlocking {
        val store = mutableMapOf<String, String>()
        val fake = ScriptedTv { cmd ->
            when {
                cmd.startsWith("settings get global ") -> store[cmd.removePrefix("settings get global ")] ?: "null"
                cmd.startsWith("settings put global ") -> { val (k, v) = cmd.removePrefix("settings put global ").split(" "); store[k] = v; "" }
                cmd.startsWith("settings delete global ") -> { store.remove(cmd.removePrefix("settings delete global ")); "" }
                else -> ""
            }
        }
        val engine = OptimizerEngine(fake)
        assertNull(engine.readGlobal("window_animation_scale"))
        assertTrue(engine.putGlobal("window_animation_scale", "0.5"))
        assertEquals("0.5", engine.readGlobal("window_animation_scale"))
        assertTrue(engine.putGlobal("window_animation_scale", null))
        assertNull(engine.readGlobal("window_animation_scale"))
        assertFalse(engine.putGlobal("adb_enabled", "0"))
        assertFalse(fake.history.any { "adb_enabled" in it })
    }

    @Test fun `all commands are shown in order before anything runs`() {
        val text = OptimizerScripts.planScript(setOf("com.vendor.ads"), SpeedTweaks.plan(SpeedLevel.OFF, null), dns = true, launcher = true, debuggingOff = true)
        val lines = text.lines()
        val disable = lines.indexOfFirst { "pm disable-user --user 0 com.vendor.ads" in it }
        val speed = lines.indexOfFirst { "window_animation_scale 0" in it }
        val dns = lines.indexOfFirst { "private_dns_mode hostname" in it }
        val off = lines.indexOfFirst { "adb_enabled 0" in it }
        assertTrue(disable in 0 until speed && speed < dns && dns < off)
        assertFalse("adb_enabled" in OptimizerScripts.planScript(emptySet(), emptyMap(), dns = false, launcher = false, debuggingOff = false))
        assertEquals(listOf("settings delete global private_dns_specifier", "settings put global private_dns_mode off"), PrivateDns.restoreCommands("off", null))
    }

    @Test fun `measurements and reports`() {
        val m = TvMeasureParser.build(
            1L,
            "MemTotal:        1843200 kB\nMemFree:  100 kB\nMemAvailable:     614400 kB\n",
            "PID\n1\n2\n3\n",
            "1.52 1.20 0.90 2/600 1234",
            "Starting: Intent\nStatus: ok\nThisTime: 640\nTotalTime: 812\nComplete",
        )
        assertEquals(600, m.memAvailableMb)
        assertEquals(1800, m.memTotalMb)
        assertEquals(3, m.processes)
        assertEquals(1.52, m.loadAvg!!, 0.001)
        assertEquals(812, m.homeStartMs)
        val r = OptimizerReports.problemReport(tv(), "CONNECT", "нет связи с 192.168.1.50", listOf("mac 00:1A:2B:3C:4D:5E", "serialno=ABC123"))
        assertFalse("192.168.1.50" in r)
        assertFalse("00:1A:2B:3C:4D:5E" in r)
        assertFalse("ABC123" in r)
        val add = OptimizerReports.addTvReport(tv(), setOf("com.vendor.ads"), setOf("com.vendor.spy"), setOf("com.vendor.launcher"))
        assertTrue("com.vendor.ads" in add && "com.vendor.spy" in add && "model: X1" in add)
    }
}
