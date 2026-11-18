package com.socks5proxy.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.socks5proxy.app.MainActivity
import com.socks5proxy.app.R
import com.socks5proxy.app.data.ProxyPreferences
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.network.VpnDetector

class ProxyQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { toggleProxy() }
        } else {
            toggleProxy()
        }
    }

    private fun toggleProxy() {
        val config = ProxyPreferences(this).loadConfig()
        val status = if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
            LocalProxyService.statusFlow.value
        } else {
            Socks5VpnService.statusFlow.value
        }

        if (status.isRunning) {
            if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
                LocalProxyService.stop(this)
            } else {
                Socks5VpnService.stop(this)
            }
            setTileEnabled(false)
            return
        }

        val needsSocks = config.engineMode != ProxyEngineMode.VPN_PROFILE || config.isVpnToSocksChainEnabled
        if (needsSocks && !config.activeProfile.isValid) {
            launchMainActivity(MainActivity.ACTION_TILE_CONNECT)
            return
        }

        if (config.engineMode == ProxyEngineMode.VPN_PROFILE && config.activeVpnProfile == null) {
            launchMainActivity(MainActivity.ACTION_TILE_CONNECT)
            return
        }

        if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
            LocalProxyService.start(this)
            setTileEnabled(true)
            return
        }

        if (VpnDetector.isExternalVpnActive(this)) {
            Toast.makeText(
                this,
                "Android поддерживает один VPN за раз. Отключите другое VPN-приложение или выберите локальный SOCKS.",
                Toast.LENGTH_LONG
            ).show()
            refreshTile()
            return
        }

        if (config.isPerAppRoutingEnabled && config.allowedApps.isEmpty()) {
            launchMainActivity(MainActivity.ACTION_TILE_CONNECT)
            return
        }

        launchMainActivity(MainActivity.ACTION_TILE_CONNECT)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchMainActivity(action: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                TILE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun setTileEnabled(enabled: Boolean) {
        getQsTile()?.let { tile ->
            tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    private fun refreshTile() {
        val tile = getQsTile() ?: return
        val config = ProxyPreferences(this).loadConfig()
        val status = if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
            LocalProxyService.statusFlow.value
        } else {
            Socks5VpnService.statusFlow.value
        }
        val anotherVpnActive = config.engineMode != ProxyEngineMode.LOCAL_PROXY &&
                VpnDetector.isExternalVpnActive(this)

        tile.label = getString(R.string.quick_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
                getString(R.string.quick_tile_local_subtitle)
            } else {
                getString(R.string.quick_tile_vpn_subtitle)
            }
        }
        tile.state = when {
            anotherVpnActive -> Tile.STATE_UNAVAILABLE
            status.isRunning -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = when {
                anotherVpnActive -> "Отключите другое VPN"
                status.isRunning -> "Включено"
                else -> "Выключено"
            }
        }
        tile.updateTile()
    }

    companion object {
        private const val TILE_REQUEST_CODE = 2417

        fun requestRefresh(context: android.content.Context) {
            runCatching {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, ProxyQuickTileService::class.java)
                )
            }
        }
    }
}
