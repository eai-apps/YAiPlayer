// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvOptimizerTest {

    @Test
    fun `adb disable command formatting`() {
        val pkg = "com.google.android.youtube.tv"
        val cmd = AdbCommandHelper.buildDisableCommand(pkg)
        assertEquals("pm disable-user --user 0 com.google.android.youtube.tv", cmd)
        assertEquals(pkg, AdbCommandHelper.parsePackageFromDisable(cmd))
    }

    @Test
    fun `adb enable command formatting`() {
        val pkg = "com.google.android.youtube.tv"
        val cmd = AdbCommandHelper.buildEnableCommand(pkg)
        assertEquals("pm enable com.google.android.youtube.tv", cmd)
        assertEquals(pkg, AdbCommandHelper.parsePackageFromEnable(cmd))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `disable command rejects blank package`() {
        AdbCommandHelper.buildDisableCommand("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `enable command rejects blank package`() {
        AdbCommandHelper.buildEnableCommand("")
    }

    @Test
    fun `command parser returns null on malformed commands`() {
        assertNull(AdbCommandHelper.parsePackageFromDisable("pm uninstall com.test"))
        assertNull(AdbCommandHelper.parsePackageFromEnable("pm disable com.test"))
    }

    @Test
    fun `package risk parsing`() {
        assertEquals(PackageRisk.SAFE, PackageRisk.fromString("safe"))
        assertEquals(PackageRisk.SAFE, PackageRisk.fromString(null))
        assertEquals(PackageRisk.SAFE, PackageRisk.fromString("unknown"))
        assertEquals(PackageRisk.MODERATE, PackageRisk.fromString("moderate"))
        assertEquals(PackageRisk.MODERATE, PackageRisk.fromString("caution"))
        assertEquals(PackageRisk.HIGH, PackageRisk.fromString("high"))
        assertEquals(PackageRisk.HIGH, PackageRisk.fromString("danger"))
    }

    @Test
    fun `confirm step requires understand checkbox to proceed`() {
        val stateUnchecked = TvOptimizerUiState(
            currentStep = TvOptimizerStep.CONFIRM,
            selectedPackageNames = setOf("com.xiaomi.mitv.upgrade"),
            understandRiskChecked = false,
        )
        assertFalse("Cannot proceed without understanding risk", stateUnchecked.canProceed)

        val stateChecked = stateUnchecked.copy(understandRiskChecked = true)
        assertTrue("Can proceed once risk is understood", stateChecked.canProceed)

        val stateEmptySelection = stateChecked.copy(selectedPackageNames = emptySet())
        assertFalse("Cannot proceed if no packages are selected", stateEmptySelection.canProceed)
    }

    @Test
    fun `step progression transitions correctly`() {
        val welcome = TvOptimizerUiState(currentStep = TvOptimizerStep.WELCOME)
        assertTrue(welcome.canProceed)

        val connectDisconnected = TvOptimizerUiState(
            currentStep = TvOptimizerStep.CONNECT,
            connectionStatus = ConnectionStatus.DISCONNECTED,
        )
        assertFalse(connectDisconnected.canProceed)

        val connectConnected = connectDisconnected.copy(
            connectionStatus = ConnectionStatus.CONNECTED,
        )
        assertTrue(connectConnected.canProceed)

        val selectModelNone = TvOptimizerUiState(
            currentStep = TvOptimizerStep.SELECT_MODEL,
            selectedModel = null,
        )
        assertFalse(selectModelNone.canProceed)

        val selectModelSelected = selectModelNone.copy(
            selectedModel = TvModel("xiaomi", "Xiaomi TV"),
        )
        assertTrue(selectModelSelected.canProceed)

        val selectPackagesNone = TvOptimizerUiState(
            currentStep = TvOptimizerStep.SELECT_PACKAGES,
            selectedPackageNames = emptySet(),
        )
        assertFalse(selectPackagesNone.canProceed)

        val selectPackagesChosen = selectPackagesNone.copy(
            selectedPackageNames = setOf("com.android.printspooler"),
        )
        assertTrue(selectPackagesChosen.canProceed)
    }

    @Test
    fun `fake adb transport records executed commands`() = runBlocking {
        val transport = FakeAdbTransport(simulateDelayMs = 0L)
        assertFalse(transport.isConnected)

        val connectRes = transport.connect("192.168.1.50", 5555)
        assertTrue(connectRes.isSuccess)
        assertTrue(transport.isConnected)

        val runRes = transport.runShell(AdbCommandHelper.buildDisableCommand("com.test.app"))
        assertTrue(runRes.isSuccess)
        assertEquals(1, transport.history.size)
        assertEquals("pm disable-user --user 0 com.test.app", transport.history[0])

        transport.disconnect()
        assertFalse(transport.isConnected)
    }
}
