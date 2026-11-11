// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

object OptimizerSafety {
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

    // Conservative until device-specific launcher resolution is integrated.
    fun isProtected(name: String): Boolean =
        name == "android" || name.startsWith("com.android.") ||
            name.startsWith("com.google.") || name.startsWith("com.yai.") ||
            name.contains("launcher", ignoreCase = true) || name.contains("systemui", ignoreCase = true)

    fun confirmedState(output: String, name: String, enabled: Boolean): Boolean {
        val expected = if (enabled) "Package $name new state: enabled" else "Package $name new state: disabled-user"
        return output.trim() == expected
    }
}
