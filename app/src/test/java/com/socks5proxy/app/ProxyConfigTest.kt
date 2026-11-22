package com.socks5proxy.app

import com.socks5proxy.app.model.ProxyConfig
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.ProxyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testEmptyHostProfile() {
        val profile = ProxyProfile(
            name = "Test Empty Host",
            host = "   ",
            port = 1080
        )
        assertFalse(profile.isValid)
    }

    @Test
    fun testProxyConfigDefaults() {
        val config = ProxyConfig()
        assertEquals(ProxyEngineMode.LOCAL_PROXY, config.engineMode)
        assertEquals(10808, config.localListenPort)
        assertEquals("1.1.1.1", config.primaryDns)
        assertEquals("8.8.8.8", config.secondaryDns)
        assertEquals(1400, config.mtu)
        assertFalse(config.isAutoStartEnabled)
        assertFalse(config.isFallbackDirectEnabled)
    }
}
