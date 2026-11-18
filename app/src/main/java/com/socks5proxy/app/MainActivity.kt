package com.socks5proxy.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.socks5proxy.app.model.ProxyEngineMode
import com.socks5proxy.app.model.TunnelState
import com.socks5proxy.app.model.TunnelStatus
import com.socks5proxy.app.data.ProxyPreferences
import com.socks5proxy.app.network.VpnDetector
import com.socks5proxy.app.service.LocalProxyService
import com.socks5proxy.app.service.Socks5VpnService
import com.socks5proxy.app.ui.MainViewModel
import com.socks5proxy.app.ui.screens.MainScreen
import com.socks5proxy.app.ui.theme.Socks5ProxyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            LocalProxyService.stop(this)
            Socks5VpnService.start(this)
        } else {
            Toast.makeText(this, "Разрешение на VPN отклонено пользователем", Toast.LENGTH_SHORT).show()
            Socks5VpnService.updateStatus(TunnelStatus(TunnelState.DISCONNECTED, "Разрешение VPN отклонено"))
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val tunnelStatus by viewModel.tunnelStatus.collectAsState()

            Socks5ProxyTheme(darkTheme = uiState.isDarkTheme) {
                MainScreen(
                    uiState = uiState,
                    tunnelStatus = tunnelStatus,
                    onToggleConnection = {
                        if (tunnelStatus.isRunning) {
                            LocalProxyService.stop(this@MainActivity)
                            Socks5VpnService.stop(this@MainActivity)
                        } else {
                            val active = uiState.config.activeProfile
                            val needsSocks = uiState.config.engineMode != ProxyEngineMode.VPN_PROFILE ||
                                uiState.config.isVpnToSocksChainEnabled
                            if (needsSocks && !active.isValid) {
                                viewModel.openEditProfile(active)
                            } else if (uiState.config.engineMode == ProxyEngineMode.VPN_PROFILE && uiState.config.activeVpnProfile == null) {
                                Toast.makeText(this@MainActivity, "Импортируйте VPN-ссылку во вкладке «Серверы»", Toast.LENGTH_LONG).show()
                                viewModel.setActiveTab(1)
                            } else {
                                val isVpnActive = VpnDetector.isExternalVpnActive(this@MainActivity)
                                if (isVpnActive && uiState.config.engineMode != ProxyEngineMode.LOCAL_PROXY) {
                                    val message = "Android поддерживает один VPN за раз. Отключите другое VPN-приложение или выберите локальный SOCKS."
                                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                                    Socks5VpnService.updateStatus(TunnelStatus(TunnelState.ERROR, message))
                                } else if (uiState.config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
                                    Socks5VpnService.stop(this@MainActivity)
                                    LocalProxyService.start(this@MainActivity)
                                } else {
                                    if (uiState.config.isPerAppRoutingEnabled && uiState.config.allowedApps.isEmpty()) {
                                        Toast.makeText(this@MainActivity, "Выберите приложения для проксирования", Toast.LENGTH_SHORT).show()
                                        viewModel.setAppSelectorVisible(true)
                                    } else {
                                        LocalProxyService.stop(this@MainActivity)
                                        prepareAndStartVpn()
                                    }
                                }
                            }
                        }
                    },
                    viewModel = viewModel
                )
            }
        }

        handleTileConnectIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTileConnectIntent(intent)
    }

    private fun handleTileConnectIntent(intent: android.content.Intent?) {
        if (intent?.action != ACTION_TILE_CONNECT) return
        intent.action = null

        val config = ProxyPreferences(this).loadConfig()
        val needsSocks = config.engineMode != ProxyEngineMode.VPN_PROFILE || config.isVpnToSocksChainEnabled
        if (needsSocks && !config.activeProfile.isValid) {
            viewModel.openEditProfile(config.activeProfile)
            return
        }

        if (config.engineMode == ProxyEngineMode.VPN_PROFILE && config.activeVpnProfile == null) {
            Toast.makeText(this, "Импортируйте VPN-ссылку во вкладке «Серверы»", Toast.LENGTH_LONG).show()
            viewModel.setActiveTab(1)
            return
        }

        if (config.engineMode == ProxyEngineMode.LOCAL_PROXY) {
            LocalProxyService.start(this)
            return
        }

        if (VpnDetector.isExternalVpnActive(this)) {
            val message = "Android поддерживает один VPN за раз. Отключите другое VPN-приложение или выберите локальный SOCKS."
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Socks5VpnService.updateStatus(TunnelStatus(TunnelState.ERROR, message))
            return
        }

        if (config.isPerAppRoutingEnabled && config.allowedApps.isEmpty()) {
            viewModel.setAppSelectorVisible(true)
            return
        }

        LocalProxyService.stop(this)
        prepareAndStartVpn()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    private fun prepareAndStartVpn() {
        try {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                Socks5VpnService.start(this)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось инициализировать VPN: ${e.message}", Toast.LENGTH_SHORT).show()
            Socks5VpnService.updateStatus(TunnelStatus(TunnelState.ERROR, "Ошибка инициализации VPN: ${e.message}"))
        }
    }

    companion object {
        const val ACTION_TILE_CONNECT = "com.socks5proxy.app.ACTION_TILE_CONNECT"
    }
}
