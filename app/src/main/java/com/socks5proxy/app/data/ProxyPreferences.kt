package com.socks5proxy.app.data

import android.content.Context
import android.content.SharedPreferences
import com.socks5proxy.app.model.ProxyConfig

class ProxyPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): ProxyConfig {
        return ProxyConfig(
            host = prefs.getString(KEY_HOST, "127.0.0.1") ?: "127.0.0.1",
            port = prefs.getInt(KEY_PORT, 1080),
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            isUdpEnabled = prefs.getBoolean(KEY_UDP_ENABLED, true),
            isPerAppRoutingEnabled = prefs.getBoolean(KEY_PER_APP_ENABLED, true),
            allowedApps = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()
        )
    }

    fun saveConfig(config: ProxyConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_PORT, config.port)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putBoolean(KEY_UDP_ENABLED, config.isUdpEnabled)
            .putBoolean(KEY_PER_APP_ENABLED, config.isPerAppRoutingEnabled)
            .putStringSet(KEY_ALLOWED_APPS, config.allowedApps)
            .apply()
    }

    fun saveAllowedApps(apps: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_ALLOWED_APPS, apps)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "socks5_proxy_prefs"
        private const val KEY_HOST = "proxy_host"
        private const val KEY_PORT = "proxy_port"
        private const val KEY_USERNAME = "proxy_username"
        private const val KEY_PASSWORD = "proxy_password"
        private const val KEY_UDP_ENABLED = "proxy_udp"
        private const val KEY_PER_APP_ENABLED = "proxy_per_app"
        private const val KEY_ALLOWED_APPS = "proxy_allowed_apps"
    }
}
