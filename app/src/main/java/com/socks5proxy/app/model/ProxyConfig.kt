package com.socks5proxy.app.model

data class ProxyConfig(
    val host: String = "127.0.0.1",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val isUdpEnabled: Boolean = true,
    val isIpv6Enabled: Boolean = false,
    val isPerAppRoutingEnabled: Boolean = true,
    val allowedApps: Set<String> = emptySet(),
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val mtu: Int = 1400
) {
    val isValid: Boolean
        get() {
            val cleanHost = host.trim()
            return cleanHost.isNotEmpty() &&
                    port in 1..65535 &&
                    username.toByteArray(Charsets.UTF_8).size <= 255 &&
                    password.toByteArray(Charsets.UTF_8).size <= 255
        }

    override fun toString(): String {
        val maskedPass = if (password.isNotEmpty()) "******" else ""
        return "ProxyConfig(host='$host', port=$port, username='$username', password='$maskedPass', udp=$isUdpEnabled, split=$isPerAppRoutingEnabled, allowedAppsCount=${allowedApps.size})"
    }
}
