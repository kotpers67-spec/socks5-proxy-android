package com.socks5proxy.app.data

import android.content.Context
import android.content.SharedPreferences
import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject

class ProxyPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): ProxyConfig {
        val modeStr = prefs.getString(KEY_ENGINE_MODE, ProxyEngineMode.LOCAL_PROXY.name) ?: ProxyEngineMode.LOCAL_PROXY.name
        val mode = try {
            ProxyEngineMode.valueOf(modeStr)
        } catch (_: Exception) {
            ProxyEngineMode.LOCAL_PROXY
        }

        val profilesJson = prefs.getString(KEY_PROFILES, null)
        val profiles = if (!profilesJson.isNullOrBlank()) {
            deserializeProfiles(profilesJson)
        } else {
            val legacyHost = prefs.getString("proxy_host", "127.0.0.1") ?: "127.0.0.1"
            val legacyPort = prefs.getInt("proxy_port", 1080)
            val legacyUser = prefs.getString("proxy_username", "") ?: ""
            val legacyPass = prefs.getString("proxy_password", "") ?: ""
            val legacyUdp = prefs.getBoolean("proxy_udp", true)
            listOf(
                ProxyProfile(
                    name = "Основной прокси",
                    host = legacyHost,
                    port = legacyPort,
                    username = legacyUser,
                    password = legacyPass,
                    isUdpEnabled = legacyUdp
                )
            )
        }

        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, profiles.firstOrNull()?.id ?: "") ?: ""

        return ProxyConfig(
            engineMode = mode,
            localListenPort = prefs.getInt(KEY_LOCAL_PORT, 10808),
            isAutoStartEnabled = prefs.getBoolean(KEY_AUTO_START, false),
            isFallbackDirectEnabled = prefs.getBoolean(KEY_FALLBACK_DIRECT, true),
            isPerAppRoutingEnabled = prefs.getBoolean(KEY_PER_APP_ENABLED, true),
            allowedApps = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet(),
            profiles = if (profiles.isNotEmpty()) profiles else listOf(ProxyProfile()),
            activeProfileId = activeId
        )
    }

    fun saveConfig(config: ProxyConfig) {
        prefs.edit()
            .putString(KEY_ENGINE_MODE, config.engineMode.name)
            .putInt(KEY_LOCAL_PORT, config.localListenPort)
            .putBoolean(KEY_AUTO_START, config.isAutoStartEnabled)
            .putBoolean(KEY_FALLBACK_DIRECT, config.isFallbackDirectEnabled)
            .putBoolean(KEY_PER_APP_ENABLED, config.isPerAppRoutingEnabled)
            .putStringSet(KEY_ALLOWED_APPS, config.allowedApps)
            .putString(KEY_PROFILES, serializeProfiles(config.profiles))
            .putString(KEY_ACTIVE_PROFILE_ID, config.activeProfileId)
            .apply()
    }

    fun saveAllowedApps(apps: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_ALLOWED_APPS, apps)
            .apply()
    }

    private fun serializeProfiles(profiles: List<ProxyProfile>): String {
        val array = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("host", p.host)
            obj.put("port", p.port)
            obj.put("username", p.username)
            obj.put("password", p.password)
            obj.put("isUdpEnabled", p.isUdpEnabled)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeProfiles(json: String): List<ProxyProfile> {
        val result = mutableListOf<ProxyProfile>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    ProxyProfile(
                        id = obj.optString("id"),
                        name = obj.optString("name", "Прокси"),
                        host = obj.optString("host", "127.0.0.1"),
                        port = obj.optInt("port", 1080),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        isUdpEnabled = obj.optBoolean("isUdpEnabled", true)
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    companion object {
        private const val PREFS_NAME = "socks5_proxy_prefs"
        private const val KEY_ENGINE_MODE = "proxy_engine_mode"
        private const val KEY_LOCAL_PORT = "proxy_local_port"
        private const val KEY_AUTO_START = "proxy_auto_start"
        private const val KEY_FALLBACK_DIRECT = "proxy_fallback_direct"
        private const val KEY_PER_APP_ENABLED = "proxy_per_app"
        private const val KEY_ALLOWED_APPS = "proxy_allowed_apps"
        private const val KEY_PROFILES = "proxy_profiles_list"
        private const val KEY_ACTIVE_PROFILE_ID = "proxy_active_profile_id"
    }
}
