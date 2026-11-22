package com.socks5proxy.app.data

import android.content.Context
import android.content.SharedPreferences
import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.model.VpnServerProfile
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
        val rawProfiles = if (!profilesJson.isNullOrBlank()) {
            deserializeProfiles(profilesJson)
        } else {
            emptyList()
        }
        val profiles = if (rawProfiles.isNotEmpty() && rawProfiles.any { it.isValid && it.host != "127.0.0.1" }) {
            rawProfiles
        } else {
            listOf(
                ProxyProfile(
                    name = "Основной прокси",
                    host = "72.56.103.114",
                    port = 48471,
                    username = "proxy_user",
                    password = "9Kvo904s1bhsazPn",
                    isUdpEnabled = true
                )
            )
        }

        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, profiles.firstOrNull()?.id ?: "") ?: ""
        val vpnProfiles = deserializeVpnProfiles(prefs.getString(KEY_VPN_PROFILES, null))

        return ProxyConfig(
            engineMode = mode,
            localListenPort = prefs.getInt(KEY_LOCAL_PORT, 10808),
            isAutoStartEnabled = prefs.getBoolean(KEY_AUTO_START, false),
            isFallbackDirectEnabled = prefs.getBoolean(KEY_FALLBACK_DIRECT, false),
            isPerAppRoutingEnabled = prefs.getBoolean(KEY_PER_APP_ENABLED, false),
            allowedApps = prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet(),
            vpnProfiles = vpnProfiles,
            activeVpnProfileId = prefs.getString(KEY_ACTIVE_VPN_PROFILE_ID, vpnProfiles.firstOrNull()?.id ?: "") ?: "",
            isVpnToSocksChainEnabled = prefs.getBoolean(KEY_VPN_TO_SOCKS_CHAIN, false),
            profiles = if (profiles.isNotEmpty()) profiles else listOf(ProxyProfile()),
            activeProfileId = activeId,
            primaryDns = prefs.getString(KEY_PRIMARY_DNS, "1.1.1.1") ?: "1.1.1.1",
            secondaryDns = prefs.getString(KEY_SECONDARY_DNS, "8.8.8.8") ?: "8.8.8.8",
            mtu = prefs.getInt(KEY_MTU, 1400)
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
            .putString(KEY_VPN_PROFILES, serializeVpnProfiles(config.vpnProfiles))
            .putString(KEY_ACTIVE_VPN_PROFILE_ID, config.activeVpnProfileId)
            .putBoolean(KEY_VPN_TO_SOCKS_CHAIN, config.isVpnToSocksChainEnabled)
            .putString(KEY_PROFILES, serializeProfiles(config.profiles))
            .putString(KEY_ACTIVE_PROFILE_ID, config.activeProfileId)
            .putString(KEY_PRIMARY_DNS, config.primaryDns)
            .putString(KEY_SECONDARY_DNS, config.secondaryDns)
            .putInt(KEY_MTU, config.mtu)
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

    private fun serializeVpnProfiles(profiles: List<VpnServerProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("protocol", profile.protocol)
                    .put("host", profile.host)
                    .put("port", profile.port)
                    .put("outboundJson", profile.outboundJson)
                    .put("sourceUrl", profile.sourceUrl)
            )
        }
        return array.toString()
    }

    private fun deserializeVpnProfiles(json: String?): List<VpnServerProfile> {
        if (json.isNullOrBlank()) return emptyList()
        val result = mutableListOf<VpnServerProfile>()
        try {
            val array = JSONArray(json)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val outboundJson = item.optString("outboundJson")
                val protocol = item.optString("protocol")
                val host = item.optString("host")
                val port = item.optInt("port")
                if (outboundJson.isBlank() || protocol.isBlank() || host.isBlank() || port !in 1..65535) continue
                result += VpnServerProfile(
                    id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    name = item.optString("name", "VPN сервер").ifBlank { "VPN сервер" },
                    protocol = protocol,
                    host = host,
                    port = port,
                    outboundJson = outboundJson,
                    sourceUrl = item.optString("sourceUrl")
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }
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
        private const val KEY_VPN_PROFILES = "vpn_profiles_json"
        private const val KEY_ACTIVE_VPN_PROFILE_ID = "vpn_active_profile_id"
        private const val KEY_VPN_TO_SOCKS_CHAIN = "vpn_to_socks_chain"
        private const val KEY_PROFILES = "proxy_profiles_list"
        private const val KEY_ACTIVE_PROFILE_ID = "proxy_active_profile_id"
        private const val KEY_PRIMARY_DNS = "proxy_primary_dns"
        private const val KEY_SECONDARY_DNS = "proxy_secondary_dns"
        private const val KEY_MTU = "proxy_mtu"
    }
}
