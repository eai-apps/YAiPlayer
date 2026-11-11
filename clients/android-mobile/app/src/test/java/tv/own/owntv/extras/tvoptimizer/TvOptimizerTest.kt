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
}
