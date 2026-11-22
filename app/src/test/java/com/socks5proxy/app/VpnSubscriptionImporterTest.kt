package com.socks5proxy.app

import com.socks5proxy.app.data.VpnSubscriptionImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSubscriptionImporterTest {

    @Test
    fun testParseVlessReality() {
        val link = "vless://b03138b1-8d2a-4db5-b847-5e6833c94247@95.163.242.12:443?type=tcp&security=reality&pbk=xyz123&fp=chrome&sni=yahoo.com&sid=abcdef&flow=xtls-rprx-vision#Germany-Reality"
        val profiles = VpnSubscriptionImporter.import(link)
        
        assertEquals(1, profiles.size)
        val profile = profiles.first()
        assertEquals("vless", profile.protocol)
        assertEquals("95.163.242.12", profile.host)
        assertEquals(443, profile.port)
        assertEquals("Germany-Reality", profile.name)
        assertTrue(profile.outboundJson.contains("realitySettings"))
        assertTrue(profile.outboundJson.contains("xtls-rprx-vision"))
    }

    @Test
    fun testParseVlessWsNoVisionFlow() {
        // Когда type=ws, flow=xtls-rprx-vision должен быть отфильтрован во избежание краша Xray
        val link = "vless://b03138b1-8d2a-4db5-b847-5e6833c94247@1.2.3.4:443?type=ws&security=tls&flow=xtls-rprx-vision&path=%2Fws#US-WebSocket"
        val profiles = VpnSubscriptionImporter.import(link)

        assertEquals(1, profiles.size)
        val profile = profiles.first()
        assertEquals("vless", profile.protocol)
        assertTrue(profile.outboundJson.contains("wsSettings"))
        // Flow vision НЕ должен попадать в wsSettings / outbound
        assertTrue(!profile.outboundJson.contains("xtls-rprx-vision"))
    }

    @Test
    fun testParseHysteria2() {
        val link = "hysteria2://mySecretPass@45.12.34.56:8443?sni=fast.cdn.net&insecure=1#Fast-Hy2"
        val profiles = VpnSubscriptionImporter.import(link)

        assertEquals(1, profiles.size)
        val profile = profiles.first()
        assertEquals("hysteria2", profile.protocol)
        assertEquals("45.12.34.56", profile.host)
        assertEquals(8443, profile.port)
        assertEquals("Fast-Hy2", profile.name)
        assertTrue(profile.outboundJson.contains("fast.cdn.net"))
    }

    @Test
    fun testParseTrojan() {
        val link = "trojan://trojanpassword@88.99.77.66:443?security=tls&sni=example.com#Trojan-Node"
        val profiles = VpnSubscriptionImporter.import(link)

        assertEquals(1, profiles.size)
        val profile = profiles.first()
        assertEquals("trojan", profile.protocol)
        assertEquals("88.99.77.66", profile.host)
        assertEquals(443, profile.port)
        assertEquals("Trojan-Node", profile.name)
    }
}
