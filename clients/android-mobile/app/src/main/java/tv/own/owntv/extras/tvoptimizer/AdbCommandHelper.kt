// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

/**
 * Pure functions for building and parsing package manager shell commands.
 */
object AdbCommandHelper {

    /**
     * Builds the ADB command to safely disable an application package for the primary user.
     */
    fun buildDisableCommand(packageName: String): String {
        require(packageName.isNotBlank()) { "Package name must not be blank" }
        return "pm disable-user --user 0 ${packageName.trim()}"
    }

    /**
     * Builds the ADB command to re-enable a previously disabled application package.
     */
    fun buildEnableCommand(packageName: String): String {
        require(packageName.isNotBlank()) { "Package name must not be blank" }
        return "pm enable ${packageName.trim()}"
    }

    /**
     * Extracts the target package name from a disable command string, or null if malformed.
     */
    fun parsePackageFromDisable(cmd: String): String? {
        val prefix = "pm disable-user --user 0 "
        if (!cmd.startsWith(prefix)) return null
        return cmd.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Extracts the target package name from an enable command string, or null if malformed.
     */
    fun parsePackageFromEnable(cmd: String): String? {
        val prefix = "pm enable "
        if (!cmd.startsWith(prefix)) return null
        return cmd.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
    }
}
