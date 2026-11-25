package com.socks5proxy.app.service

import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.model.VpnServerProfile
import org.json.JSONArray
import org.json.JSONObject

internal data class BuiltVpnConfig(
    val json: String,
    val measuredOutboundTag: String
)

internal object VpnTunnelConfigBuilder {
    fun build(
        config: ProxyConfig,
        server: VpnServerProfile,
        tunFd: Int,
        metricsPort: Int
    ): BuiltVpnConfig {
        val vpnOutbound = JSONObject(server.outboundJson).put("tag", VPN_OUTBOUND_TAG)
        val useSocksChain = config.isVpnToSocksChainEnabled
        val measuredTag = if (useSocksChain) SOCKS_OUTBOUND_TAG else VPN_OUTBOUND_TAG

        val outbounds = JSONArray()
        if (useSocksChain) {
            outbounds.put(buildSocksOutbound(config.activeProfile))
            outbounds.put(vpnOutbound)
        } else {
            outbounds.put(vpnOutbound)
        }
        outbounds.put(JSONObject().put("protocol", "freedom").put("tag", "direct"))
        outbounds.put(JSONObject().put("protocol", "blackhole").put("tag", "block"))

        val tunSettings = JSONObject()
            .put("name", "tun0")
            .put("mtu", config.mtu)
            .put("gateway", JSONArray().put("198.18.0.1/24").put("fd00::1/126"))
            .put("dns", JSONArray().put(config.primaryDns).put(config.secondaryDns))
        val inbound = JSONObject()
            .put("tag", TUN_INBOUND_TAG)
            .put("protocol", "tun")
            .put("settings", tunSettings)

        val policy = JSONObject().put(
            "system",
            JSONObject()
                .put("statsInboundUplink", true)
                .put("statsInboundDownlink", true)
                .put("statsOutboundUplink", true)
                .put("statsOutboundDownlink", true)
        )

        val root = JSONObject()
            // libXray forwards this runtime FD through the Xray config's root env object.
            .put("env", JSONObject().put("xray.tun.fd", tunFd.toString()))
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", outbounds)
            .put("stats", JSONObject())
            .put("policy", policy)
            .put("metrics", JSONObject().put("listen", "127.0.0.1:$metricsPort"))

        if (config.isBypassLanEnabled) {
            val privateCidrs = JSONArray()
                .put("10.0.0.0/8")
                .put("172.16.0.0/12")
                .put("192.168.0.0/16")
                .put("127.0.0.0/8")
                .put("169.254.0.0/16")
                .put("fc00::/7")
                .put("fe80::/10")
            val lanRule = JSONObject()
                .put("type", "field")
                .put("ip", privateCidrs)
                .put("outboundTag", "direct")
            root.put(
                "routing",
                JSONObject()
                    .put("domainStrategy", "IPIfNonMatch")
                    .put("rules", JSONArray().put(lanRule))
            )
        }

        return BuiltVpnConfig(root.toString(), measuredTag)
    }

    private fun buildSocksOutbound(profile: ProxyProfile): JSONObject {
        val server = JSONObject()
            .put("address", profile.host.trim())
            .put("port", profile.port)
        if (profile.username.isNotBlank()) {
            server.put(
                "users",
                JSONArray().put(
                    JSONObject()
                        .put("user", profile.username)
                        .put("pass", profile.password)
                )
            )
        }

        // Xray's dialerProxy makes the connection to the SOCKS server through the VPN outbound.
        val streamSettings = JSONObject().put(
            "sockopt",
            JSONObject().put("dialerProxy", VPN_OUTBOUND_TAG)
        )
        return JSONObject()
            .put("tag", SOCKS_OUTBOUND_TAG)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", streamSettings)
    }

    const val TUN_INBOUND_TAG = "app-tun"
    const val VPN_OUTBOUND_TAG = "vpn-server"
    const val SOCKS_OUTBOUND_TAG = "socks-after-vpn"
}
