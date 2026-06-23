package com.pos.cosmos.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent app settings mirroring the desktop AppSettings/ConfigsForm fields
 * relevant to local-database mode and sync behavior.
 *
 * Stored in plain SharedPreferences (no secrets here — credentials live in SessionManager).
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _useLocalDatabase = MutableStateFlow(prefs.getBoolean(KEY_USE_LOCAL_DB, false))
    val useLocalDatabase: StateFlow<Boolean> = _useLocalDatabase.asStateFlow()

    private val _autoSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SYNC, false))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    private val _autoSyncIntervalMinutes = MutableStateFlow(prefs.getInt(KEY_AUTO_SYNC_INTERVAL, 5))
    val autoSyncIntervalMinutes: StateFlow<Int> = _autoSyncIntervalMinutes.asStateFlow()

    private val _billServiceUrl = MutableStateFlow(
        prefs.getString(KEY_BILL_SERVICE_URL, DEFAULT_BILL_SERVICE_URL) ?: DEFAULT_BILL_SERVICE_URL
    )
    val billServiceUrl: StateFlow<String> = _billServiceUrl.asStateFlow()

    private val _dealerServiceUrl = MutableStateFlow(
        prefs.getString(KEY_DEALER_SERVICE_URL, DEFAULT_DEALER_SERVICE_URL) ?: DEFAULT_DEALER_SERVICE_URL
    )
    val dealerServiceUrl: StateFlow<String> = _dealerServiceUrl.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC_AT, 0L))
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    fun setUseLocalDatabase(value: Boolean) {
        prefs.edit().putBoolean(KEY_USE_LOCAL_DB, value).apply()
        _useLocalDatabase.value = value
    }

    fun setAutoSyncEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()
        _autoSyncEnabled.value = value
    }

    fun setAutoSyncIntervalMinutes(value: Int) {
        val clamped = value.coerceIn(1, 1440)
        prefs.edit().putInt(KEY_AUTO_SYNC_INTERVAL, clamped).apply()
        _autoSyncIntervalMinutes.value = clamped
    }

    fun setBillServiceUrl(value: String) {
        prefs.edit().putString(KEY_BILL_SERVICE_URL, value).apply()
        _billServiceUrl.value = value
    }

    fun setDealerServiceUrl(value: String) {
        prefs.edit().putString(KEY_DEALER_SERVICE_URL, value).apply()
        _dealerServiceUrl.value = value
    }

    fun setLastSyncAt(epochMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_AT, epochMillis).apply()
        _lastSyncAt.value = epochMillis
    }

    /** Per-table sync watermark (mirrors desktop AppSettings.LastSyncPerTable). */
    fun getTableLastSync(table: String): Long =
        prefs.getLong(KEY_LAST_SYNC_TABLE_PREFIX + table, 0L)

    fun setTableLastSync(table: String, epochMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TABLE_PREFIX + table, epochMillis).apply()
    }

    fun clearAllTableLastSync() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_LAST_SYNC_TABLE_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "cosmos_app_settings"
        private const val KEY_USE_LOCAL_DB = "use_local_database"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val KEY_AUTO_SYNC_INTERVAL = "auto_sync_interval_minutes"
        private const val KEY_BILL_SERVICE_URL = "bill_service_url"
        private const val KEY_DEALER_SERVICE_URL = "dealer_service_url"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_TABLE_PREFIX = "last_sync_table_"

        const val DEFAULT_BILL_SERVICE_URL = "https://cosmospos.com:8080"
        const val DEFAULT_DEALER_SERVICE_URL = "https://cosmospos.com:8090"

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
        }
    }
}
