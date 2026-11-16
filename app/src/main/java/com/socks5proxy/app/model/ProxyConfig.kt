package com.socks5proxy.app.model

import java.util.UUID

enum class ProxyEngineMode {
    LOCAL_PROXY,
    SYSTEM_VPN
}

data class ProxyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Прокси 1",
    val host: String = "127.0.0.1",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val isUdpEnabled: Boolean = true
) {
    val isValid: Boolean
        get() = host.trim().isNotEmpty() && port in 1..65535
}

data class ProxyConfig(
    val engineMode: ProxyEngineMode = ProxyEngineMode.LOCAL_PROXY,
    val localListenPort: Int = 10808,
    val isAutoStartEnabled: Boolean = false,
    val isFallbackDirectEnabled: Boolean = true,
    val isPerAppRoutingEnabled: Boolean = true,
    val allowedApps: Set<String> = emptySet(),
    val profiles: List<ProxyProfile> = listOf(ProxyProfile()),
    val activeProfileId: String = "",
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val mtu: Int = 1400
) {
    val activeProfile: ProxyProfile
        get() = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull() ?: ProxyProfile()

    val isValid: Boolean
        get() = activeProfile.isValid && localListenPort in 1024..65535

    override fun toString(): String {
        return "ProxyConfig(mode=$engineMode, active='${activeProfile.name}', host='${activeProfile.host}', port=${activeProfile.port})"
    }
}
