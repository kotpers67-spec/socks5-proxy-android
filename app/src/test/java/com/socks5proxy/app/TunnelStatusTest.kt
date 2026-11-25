package com.socks5proxy.app

import com.socks5proxy.app.model.TunnelState
import com.socks5proxy.app.model.TunnelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelStatusTest {

    @Test
    fun testFormattingBytesAndSpeed() {
        assertEquals("0 Б", TunnelStatus.formatBytes(0))
        assertEquals("512 Б", TunnelStatus.formatBytes(512))
        assertEquals("2.0 КБ", TunnelStatus.formatBytes(2048))
        assertEquals("1.5 МБ", TunnelStatus.formatBytes(1536 * 1024L))

        assertEquals("0 Б/с", TunnelStatus.formatSpeed(0))
        assertEquals("10.0 КБ/с", TunnelStatus.formatSpeed(10 * 1024L))
    }

    @Test
    fun testFormattedDuration() {
        val status = TunnelStatus(state = TunnelState.CONNECTED, connectedAtMillis = 10_000L)
        assertEquals("01:01:05", status.formattedDuration(nowMillis = 10_000L + 3665_000L))
        assertEquals("00:00:00", TunnelStatus(state = TunnelState.DISCONNECTED).formattedDuration())
    }
}
