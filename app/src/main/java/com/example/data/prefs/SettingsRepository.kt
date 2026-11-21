package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "dadeban_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val DEFAULT_THREADS = intPreferencesKey("default_threads")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val SPEED_LIMIT_KBPS = intPreferencesKey("global_speed_limit_kbps")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val LOW_BATTERY_PAUSE = booleanPreferencesKey("low_battery_pause")
        val AUTO_RETRY_COUNT = intPreferencesKey("auto_retry_count")
        val AUTO_EXPORT_PUBLIC = booleanPreferencesKey("auto_export_public")
        val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themePreference = runCatching {
                ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.SYSTEM.name)
            }.getOrDefault(ThemePreference.SYSTEM),
            defaultThreadCount = (prefs[Keys.DEFAULT_THREADS] ?: 8).coerceIn(1, 16),
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT] ?: 3).coerceIn(1, 5),
            globalSpeedLimitKbps = (prefs[Keys.SPEED_LIMIT_KBPS] ?: 0).coerceAtLeast(0),
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            lowBatteryPause = prefs[Keys.LOW_BATTERY_PAUSE] ?: true,
            autoRetryCount = (prefs[Keys.AUTO_RETRY_COUNT] ?: 3).coerceIn(0, 10),
            autoExportPublicDownloads = prefs[Keys.AUTO_EXPORT_PUBLIC] ?: true,
            customUserAgent = prefs[Keys.CUSTOM_USER_AGENT]
                ?: "PDM/1.0 (Palang Download Manager; Android)",
            proxyEnabled = prefs[Keys.PROXY_ENABLED] ?: false,
            proxyHost = prefs[Keys.PROXY_HOST] ?: "",
            proxyPort = (prefs[Keys.PROXY_PORT] ?: 8080).coerceIn(1, 65535)
        )
    }

    suspend fun updateTheme(pref: ThemePreference) {
        context.settingsDataStore.edit { it[Keys.THEME] = pref.name }
    }

    suspend fun updateDefaultThreads(threads: Int) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_THREADS] = threads.coerceIn(1, 16) }
    }

    suspend fun updateMaxConcurrent(max: Int) {
        context.settingsDataStore.edit { it[Keys.MAX_CONCURRENT] = max.coerceIn(1, 5) }
    }

    suspend fun updateGlobalSpeedLimit(kbps: Int) {
        context.settingsDataStore.edit { it[Keys.SPEED_LIMIT_KBPS] = kbps.coerceAtLeast(0) }
    }

    suspend fun updateWifiOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun updateLowBatteryPause(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LOW_BATTERY_PAUSE] = enabled }
    }

    suspend fun updateAutoRetryCount(count: Int) {
        context.settingsDataStore.edit { it[Keys.AUTO_RETRY_COUNT] = count.coerceIn(0, 10) }
    }

    suspend fun updateAutoExportPublic(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_EXPORT_PUBLIC] = enabled }
    }

    suspend fun updateCustomUserAgent(ua: String) {
        context.settingsDataStore.edit {
            it[Keys.CUSTOM_USER_AGENT] = ua.trim().ifEmpty {
                "PDM/1.0 (Palang Download Manager; Android)"
            }
        }
    }

    suspend fun updateProxy(enabled: Boolean, host: String, port: Int) {
        context.settingsDataStore.edit {
            it[Keys.PROXY_ENABLED] = enabled
            it[Keys.PROXY_HOST] = host.trim()
            it[Keys.PROXY_PORT] = port.coerceIn(1, 65535)
        }
    }
}
