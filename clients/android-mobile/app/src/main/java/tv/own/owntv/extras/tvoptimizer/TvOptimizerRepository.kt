// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.own.owntv.core.R

interface TvOptimizerRepository {
    suspend fun models(): List<TvModel>
    suspend fun packagesFor(modelId: String): List<TvPackageInfo>
    suspend fun allPackages(): List<TvPackageInfo>
}

class AssetTvOptimizerRepository(
    private val context: Context,
) : TvOptimizerRepository {

    private var cachedModels: List<TvModel>? = null
    private var cachedPackages: List<TvPackageInfo>? = null

    private suspend fun load(): Pair<List<TvModel>, List<TvPackageInfo>> = withContext(Dispatchers.IO) {
        val currentModels = cachedModels
        val currentPackages = cachedPackages
        if (currentModels != null && currentPackages != null) {
            return@withContext currentModels to currentPackages
        }

        val jsonStr = runCatching {
            context.assets.open("tvoptimizer/demo.json").bufferedReader().use { it.readText() }
        }.getOrNull()

        if (jsonStr.isNullOrBlank()) {
            val fallback = fallbackData()
            cachedModels = fallback.first
            cachedPackages = fallback.second
            return@withContext fallback
        }

        val root = JSONObject(jsonStr)
        val modelsJson = root.optJSONArray("models")
        val parsedModels = mutableListOf<TvModel>()
        if (modelsJson != null) {
            for (i in 0 until modelsJson.length()) {
                val obj = modelsJson.getJSONObject(i)
                parsedModels.add(
                    TvModel(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                    )
                )
            }
        }

        val packagesJson = root.optJSONArray("packages")
        val parsedPackages = mutableListOf<TvPackageInfo>()
        if (packagesJson != null) {
            for (i in 0 until packagesJson.length()) {
                val obj = packagesJson.getJSONObject(i)
                val modelsArr = obj.optJSONArray("models")
                val modelList = mutableListOf<String>()
                if (modelsArr != null) {
                    for (m in 0 until modelsArr.length()) {
                        modelList.add(modelsArr.getString(m))
                    }
                }
                parsedPackages.add(
                    TvPackageInfo(
                        packageName = obj.getString("package"),
                        titleKey = obj.getString("titleKey"),
                        descriptionKey = obj.getString("descriptionKey"),
                        risk = PackageRisk.fromString(obj.optString("risk")),
                        recommended = obj.optBoolean("recommended", false),
                        models = modelList,
                    )
                )
            }
        }

        cachedModels = parsedModels
        cachedPackages = parsedPackages
        parsedModels to parsedPackages
    }

    override suspend fun models(): List<TvModel> = load().first

    override suspend fun allPackages(): List<TvPackageInfo> = load().second

    override suspend fun packagesFor(modelId: String): List<TvPackageInfo> {
        val all = allPackages()
        return all.filter { it.models.isEmpty() || it.models.contains(modelId) }
    }

    private fun fallbackData(): Pair<List<TvModel>, List<TvPackageInfo>> {
        val models = listOf(
            TvModel("android_tv_generic", context.getString(R.string.tv_optimizer_vendor_generic)),
            TvModel("xiaomi_mitv", context.getString(R.string.tv_optimizer_vendor_xiaomi)),
            TvModel("sony_bravia", context.getString(R.string.tv_optimizer_vendor_sony)),
            TvModel("tcl_tv", context.getString(R.string.tv_optimizer_vendor_tcl)),
            TvModel("philips_tv", context.getString(R.string.tv_optimizer_vendor_philips)),
        )
        val packages = listOf(
            TvPackageInfo("com.google.android.youtube.tvunplugged", "pkg_youtube_tv_title", "pkg_youtube_tv_desc", PackageRisk.SAFE, true, listOf("android_tv_generic", "xiaomi_mitv", "sony_bravia", "tcl_tv", "philips_tv")),
            TvPackageInfo("com.google.android.play.games", "pkg_play_games_title", "pkg_play_games_desc", PackageRisk.SAFE, true, listOf("android_tv_generic", "xiaomi_mitv", "sony_bravia", "tcl_tv", "philips_tv")),
            TvPackageInfo("com.mitv.tvhome", "pkg_patchwall_title", "pkg_patchwall_desc", PackageRisk.MODERATE, true, listOf("xiaomi_mitv")),
            TvPackageInfo("com.xiaomi.mitv.upgrade", "pkg_xiaomi_upgrade_title", "pkg_xiaomi_upgrade_desc", PackageRisk.MODERATE, false, listOf("xiaomi_mitv")),
            TvPackageInfo("com.sony.dtv.braviasync", "pkg_sony_sync_title", "pkg_sony_sync_desc", PackageRisk.MODERATE, false, listOf("sony_bravia")),
            TvPackageInfo("com.tcl.tvmanager", "pkg_tcl_manager_title", "pkg_tcl_manager_desc", PackageRisk.SAFE, true, listOf("tcl_tv")),
            TvPackageInfo("com.android.printspooler", "pkg_print_spooler_title", "pkg_print_spooler_desc", PackageRisk.SAFE, true, listOf("android_tv_generic", "xiaomi_mitv", "sony_bravia", "tcl_tv", "philips_tv")),
            TvPackageInfo("com.google.android.music", "pkg_google_music_title", "pkg_google_music_desc", PackageRisk.SAFE, true, listOf("android_tv_generic", "xiaomi_mitv", "sony_bravia", "tcl_tv", "philips_tv")),
        )
        return models to packages
    }
}
