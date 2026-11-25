package com.socks5proxy.app

import com.socks5proxy.app.updater.UpdateService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateServiceTest {

    @Test
    fun testVersionComparison() {
        assertTrue(UpdateService.isVersionNewer("1.5.4", "1.5.3"))
        assertTrue(UpdateService.isVersionNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateService.isVersionNewer("2.0.0", "1.9.9"))
        assertTrue(UpdateService.isVersionNewer("1.5.4", "1.5"))

        assertFalse(UpdateService.isVersionNewer("1.5.3", "1.5.3"))
        assertFalse(UpdateService.isVersionNewer("1.5.2", "1.5.3"))
        assertFalse(UpdateService.isVersionNewer("1.4.9", "1.5.0"))
    }
}
