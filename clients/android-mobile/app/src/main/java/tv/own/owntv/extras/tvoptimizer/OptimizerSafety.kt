// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

/** Parts of the TV that are switched off only when the user ticks them on purpose. */
enum class OptionalArea { ACCESSIBILITY, CASTING, VOICE, UPDATES }

/**
 * What must never be switched off. YAiPlayer installs no launcher, so every app that answers the Home button stays
 * on; so do Wi-Fi, Bluetooth and the remote, the Play Store and Google services, the keyboard, TV inputs, the setup
 * wizard (on Google TV it owns the saved Wi-Fi network), and YAiPlayer itself.
 */
object OptimizerSafety {
    private val NEVER = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.shell",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.inputdevices",
        "com.android.keychain",
        "com.android.externalstorage",
        "com.android.webview",
        "com.google.android.webview",
        "com.android.server.telecom",
        "com.android.location.fused",
        "com.android.tv",
        "com.google.android.tv.remote.service",
        "com.google.android.tungsten.setupwraith",
        "com.google.android.tvsetup",
        "com.android.provision",
    )
    private val NEVER_PREFIXES = listOf(
        "com.android.providers.",
        "com.android.wifi",
        "com.android.networkstack",
        "com.google.android.networkstack",
        "com.android.bluetooth",
        "com.google.android.bluetooth",
        "com.android.connectivity",
        "com.google.android.connectivity",
        "com.android.tethering",
        "com.google.mainline.",
        "com.yai.",
    )
    private val NEVER_WORDS = listOf(
        "launcher", "systemui", "inputmethod", "keyboard", "leanbackime", "setupwizard", "setupwraith",
        "tvinput", "hdmi", "bluetooth", "wifi", "remotecontrol", "rcu", "bluetoothremote", "remoteservice",
    )

    fun parsePackageList(output: String): Set<String>? {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val packages = lines.map { line ->
            if (!line.startsWith("package:")) return null
            val name = line.removePrefix("package:")
            if (runCatching { AdbCommandHelper.buildEnableCommand(name) }.isFailure) return null
            name
        }
        return packages.toSet()
    }

    /** `cmd package query-activities --brief` lists `package/activity` lines; the packages are Home handlers. */
    fun parseHomeHandlers(output: String): Set<String> =
        output.lineSequence().map { it.trim() }
            .filter { it.contains('/') && !it.contains(' ') }
            .map { it.substringBefore('/') }
            .filter { runCatching { AdbCommandHelper.buildEnableCommand(it) }.isSuccess }
            .toSet()

    /** `settings get secure default_input_method` gives `package/.Service`. */
    fun parseImePackage(output: String): String? =
        output.trim().substringBefore('/').takeIf { runCatching { AdbCommandHelper.buildEnableCommand(it) }.isSuccess }

    fun isProtected(name: String, homeHandlers: Set<String> = emptySet(), imePackage: String? = null): Boolean {
        if (name in NEVER || name in homeHandlers || name == imePackage) return true
        if (NEVER_PREFIXES.any { name.startsWith(it) }) return true
        val lower = name.lowercase()
        return NEVER_WORDS.any { lower.contains(it) }
    }

    fun optionalArea(name: String): OptionalArea? {
        val n = name.lowercase()
        return when {
            n.contains("accessibility") || n.contains("talkback") -> OptionalArea.ACCESSIBILITY
            n.contains("mediashell") || n.contains("chromecast") || n.contains("miracast") || n.contains(".cast") ||
                n.contains("dlna") || n.contains("milink") -> OptionalArea.CASTING
            n.contains("katniss") || n.contains("voice") || n.contains("assistant") || n.contains("alexa") ||
                n.contains("speech") || n.contains("googlequicksearchbox") -> OptionalArea.VOICE
            n.contains("update") || n.contains("upgrade") || n.contains(".ota") || n.contains("fota") ||
                n.contains("firmware") -> OptionalArea.UPDATES
            else -> null
        }
    }

    fun confirmedState(output: String, name: String, enabled: Boolean): Boolean {
        val expected = if (enabled) "Package $name new state: enabled" else "Package $name new state: disabled-user"
        return output.trim() == expected
    }
}
