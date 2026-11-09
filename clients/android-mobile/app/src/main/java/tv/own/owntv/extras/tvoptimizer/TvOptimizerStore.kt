// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tvOptimizerStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_tv_optimizer")

class TvOptimizerStore(private val context: Context) {

    private object Keys {
        val DISABLED_PACKAGES = stringSetPreferencesKey("disabled_packages")
        val BACKUP_TIMESTAMP = longPreferencesKey("backup_timestamp")
    }

    val disabledPackages: Flow<Set<String>> = context.tvOptimizerStore.data.map { prefs ->
        prefs[Keys.DISABLED_PACKAGES] ?: emptySet()
    }

    val backupTimestamp: Flow<Long?> = context.tvOptimizerStore.data.map { prefs ->
        prefs[Keys.BACKUP_TIMESTAMP]
    }

    suspend fun getDisabledPackages(): Set<String> = disabledPackages.first()

    suspend fun saveDisabledPackages(packages: Set<String>) {
        context.tvOptimizerStore.edit { prefs ->
            val current = prefs[Keys.DISABLED_PACKAGES] ?: emptySet()
            prefs[Keys.DISABLED_PACKAGES] = current + packages
            prefs[Keys.BACKUP_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun removeDisabledPackages(packages: Set<String>) {
        context.tvOptimizerStore.edit { prefs ->
            val current = prefs[Keys.DISABLED_PACKAGES] ?: emptySet()
            prefs[Keys.DISABLED_PACKAGES] = current - packages
        }
    }

    suspend fun clearAll() {
        context.tvOptimizerStore.edit { prefs ->
            prefs.remove(Keys.DISABLED_PACKAGES)
            prefs.remove(Keys.BACKUP_TIMESTAMP)
        }
    }
}
