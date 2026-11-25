package com.socks5proxy.app.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
    val name: String = "Новый прокси",
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val isUdpEnabled: Boolean = true
) {
    val isValid: Boolean
        get() = host.trim().isNotEmpty() && port in 1..65535 &&
            ((username.isBlank() && password.isBlank()) || (username.isNotBlank() && password.isNotEmpty()))

    fun toSocksUri(): String {
        val cleanHost = host.trim()
        val encodedHost = if (cleanHost.contains(':') && !cleanHost.startsWith("[")) "[$cleanHost]" else cleanHost
        val authPart = if (username.isNotBlank()) {
            val u = URLEncoder.encode(username, StandardCharsets.UTF_8.name())
            val p = URLEncoder.encode(password, StandardCharsets.UTF_8.name())
            "$u:$p@"
        } else {
            ""
        }
        val tagPart = if (name.isNotBlank()) {
            "#${URLEncoder.encode(name, StandardCharsets.UTF_8.name())}"
        } else {
            ""
        }
        return "socks5://$authPart$encodedHost:$port$tagPart"
    }

    companion object {
        fun fromUriOrLine(raw: String): ProxyProfile? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            if (trimmed.startsWith("socks5://", ignoreCase = true) ||
                trimmed.startsWith("socks://", ignoreCase = true)
            ) {
                return runCatching {
                    val normalized = "http://" + trimmed.substringAfter("://")
                    val uri = URI(normalized)
                    val host = uri.host?.trim().orEmpty()
                    val port = if (uri.port in 1..65535) uri.port else 1080
                    if (host.isEmpty()) return null
                    val userInfo = uri.rawUserInfo.orEmpty()
                    val (user, pass) = if (userInfo.contains(':')) {
                        URLDecoder.decode(userInfo.substringBefore(':'), StandardCharsets.UTF_8.name()) to
                            URLDecoder.decode(userInfo.substringAfter(':'), StandardCharsets.UTF_8.name())
                    } else if (userInfo.isNotBlank()) {
                        URLDecoder.decode(userInfo, StandardCharsets.UTF_8.name()) to ""
                    } else {
                        "" to ""
                    }
                    val fragment = trimmed.substringAfter('#', "")
                    val name = if (fragment.isNotBlank()) {
                        URLDecoder.decode(fragment, StandardCharsets.UTF_8.name()).trim()
                    } else {
                        "SOCKS5 · $host"
                    }
                    ProxyProfile(
                        name = name.ifBlank { "SOCKS5 · $host" },
                        host = host,
                        port = port,
                        username = user,
                        password = pass
                    )
                }.getOrNull()
            }

            // Support host:port or host:port:user:pass
            val parts = trimmed.split(':')
            if (parts.size == 2 || parts.size == 4) {
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: return null
                if (host.isEmpty() || port !in 1..65535) return null
                val user = if (parts.size == 4) parts[2].trim() else ""
                val pass = if (parts.size == 4) parts[3].trim() else ""
                return ProxyProfile(
                    name = "SOCKS5 · $host",
                    host = host,
                    port = port,
                    username = user,
                    password = pass
                )
            }
            return null
        }
    }
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
    val profiles: List<ProxyProfile> = emptyList(),
    val activeProfileId: String = "",
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val mtu: Int = 1400,
    val isBypassLanEnabled: Boolean = true,
    val isAutoCheckUpdatesEnabled: Boolean = true
) {
    val activeProfile: ProxyProfile
        get() = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull() ?: ProxyProfile()

    val activeVpnProfile: VpnServerProfile?
        get() = vpnProfiles.find { it.id == activeVpnProfileId } ?: vpnProfiles.firstOrNull()

    val isValid: Boolean
        get() = activeProfile.isValid && localListenPort in 1024..65535

    val isReadyForConnection: Boolean
        get() = when (engineMode) {
            ProxyEngineMode.VPN_PROFILE -> activeVpnProfile != null && (!isVpnToSocksChainEnabled || activeProfile.isValid)
            ProxyEngineMode.SYSTEM_VPN -> activeProfile.isValid
            ProxyEngineMode.LOCAL_PROXY -> activeProfile.isValid && localListenPort in 1024..65535
        }

    override fun toString(): String {
        return "ProxyConfig(mode=$engineMode, active='${activeProfile.name}', host='${activeProfile.host}', port=${activeProfile.port})"
    }
}
