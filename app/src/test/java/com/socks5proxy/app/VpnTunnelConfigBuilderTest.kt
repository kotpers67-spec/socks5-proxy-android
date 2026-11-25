package com.socks5proxy.app

import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.model.VpnServerProfile
import com.socks5proxy.app.service.VpnTunnelConfigBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTunnelConfigBuilderTest {

    private val sampleServer = VpnServerProfile(
        id = "srv-1",
        name = "Test VLESS",
        protocol = "vless",
        host = "example.com",
        port = 443,
        outboundJson = """{"protocol":"vless","settings":{"vnext":[{"address":"example.com","port":443,"users":[{"id":"uuid"}]}]}}"""
    )

    @Test
    fun testDirectVpnConfigWithBypassLan() {
        val config = ProxyConfig(
            engineMode = ProxyEngineMode.VPN_PROFILE,
            vpnProfiles = listOf(sampleServer),
            activeVpnProfileId = sampleServer.id,
            isBypassLanEnabled = true
        )
        val built = VpnTunnelConfigBuilder.build(config, sampleServer, tunFd = 42, metricsPort = 18080)
        assertEquals(VpnTunnelConfigBuilder.VPN_OUTBOUND_TAG, built.measuredOutboundTag)

        val root = JSONObject(built.json)
        assertEquals("42", root.getJSONObject("env").getString("xray.tun.fd"))
        assertEquals("127.0.0.1:18080", root.getJSONObject("metrics").getString("listen"))
        assertTrue(root.has("routing"))
        val rules = root.getJSONObject("routing").getJSONArray("rules")
        assertEquals("direct", rules.getJSONObject(0).getString("outboundTag"))
    }

    @Test
    fun testChainedVpnToSocksConfigWithoutBypassLan() {
        val socks = ProxyProfile(name = "ChainSocks", host = "10.0.0.2", port = 1080, username = "u", password = "p")
        val config = ProxyConfig(
            engineMode = ProxyEngineMode.VPN_PROFILE,
            vpnProfiles = listOf(sampleServer),
            activeVpnProfileId = sampleServer.id,
            profiles = listOf(socks),
            activeProfileId = socks.id,
            isVpnToSocksChainEnabled = true,
            isBypassLanEnabled = false
        )
        val built = VpnTunnelConfigBuilder.build(config, sampleServer, tunFd = 99, metricsPort = 18081)
        assertEquals(VpnTunnelConfigBuilder.SOCKS_OUTBOUND_TAG, built.measuredOutboundTag)

        val root = JSONObject(built.json)
        assertFalse(root.has("routing"))
        val firstOutbound = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("socks", firstOutbound.getString("protocol"))
        assertEquals(
            VpnTunnelConfigBuilder.VPN_OUTBOUND_TAG,
            firstOutbound.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy")
        )
    }
}
