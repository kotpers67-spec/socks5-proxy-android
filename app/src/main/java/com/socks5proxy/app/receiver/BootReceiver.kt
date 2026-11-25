package com.socks5proxy.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.socks5proxy.app.data.ProxyPreferences
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.service.LocalProxyService
import com.socks5proxy.app.service.Socks5VpnService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = ProxyPreferences(context)
            val config = prefs.loadConfig()

            if (config.isAutoStartEnabled && config.isReadyForConnection) {
                if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
                    LocalProxyService.start(context)
                } else if (VpnService.prepare(context) == null) {
                    Socks5VpnService.start(context)
                }
            }
        }
    }
}
