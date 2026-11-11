// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TvOptimizerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _disabledPackages = MutableStateFlow(readDisabledPackages())
    val disabledPackages: StateFlow<Set<String>> = _disabledPackages.asStateFlow()

    private val _backupTimestamp = MutableStateFlow(readBackupTimestamp())
    val backupTimestamp: StateFlow<Long?> = _backupTimestamp.asStateFlow()

    private fun readDisabledPackages(): Set<String> {
        return prefs.getStringSet(KEY_DISABLED_PACKAGES, null)?.toSet() ?: emptySet()
    }

    private fun readBackupTimestamp(): Long? {
        val ts = prefs.getLong(KEY_BACKUP_TIMESTAMP, -1L)
        return if (ts > 0) ts else null
    }

    suspend fun getDisabledPackages(): Set<String> = readDisabledPackages()

    suspend fun saveDisabledPackages(packages: Set<String>) {
        val current = readDisabledPackages()
        val updated = current + packages
        val now = System.currentTimeMillis()
        prefs.edit()
            .putStringSet(KEY_DISABLED_PACKAGES, updated)
            .putLong(KEY_BACKUP_TIMESTAMP, now)
            .commit().also { check(it) }
        _disabledPackages.value = updated
        _backupTimestamp.value = now
    }

    suspend fun removeDisabledPackages(packages: Set<String>) {
        val current = readDisabledPackages()
        val updated = current - packages
        prefs.edit()
            .putStringSet(KEY_DISABLED_PACKAGES, updated)
            .commit().also { check(it) }
        _disabledPackages.value = updated
    }

    suspend fun clearAll() {
        prefs.edit()
            .remove(KEY_DISABLED_PACKAGES)
            .remove(KEY_BACKUP_TIMESTAMP)
            .commit().also { check(it) }
        _disabledPackages.value = emptySet()
        _backupTimestamp.value = null
    }

    companion object {
        private const val PREFS_NAME = "owntv_tv_optimizer"
        private const val KEY_DISABLED_PACKAGES = "disabled_packages"
        private const val KEY_BACKUP_TIMESTAMP = "backup_timestamp"
    }
}
