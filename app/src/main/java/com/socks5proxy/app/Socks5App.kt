package com.socks5proxy.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import java.io.File

class Socks5App : Application() {

    override fun onCreate() {
        super.onCreate()
        cleanupStaleTempFiles()
        createNotificationChannel()
    }

    private fun cleanupStaleTempFiles() {
        try {
            val staleConfig = File(cacheDir, "hev_tunnel_config.yml")
            if (staleConfig.exists()) {
                staleConfig.delete()
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "socks5_vpn_channel"
        const val NOTIFICATION_ID = 1001
    }
}
