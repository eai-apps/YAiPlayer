// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

enum class PackageRisk {
    SAFE,
    MODERATE,
    HIGH;

    companion object {
        fun fromString(value: String?): PackageRisk = when (value?.lowercase()?.trim()) {
            "moderate", "caution" -> MODERATE
            "high", "danger" -> HIGH
            else -> SAFE
        }
    }
}

data class TvModel(
    val id: String,
    val name: String,
)

data class TvPackageInfo(
    val packageName: String,
    val titleKey: String,
    val descriptionKey: String,
    val risk: PackageRisk,
    val recommended: Boolean,
    val models: List<String> = emptyList(),
)

enum class TvOptimizerStep {
    WELCOME,
    DEBUG_GUIDE,
    CONNECT,
    SELECT_MODEL,
    SELECT_PACKAGES,
    CONFIRM,
    APPLYING,
    RESULT,
    ROLLBACK;

    fun hasPrevious(): Boolean = this != WELCOME && this != APPLYING && this != RESULT
}

data class OptimizationBackup(
    val disabledPackages: Set<String>,
    val timestamp: Long = System.currentTimeMillis(),
)
