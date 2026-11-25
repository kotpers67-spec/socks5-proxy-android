package com.socks5proxy.app

import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.ProxyProfile
import com.socks5proxy.app.model.VpnServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyConfigTest {

    @Test
    fun testValidProxyProfile() {
        val profile = ProxyProfile(
            name = "Test",
            host = "1.2.3.4",
            port = 1080,
            username = "user",
            password = "pass"
        )
        assertTrue(profile.isValid)
    }

    @Test
    fun testAnonymousProxyProfile() {
        val profile = ProxyProfile(
            name = "Test Anon",
            host = "1.2.3.4",
            port = 1080,
            username = "",
            password = ""
        )
        assertTrue(profile.isValid)
    }

    @Test
    fun testInvalidPortProfile() {
        val profile = ProxyProfile(
            name = "Test Invalid Port",
            host = "1.2.3.4",
            port = 0
        )
        assertFalse(profile.isValid)
    }

    @Test
    fun testEmptyHostProfileIsInvalidByDefault() {
        val defaultProfile = ProxyProfile()
        assertEquals("", defaultProfile.host)
        assertEquals(1080, defaultProfile.port)
        assertFalse(defaultProfile.isValid)
    }

    @Test
    fun testProxyConfigDefaultsHaveNoHardcodedServer() {
        val config = ProxyConfig()
        assertEquals(ProxyEngineMode.LOCAL_PROXY, config.engineMode)
        assertEquals(10808, config.localListenPort)
        assertEquals("1.1.1.1", config.primaryDns)
        assertEquals("8.8.8.8", config.secondaryDns)
        assertEquals(1400, config.mtu)
        assertTrue(config.profiles.isEmpty())
        assertTrue(config.isBypassLanEnabled)
        assertTrue(config.isAutoCheckUpdatesEnabled)
        assertFalse(config.isAutoStartEnabled)
        assertFalse(config.isFallbackDirectEnabled)
        assertFalse(config.isReadyForConnection)
    }

    @Test
    fun testSocksUriRoundTripAndLineParsing() {
        val original = ProxyProfile(
            name = "EU Node",
            host = "10.20.30.40",
            port = 10808,
            username = "alice",
            password = "secret"
        )
        val uri = original.toSocksUri()
        val parsed = ProxyProfile.fromUriOrLine(uri)
        assertNotNull(parsed)
        assertEquals("EU Node", parsed!!.name)
        assertEquals("10.20.30.40", parsed.host)
        assertEquals(10808, parsed.port)
        assertEquals("alice", parsed.username)
        assertEquals("secret", parsed.password)

        val colonParsed = ProxyProfile.fromUriOrLine("9.8.7.6:9050:bob:pw123")
        assertNotNull(colonParsed)
        assertEquals("9.8.7.6", colonParsed!!.host)
        assertEquals(9050, colonParsed.port)
        assertEquals("bob", colonParsed.username)
        assertEquals("pw123", colonParsed.password)

        assertNull(ProxyProfile.fromUriOrLine("not-a-proxy"))
    }

    @Test
    fun testReadyForConnectionAcrossModes() {
        val vpn = VpnServerProfile(
            id = "v1",
            name = "VPN",
            protocol = "vless",
            host = "1.2.3.4",
            port = 443,
            outboundJson = "{}"
        )
        val vpnOnlyConfig = ProxyConfig(
            engineMode = ProxyEngineMode.VPN_PROFILE,
            vpnProfiles = listOf(vpn),
            activeVpnProfileId = "v1",
            isVpnToSocksChainEnabled = false
        )
        assertTrue(vpnOnlyConfig.isReadyForConnection)

        val chainedWithoutSocks = vpnOnlyConfig.copy(isVpnToSocksChainEnabled = true)
        assertFalse(chainedWithoutSocks.isReadyForConnection)
    }
}
