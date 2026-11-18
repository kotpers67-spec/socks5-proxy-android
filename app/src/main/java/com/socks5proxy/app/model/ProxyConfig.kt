package com.socks5proxy.app.model

import java.util.UUID

enum class ProxyEngineMode {
    LOCAL_PROXY,
    SYSTEM_VPN,
    VPN_PROFILE
}

data class VpnServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val outboundJson: String,
    val sourceUrl: String = ""
)

data class ProxyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Основной прокси",
    val host: String = "72.56.103.114",
    val port: Int = 48471,
    val username: String = "proxy_user",
    val password: String = "9Kvo904s1bhsazPn",
    val isUdpEnabled: Boolean = true
) {
    val isValid: Boolean
        get() = host.trim().isNotEmpty() && port in 1..65535 &&
            ((username.isBlank() && password.isBlank()) || (username.isNotBlank() && password.isNotEmpty()))
}

data class ProxyConfig(
    val engineMode: ProxyEngineMode = ProxyEngineMode.LOCAL_PROXY,
    val localListenPort: Int = 10808,
    val isAutoStartEnabled: Boolean = false,
    val isFallbackDirectEnabled: Boolean = false,
    val isPerAppRoutingEnabled: Boolean = false,
    val allowedApps: Set<String> = emptySet(),
    val vpnProfiles: List<VpnServerProfile> = emptyList(),
    val activeVpnProfileId: String = "",
    val isVpnToSocksChainEnabled: Boolean = false,
    val profiles: List<ProxyProfile> = listOf(ProxyProfile()),
    val activeProfileId: String = "",
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val mtu: Int = 1400
) {
    val activeProfile: ProxyProfile
        get() = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull() ?: ProxyProfile()

    val activeVpnProfile: VpnServerProfile?
        get() = vpnProfiles.find { it.id == activeVpnProfileId } ?: vpnProfiles.firstOrNull()

    val isValid: Boolean
        get() = activeProfile.isValid && localListenPort in 1024..65535

    override fun toString(): String {
        return "ProxyConfig(mode=$engineMode, active='${activeProfile.name}', host='${activeProfile.host}', port=${activeProfile.port})"
    }
}
